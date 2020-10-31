package org.zzzyxwvut.readywriter.fd.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.zzzyxwvut.impedimenta.ContendingExecutor;
import org.zzzyxwvut.impedimenta.FileReader;
import org.zzzyxwvut.julics.annotation.Loggable;
import org.zzzyxwvut.julics.naming.LoggerRef;

import org.zzzyxwvut.readywriter.ReadyWriter;
import org.zzzyxwvut.readywriter.service.FileDescriptorWriterVisitor;

/*
 * XXX: The following tests depend on the proc file system availability and
 * expect to inherit file descriptors from JVM's parent process. Drive these
 * tests with the supplied src/test/shell/fd_tests.sh script.
 *
 * (See src/test/shell/build.xml and the antrun plugin set-up in pom.xml.)
 */
@EnabledOnOs({ OS.LINUX })
@Loggable(retainsValues = false)
public class FileDescriptorWriterProviderTests
{
	private static final int THREADS = 8;
	private static final String NEWLINE = System.lineSeparator();
	private static final Path PREFIX = Path.of("/proc/self/fd");
	private static final Function<ReadyWriter,
				Function<String, Runnable>> WRITER =
			writer -> message -> () -> writer.write(message);

	private static String LOG_FILE_NAME;

	private ContendingExecutor executor;
	private ReadyWriter fdWriter;

	@BeforeAll
	public static void setUpClass()
	{
		final LoggerRef loggerRef = new LoggerRef(
				FileDescriptorWriterProviderTests.class);

		/*
		 * The FileHandler.pattern property is made available with
		 * an LoggerRef object creation, see the detail message.
		 */
		LOG_FILE_NAME = LogManager.getLogManager()
			.getProperty("java.util.logging.FileHandler.pattern");

		if (LOG_FILE_NAME == null)
			throw new IllegalStateException(
				"Perhaps src/test/resources/logging.properties "
				+ "was not read or its "
				+ "java.util.logging.FileHandler.pattern "
				+ "property is missing");

		LoggerRef.logger()
			.apply(loggerRef)
			.apply(Level.OFF)
			.accept(pathWalker()
				.apply(fileNamer()
					.apply(LoggerRef.errorLogger()
						.apply(loggerRef)
						.apply(Level.WARNING))));
	}

	/*
	 * Note that path.toFile().getCanonicalPath() "quaffs
	 * nepenthe" when ENOENT is delivered along with
	 * the realpath(3) invocation (see canonicalize(char*, char*, int)
	 * in unix/native/libjava/canonicalize_md.c,
	 * see *_canonicalize0(JNIEnv*, jobject, jstring) in
	 * unix/native/libjava/UnixFileSystem_md.c,
	 * see java.io.UnixFileSystem#canonicalize(String)).
	 *
	 * WHEREAS path.toRealPath(), in the similar context,
	 * collects a sun.nio.fs.UnixException (see
	 * *_realpath0(JNIEnv*, jclass, jlong) in
	 * unix/native/libnio/fs/UnixNativeDispatcher.c,
	 * see sun.nio.fs.UnixNativeDispatcher#realpath(UnixPath),
	 * see sun.nio.fs.UnixPath#toRealPath(LinkOption...)).
	 */
	private static Function<Function<Throwable, Consumer<Supplier<String>>>,
				Function<Path, String>> fileNamer()
	{
		return warner -> path -> {
			try {
				return String.format("%s -> %s",
					path.getFileName(),
					path.toFile().getCanonicalPath());
			} catch (final IOException e) {
				warner.apply(e)
					.accept(() ->
						"Failed to resolve path");
				return String.format("%s -> %s",
					path.getFileName(), e.toString());
			}
		};
	}

	private static Function<Function<Path, String>, Supplier<String>>
								pathWalker()
	{
		return fileNamer -> () -> {
			try (Stream<Path> files = Files.list(Path.of(
							"/proc/self/fd"))) {
				return files
					.map(fileNamer)
					.collect(Collectors.joining(NEWLINE));
			} catch (final IOException e) {
				fail("Failed to make a snapshot of file descriptors",
									e);
			}

			return "";
		};
	}

