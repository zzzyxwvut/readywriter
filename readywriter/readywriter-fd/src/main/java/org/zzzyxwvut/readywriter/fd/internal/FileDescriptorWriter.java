package org.zzzyxwvut.readywriter.fd.internal;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import java.util.logging.Level;

import org.zzzyxwvut.julics.annotation.Loggable;
import org.zzzyxwvut.julics.naming.LoggerRef;

import org.zzzyxwvut.readywriter.ReadyWriter;
import org.zzzyxwvut.readywriter.service.FileDescriptorWriterVisitor;

/**
 * A service provider capable of writing to an inherited file descriptor.
 * <p>
 * A file descriptor number greater than 2 shall only be used iff:
 * <ul>
 * <li>this JVM instance is run by a Linux kernel</li>
 * <li>the proc virtual file system is mounted at {@code /proc}</li>
 * <li>the file descriptor number is in a [3, SOFT_LIMIT) range, where
 *	SOFT_LIMIT can be retrieved as follows:<pre>
 *	<code>JVM_PID=12345	# Consider using `jps -l`.
 *	prlimit --output SOFT --noheadings --nofile \
 *				--pid ${JVM_PID}</code></pre></li>
 * <li>the file descriptor number is valid; viz, it refers to an open file,
 *	managed by this process, that is writable, not a directory, and either
 *	its resolved name or the file descriptor number itself, if the file is
 *	deleted, matches an arbitrary name pattern</li>
 * </ul>
 * <p>
 * <strong>All bets are off when a JVM instance closes an inherited file
 * descriptor and its number value is re-used anew.</strong>
 *
 * @implNote
 * The channel of a file descriptor greater than 2, when used, shall be
 * treated as with the {@link java.nio.file.StandardOpenOption#APPEND APPEND}
 * and {@link java.nio.file.StandardOpenOption#WRITE WRITE} options applied.
 */
final class FileDescriptorWriter implements FileDescriptorWriterProvider
{
	private static final FileChannel OUT_CHANNEL =
		new FileOutputStream(FileDescriptor.out).getChannel();
	private static final FileChannel ERR_CHANNEL =
		new FileOutputStream(FileDescriptor.err).getChannel();
	private static final Writer FORCER = (channel, buffer) -> {
		channel.write(buffer);
		channel.forceContent();
	};	/* fsync(3): EINVAL for pipes, sockets, FIFOs. */
	private static final Writer WRITER = ForcibleWritableByteChannel::write;
	private static final BiFunction<Integer, Pattern,
				ForcibleWritableByteChannel<?>> BINDER;

	private final Object lock = new Object();
	private final FileDescriptorWriterVisitor fdwVisitor;

	static {
		final boolean runByLinux = System.getProperty("os.name")
						.equalsIgnoreCase("linux")
				&& Files.isExecutable(Path.of("/proc/self"));
		BINDER = (runByLinux)
			? (fdNumber, fileName) -> (fdNumber > 2)
				? newChannel(ProcWritableFileDescriptorControl
					.dup(fdNumber, fileName))
				: (fdNumber > 1)	/* See unistd.h */
					? new StandardFileChannel(ERR_CHANNEL)
					: new StandardFileChannel(OUT_CHANNEL)
			: (fdNumber, fileName) -> (fdNumber > 1)
				? new StandardFileChannel(ERR_CHANNEL)
				: new StandardFileChannel(OUT_CHANNEL);
	}

	/**
	 * Constructs a new {@code FileDescriptorWriter} object.
	 *
	 * @implSpec
	 * This implementation dismisses file-name-pattern matching for file
	 * descriptor numbers less than 3.
	 *
	 * @param fdwVisitor a supported visitor
	 */
	FileDescriptorWriter(FileDescriptorWriterVisitor fdwVisitor)
	{
		this.fdwVisitor = Objects.requireNonNull(fdwVisitor,
							"fdwVisitor");
	}

	/**
	 * Constructs a new {@code FileDescriptorWriter} object. It is set to
	 * write to the standard output file channel, using the UTF-8 character
	 * set and the big-endian byte order.
	 */
	FileDescriptorWriter()
	{
		this(new FileDescriptorWriterVisitor(1));
	}

	private static ForcibleWritableByteChannel<?> newChannel(
						WritableByteChannel channel)
	{
		return (channel instanceof FileChannel)
			? new CloseableFileChannel((FileChannel) channel)
			: new NullFileChannel(channel);
	}

