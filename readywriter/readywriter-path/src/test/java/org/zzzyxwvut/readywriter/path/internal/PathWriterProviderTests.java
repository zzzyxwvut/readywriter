package org.zzzyxwvut.readywriter.path.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import org.zzzyxwvut.impedimenta.ContendingExecutor;
import org.zzzyxwvut.impedimenta.FileReader;
import org.zzzyxwvut.julics.annotation.Loggable;
import org.zzzyxwvut.julics.naming.LoggerRef;

import org.zzzyxwvut.readywriter.ReadyWriter;
import org.zzzyxwvut.readywriter.service.PathWriterVisitor;

@Loggable(retainsValues = false)
public class PathWriterProviderTests
{
	private static final int THREADS = 8;
	private static final String NEWLINE = System.lineSeparator();
	private static final Function<ReadyWriter,
				Function<String, Runnable>> WRITER =
			writer -> message -> () -> writer.write(message);

	@TempDir
	public static Path tmpDir;

	private ContendingExecutor executor;
	private ReadyWriter pathWriter;

	static {
		/* Load logging.properties. */
		new LoggerRef(PathWriterProviderTests.class);
		System.setProperty("org.zzzyxwvut.impedimenta.contention",
								"true");
	}

////	private static Path tmpDir;
////
////	@BeforeAll
////	public static void setUpClass()
////	{
////		try {
////			tmpDir = Files.createTempDirectory(
////				Path.of(System.getProperty("java.io.tmpdir")),
////								"jrw_test");
////			/* File#deleteOnExit() expects an empty directory. */
////		} catch (final IOException e) {
////			fail("Failed to set up a testing directory", e);
////		}
////	}
////
////	@AfterAll
////	public static void tearDownClass()
////	{
////		try {
////			try (Stream<Path> pathStream = Files.walk(tmpDir, 1)) {
////				pathStream.filter(Files::isRegularFile)
////							.forEach(path -> {
////					try {
////						Files.deleteIfExists(path);
////					} catch (final IOException e) {
////						throw new UncheckedIOException(e);
////					}
////				});
////			}
////
////			Files.deleteIfExists(tmpDir);
////		} catch (final IOException e) {
////			throw new UncheckedIOException(e);
////		}
////	}

	private static String getCallerMethodsName(TestInfo info)
	{
		return info.getTestMethod()
			.map(Method::getName)
			.orElseGet(() -> StackWalker.getInstance()
				.walk(frame -> frame.skip(1).findFirst())
				.map(StackWalker.StackFrame::getMethodName)
				.orElseGet(() -> "<INNOMINATE>"));
	}

	@BeforeEach
	public void setUp() throws InterruptedException
	{
		pathWriter = PathWriterProvider.provider();
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
						PathWriterVisitor visitor)
	{
		return writer.accept(visitor)
			.orElseThrow(IllegalStateException::new);
	}

	@Test
	public void testExplicitByteOrder(TestInfo info) throws IOException,
							InterruptedException
	{
		final byte[] expected = "\ud83d\ude14|\ud83d\ude11"
			.getBytes(StandardCharsets.UTF_8); /* U+1F614|U+1F611 */
		final Path path = tmpDir.resolve(getCallerMethodsName(info));
		final Runnable r = WRITER
			.apply(newReadyWriter(pathWriter,
				new PathWriterVisitor(path, false,
						StandardCharsets.UTF_8,
						ByteOrder.LITTLE_ENDIAN)))
			.apply(new String(expected, StandardCharsets.UTF_8));
		executor.executeAndWait(Collections.nCopies(THREADS, r));
		final byte[] obtained = Optional.ofNullable(new FileReader(
						ByteOrder.LITTLE_ENDIAN)
				.awaitAndReadBytes(path, expected.length))
			.orElseThrow(AssertionError::new);
		assertArrayEquals(expected, obtained);
	}

	@Test
	public void testWordsWithExplicitDefaults(TestInfo info) throws IOException,
							InterruptedException
	{
		final byte[] expected = new StringBuilder(16)
				.append("foo").append(NEWLINE)
				.append("bar").append(NEWLINE)
				.append("baz").append(NEWLINE)
				.append("quux")
				.toString()
			.repeat(THREADS)
			.getBytes(StandardCharsets.UTF_8);
		final Path path = tmpDir.resolve(getCallerMethodsName(info));
		final Runnable r = WRITER
			.apply(newReadyWriter(pathWriter,
				new PathWriterVisitor(path, true,
						StandardCharsets.UTF_8,
						ByteOrder.BIG_ENDIAN)))
			.apply(new String(expected, StandardCharsets.UTF_8));
		executor.executeAndWait(Collections.nCopies(THREADS, r));
		final byte[] obtained = Optional.ofNullable(new FileReader()
				.awaitAndReadBytes(path, expected.length))
			.orElseThrow(AssertionError::new);
		assertArrayEquals(expected, obtained);
	}

	@Test
	public void testWordsWithImplicitDefaults(TestInfo info) throws IOException,
							InterruptedException
	{
		final byte[] expected = new StringBuilder(16)
				.append("foo").append(NEWLINE)
				.append("bar").append(NEWLINE)
				.append("baz").append(NEWLINE)
				.append("quux")
				.toString()
			.repeat(THREADS)
			.getBytes(StandardCharsets.UTF_8);
		final Path path = tmpDir.resolve(getCallerMethodsName(info));
		final Runnable r = WRITER
			.apply(newReadyWriter(pathWriter,
					new PathWriterVisitor(path, true)))
			.apply(new String(expected, StandardCharsets.UTF_8));
		executor.executeAndWait(Collections.nCopies(THREADS, r));
		final byte[] obtained = Optional.ofNullable(new FileReader()
				.awaitAndReadBytes(path, expected.length))
			.orElseThrow(AssertionError::new);
		assertArrayEquals(expected, obtained);
	}

	@Test
	public void testSerialAppendingOfLines(TestInfo info) throws IOException,
							InterruptedException
	{
		final byte[] expected = "foo bar"
			.concat(NEWLINE)
			.repeat(THREADS)
			.getBytes(StandardCharsets.UTF_8);
		final Path path = tmpDir.resolve(getCallerMethodsName(info));
		final Runnable r = WRITER
			.apply(newReadyWriter(pathWriter,
					new PathWriterVisitor(path, true)))
			.apply(new String(expected, StandardCharsets.UTF_8));
		executor.executeAndWait(Collections.nCopies(THREADS, r));
		final byte[] obtained = Optional.ofNullable(new FileReader()
				.awaitAndReadBytes(path, expected.length))
			.orElseThrow(AssertionError::new);
		assertArrayEquals(expected, obtained);
	}

	@Test
	public void testSerialTruncatingOfLines(TestInfo info) throws IOException,
							InterruptedException
	{
		final byte[] expected = "foo bar"
			.concat(NEWLINE)
			.getBytes(StandardCharsets.UTF_8);
		final Path path = tmpDir.resolve(getCallerMethodsName(info));
		final Runnable r = WRITER
			.apply(newReadyWriter(pathWriter,
					new PathWriterVisitor(path, false)))
			.apply(new String(expected, StandardCharsets.UTF_8));
		executor.executeAndWait(Collections.nCopies(THREADS, r));
		final byte[] obtained = Optional.ofNullable(new FileReader()
				.awaitAndReadBytes(path, expected.length))
			.orElseThrow(AssertionError::new);
		assertArrayEquals(expected, obtained);
	}
}