	@BeforeEach
	public void setUp() throws InterruptedException
	{
		/*
		 * See readywriter-fd/pom.xml about service loading and
		 * the maven-surefire forks.
		 */
		fdWriter = FileDescriptorWriterProvider.provider();
		executor = ContendingExecutor.newInstance(THREADS);
	}

	@AfterEach
	public void tearDown() throws InterruptedException
	{
		executor.shutdown();

		do {
			executor.awaitTermination(2L, TimeUnit.SECONDS);
		} while (!executor.isTerminated());

		final List<String> errors = executor.getErrors()
			.stream()
			.map(entry -> String.format("%s :: %s",
						entry.getKey(),
						entry.getValue().toString()))
			.collect(Collectors.toUnmodifiableList());

		if (!errors.isEmpty())
			throw new UncaughtExceptionError(
				String.format("Uncaught error(s): %s",
						errors.toString()));
	}

	private static ReadyWriter newReadyWriter(ReadyWriter writer,
					FileDescriptorWriterVisitor visitor)
	{
		return writer.accept(visitor)
			.orElseThrow(IllegalStateException::new);
	}

	private static String nCat(int copies, String sample)
	{
		return String.join("", Collections.nCopies(copies, sample));
	}

	/*
	 * Note that in order to pass the test of file descriptors 1 and 2,
	 * no writing to System.{out,err} is expected in the method and its
	 * callees; consider running the mvn command with the --quiet option.
	 */
	@ParameterizedTest(name = "File Descriptor @{index}: {0}")
	@ValueSource(ints = { 2, 1 })
	public void testWritingToDefaultDescriptors(int fd) throws IOException,
							InterruptedException
	{
		final String sample = new StringBuilder(32)
			.append("foo bar").append(NEWLINE)
			.append("baz quux").append(NEWLINE)
			.toString();
		final byte[] expected = nCat(THREADS, sample)
			.getBytes(StandardCharsets.UTF_8);
		final Path path = PREFIX.resolve(String.valueOf(fd));
		final Runnable r = WRITER
			.apply(newReadyWriter(fdWriter,
					new FileDescriptorWriterVisitor(fd,
						Pattern.compile("[12]"),
						StandardCharsets.UTF_8,
						ByteOrder.nativeOrder())))
			.apply(new String(expected, StandardCharsets.UTF_8));
		executor.executeAndWait(Collections.nCopies(THREADS, r));
		final byte[] obtained = Optional.ofNullable(new FileReader()
				.watchAndReadBytes(path, expected.length))
			.orElseThrow(AssertionError::new);
		assertArrayEquals(expected, obtained,
			FileDescriptorWriterProviderTests::logFileName);
	}

	/*
	 * To run the next two methods by hand (consider the note at the top):
	 *	mkfifo /tmp/{3,4} && exec 3<>/tmp/3 4<>/tmp/4 && rm /tmp/{3,4}
	 * 	vdir /proc/self/fd
	 * 	cd readywriter-fd/
	 *	mvn -e -P tester surefire:test -Dtest=org.zzzyxwvut.readywriter.fd.internal.FileDescriptorWriterProviderTests#testWritingToBespokeDescriptors,#testExchangingOfDescriptors
	 *	exec 3>&- 4>&-
	 */
	@ParameterizedTest(name = "File Descriptor @{index}: {0}")
	@ValueSource(ints = { 4, 3 })
	public void testWritingToBespokeDescriptors(int fd) throws IOException,
							InterruptedException
	{
		final String sample = new StringBuilder(32)
			.append("foo bar").append(NEWLINE)
			.append("baz quux").append(NEWLINE)
			.toString();
		final byte[] expected = nCat(THREADS, sample)
			.getBytes(StandardCharsets.UTF_8);
		final Path path = PREFIX.resolve(String.valueOf(fd));
		final Runnable r = WRITER
			.apply(newReadyWriter(fdWriter,
					new FileDescriptorWriterVisitor(fd,
						Pattern.compile("[34]"),
						StandardCharsets.UTF_8,
						ByteOrder.nativeOrder())))
			.apply(new String(expected, StandardCharsets.UTF_8));
		executor.executeAndWait(Collections.nCopies(THREADS, r));
		final byte[] obtained = Optional.ofNullable(new FileReader()
				.awaitAndReadBytes(path, expected.length))
			.orElseThrow(AssertionError::new);
		assertArrayEquals(expected, obtained,
			FileDescriptorWriterProviderTests::logFileName);
	}