	private void doWrite(Writer writer, String message)
	{
		Objects.requireNonNull(message, "message");
		final ByteBuffer buffer = ByteBuffer.wrap(
						message.getBytes(
							fdwVisitor.charset()));
		buffer.order(fdwVisitor.byteOrder());

		synchronized (lock) {
			try (ForcibleWritableByteChannel<?> channel = BINDER
					.apply(fdwVisitor.fdNumber(),
						fdwVisitor.fileName())) {
				writer.write(channel, buffer);
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	/**
	 * Writes a message to an open file.
	 *
	 * @implNote Writing is guarded with a private monitor.
	 *
	 * @param message a message to write
	 * @throws UncheckedIOException if an I/O error occurs
	 */
	@Override
	public void write(String message)	{ doWrite(WRITER, message); }

	/**
	 * Writes a message to an open file, forcing any changes to
	 * the containing storage device.
	 *
	 * @implNote Writing is guarded with a private monitor.
	 *
	 * @param message a message to write
	 * @throws UncheckedIOException if an I/O error occurs
	 */
	@Override
	public void writeAndForce(String message) { doWrite(FORCER, message); }

	@Override
	public Kind kind()			{ return Kind.FILE_DESCRIPTOR; }

	@Override
	public <T extends Visitor<? extends T>> Optional<ReadyWriter> accept(
								T visitor)
	{
		return Objects.requireNonNull(visitor, "visitor")
			.visit(this)
			.filter(FileDescriptorWriterVisitor.class::isInstance)
			.map(Function.<FileDescriptorWriter>identity()
				.<FileDescriptorWriterVisitor>compose(
						FileDescriptorWriter::new)
				.compose(FileDescriptorWriterVisitor.class
								::cast));
	}

	/**
	 * This class controls duplication of writable file descriptors by
	 * availing itself of some {@code procfs}(5) interfaces.
	 */
	@Loggable
	static class ProcWritableFileDescriptorControl
	{
		private static final WritableByteChannel NULL_CHANNEL =
			Channels.newChannel(OutputStream.nullOutputStream());
		private static final Set<StandardOpenOption> APPEND_OPTS =
					Set.of(StandardOpenOption.APPEND,
						StandardOpenOption.WRITE);
		private static final Path PATH = Path.of("/proc/self/fd");
		private static final Consumer<Supplier<String>> FINER;
		private static final Consumer<Supplier<String>> OFFER;
		private static final int SOFT_LIMIT;

		static {
			final LoggerRef loggerRef = new LoggerRef(
				ProcWritableFileDescriptorControl.class);
			FINER = LoggerRef.logger()
				.apply(loggerRef)
				.apply(Level.FINE);
			OFFER = LoggerRef.logger()
				.apply(loggerRef)
				.apply(Level.OFF);
			final Function<Pattern,
					Function<String, Stream<String>>> worder =
				blanks -> blanks::splitAsStream;
			int softLimit = 1024 - 1; /* getconf _POSIX_OPEN_MAX */

			/*
			 * Parse the soft limit of the maximum open files property:
			 *
			 * Limit		Soft Limit	Hard Limit	Units
			 * Max open files	x		y		files
			 *
			 * See linux-source-*\/fs/proc/base.c
			 */
			try (BufferedReader reader = Files.newBufferedReader(
						Path.of("/proc/self/limits"),
						StandardCharsets.UTF_8)) {
				softLimit = reader
					.lines()
					.sequential()
					.dropWhile(Predicate.not(line ->
						line.startsWith(
							"Max open files")))
					.limit(1L)
					.flatMap(worder
						.apply(Pattern.compile("\\s+")))
					.filter(Pattern.compile("^\\d+$")
							.asMatchPredicate())
					.limit(1L)
					.mapToInt(Integer::valueOf)
					.peek(value -> FINER.accept(formatter()
						.format("Max open files: %d",
								value)))
					.findFirst()
					.getAsInt() - 1;
			} catch (final Exception ignored) {
				LoggerRef.errorLogger()
					.apply(loggerRef)
					.apply(Level.WARNING)
					.apply(ignored)
					.accept(() -> "Failed to parse "
						+ "the maximum open file "
						+ "soft limit");
			}

			SOFT_LIMIT = softLimit;
		}

		private ProcWritableFileDescriptorControl() { /* No instantiation. */ }

		private static Formatter formatter()
		{
			return (format, args) -> () ->
						String.format(format, args);
		}

		private static boolean alienated(int fdNumber,
						Pattern fileName,
						Path fdPath) throws IOException
		{
			final String filePath = fdPath.toFile()
				.getCanonicalPath();
			final String candidateName = Optional.ofNullable(
							Path.of(filePath)
						.getFileName())
				.orElseGet(() -> Path.of(""))
				.toString();

			if (fileName.matcher(candidateName).matches()
					&& !Files.isDirectory(fdPath)
					&& Files.isWritable(fdPath)) {
				FINER.accept(formatter()
					.format("Accepted fd: %d -> %s",
						fdNumber, filePath));
				return false;
			}

			OFFER.accept(formatter()
				.format("Rejected fd: %d -> %s",
						fdNumber, filePath));
			return true;
		}

		/**
		 * Duplicates an inherited file descriptor and binds its copy
		 * to {@link java.nio.channels.WritableByteChannel WritableByteChannel}.
		 *
		 * @param fdNumber an inherited file descriptor number
		 * @param fileName a file name pattern against which either
		 *	the name of an open file, to which the passed file
		 *	descriptor number refers, or the file descriptor number
		 *	itself, if the file is deleted, shall be matched
		 * @return a bound channel, if the passed file descriptor
		 *	number is valid and within the allowed value range;
		 *	else a bit-bucket channel
		 * @throws UncheckedIOException if an I/O error occurs
		 * @see FileDescriptorWriter
		 */
		static WritableByteChannel dup(int fdNumber, Pattern fileName)
		{
			Objects.requireNonNull(fileName, "fileName");

			if (fdNumber < 3 || fdNumber > SOFT_LIMIT)
				return NULL_CHANNEL; /* See unistd.h */

			try {
				final Path fdPath = PATH.resolve(
						String.valueOf(fdNumber));
				return (alienated(fdNumber, fileName, fdPath))
					? NULL_CHANNEL
					/*
					 * See *_open0(JNIEnv*, etc.) at
					 * libnio/fs/UnixNativeDispatcher.c
					 */
					: FileChannel.open(fdPath, APPEND_OPTS);
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@FunctionalInterface
		private interface Formatter
		{
			Supplier<String> format(String format, Object... args);
		}
	}

	/**
	 * A {@code WritableByteChannel} capable of forcing content updates to
	 * the storage device.
	 */
	static abstract class ForcibleWritableByteChannel<T extends WritableByteChannel>
						implements WritableByteChannel
	{
		/** A writable byte channel. */
		final T channel;

		/**
		 * Constructs a new {@code ForcibleWritableByteChannel}
		 * object.
		 *
		 * @param channel a writable byte channel
		 */
		ForcibleWritableByteChannel(T channel)
		{
			this.channel = Objects.requireNonNull(channel,
								"channel");
		}

		/**
		 * Attempts to force content updates to the storage device.
		 *
		 * @throws IOException if an I/O error occurs
		 */
		public abstract void forceContent() throws IOException;

		@Override
		public int write(ByteBuffer buffer) throws IOException
		{
			Objects.requireNonNull(buffer, "buffer");
			return channel.write(buffer);
		}

		@Override
		public boolean isOpen()		{ return channel.isOpen(); }

		@Override
		public abstract void close() throws IOException;
	}

	private static class StandardFileChannel extends
				ForcibleWritableByteChannel<FileChannel>
	{
		StandardFileChannel(FileChannel channel) { super(channel); }

		/**
		 * {@inheritDoc}
		 *
		 * @implSpec
		 * Although this implementation is exclusively used with
		 * <code>FileDescriptor.{out,err}</code>, no assumption is made
		 * whether their storage devices support synchronisation.
		 * <p>
		 * E.g.<pre><code>
		 *	JVM_PID=12345	# Consider using `jps -l`.
		 *	sync --data /proc/${JVM_PID}/fd/1</code></pre>
		 */
		@Override
		public void forceContent() throws IOException
		{
			channel.force(false);
		}

		/**
		 * {@inheritDoc}
		 *
		 * @implSpec
		 * This implementation does nothing.
		 */
		@Override
		public void close() throws IOException { /* NO-OP. */ }
	}

	private static class CloseableFileChannel extends StandardFileChannel
	{
		CloseableFileChannel(FileChannel channel) { super(channel); }

		@Override
		public void close() throws IOException { channel.close(); }
	}

	private static class NullFileChannel extends
				ForcibleWritableByteChannel<WritableByteChannel>
	{
		NullFileChannel(WritableByteChannel channel) { super(channel); }

		/**
		 * {@inheritDoc}
		 *
		 * @implSpec
		 * This implementation does nothing.
		 */
		@Override
		public void forceContent() throws IOException { /* NO-OP. */ }

		/**
		 * {@inheritDoc}
		 *
		 * @implSpec
		 * This implementation does nothing and returns {@code 0}.
		 */
		@Override
		public int write(ByteBuffer buffer) throws IOException
		{
			Objects.requireNonNull(buffer, "buffer");
			return 0;
		}

		/**
		 * {@inheritDoc}
		 *
		 * @implSpec
		 * This implementation does nothing.
		 */
		@Override
		public void close() throws IOException { /* NO-OP. */ }
	}

	@FunctionalInterface
	private interface Writer
	{
		void write(ForcibleWritableByteChannel<?> channel,
					ByteBuffer buffer) throws IOException;
	}
}