	@Test
	public void testExchangingOfDescriptors() throws IOException,
							InterruptedException
	{
		final String part30 = new StringBuilder(16)
			.append("foo bar").append(NEWLINE)
			.toString();
		final String part31 = new StringBuilder(16)
			.append("baz quux").append(NEWLINE)
			.toString();
		final String part40 = new StringBuilder(16)
			.append("xuuq zab").append(NEWLINE)
			.toString();
		final String part41 = new StringBuilder(16)
			.append("rab oof").append(NEWLINE)
			.toString();
		final byte[] expected3 = new StringBuilder(16 * 2 * THREADS)
				.append(nCat(THREADS, part30))
				.append(nCat(THREADS, part31))
				.toString()
			.getBytes(StandardCharsets.UTF_8);
		final byte[] expected4 = new StringBuilder(16 * 2 * THREADS)
				.append(nCat(THREADS, part40))
				.append(nCat(THREADS, part41))
				.toString()
			.getBytes(StandardCharsets.UTF_8);
		final Path path3 = PREFIX.resolve("3");
		final Path path4 = PREFIX.resolve("4");
		final Pattern fileName = Pattern.compile("[1-4]");

		newReadyWriter(fdWriter, new FileDescriptorWriterVisitor(2,
					fileName, StandardCharsets.UTF_8,
						ByteOrder.nativeOrder()));

		final Runnable r0 = WRITER
			.apply(newReadyWriter(fdWriter,
				new FileDescriptorWriterVisitor(3, fileName,
						StandardCharsets.UTF_8,
						ByteOrder.nativeOrder())))
			.apply(part30);
		executor.executeAndWait(Collections.nCopies(THREADS, r0));

		final Runnable r1 = WRITER
			.apply(newReadyWriter(fdWriter,
				new FileDescriptorWriterVisitor(4, fileName,
						StandardCharsets.UTF_8,
						ByteOrder.nativeOrder())))
			.apply(part40);
		executor.executeAndWait(Collections.nCopies(THREADS, r1));

		final Runnable r2 = WRITER
			.apply(newReadyWriter(fdWriter,
				new FileDescriptorWriterVisitor(3, fileName,
						StandardCharsets.UTF_8,
						ByteOrder.nativeOrder())))
			.apply(part31);
		executor.executeAndWait(Collections.nCopies(THREADS, r2));

		final Runnable r3 = WRITER
			.apply(newReadyWriter(fdWriter,
				new FileDescriptorWriterVisitor(4, fileName,
						StandardCharsets.UTF_8,
						ByteOrder.nativeOrder())))
			.apply(part41);
		executor.executeAndWait(Collections.nCopies(THREADS, r3));

		newReadyWriter(fdWriter, new FileDescriptorWriterVisitor(1,
					fileName, StandardCharsets.UTF_8,
						ByteOrder.nativeOrder()));

		final byte[] obtained3 = Optional.ofNullable(new FileReader()
				.awaitAndReadBytes(path3, expected3.length))
			.orElseThrow(AssertionError::new);
		assertArrayEquals(expected3, obtained3,
			FileDescriptorWriterProviderTests::logFileName);
		final byte[] obtained4 = Optional.ofNullable(new FileReader()
				.awaitAndReadBytes(path4, expected4.length))
			.orElseThrow(AssertionError::new);
		assertArrayEquals(expected4, obtained4,
			FileDescriptorWriterProviderTests::logFileName);
	}

	private static String logFileName()
	{
		return (!LOG_FILE_NAME.startsWith("%t/"))
			? String.format("Read the log %s file", LOG_FILE_NAME)
			: String.format("Read the log %s%s%s file",
				Objects.requireNonNullElseGet(
					System.getProperty("java.io.tmpdir"),
					() -> System.getProperty("user.home")),
				System.getProperty("file.separator"),
				LOG_FILE_NAME.substring(3));
	} /* See java.logging/java.util.logging.FileHandler#generate(String, int, int, int) */
}
