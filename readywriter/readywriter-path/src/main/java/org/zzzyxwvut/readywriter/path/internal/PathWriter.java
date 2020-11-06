package org.zzzyxwvut.readywriter.path.internal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileSystems;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.zzzyxwvut.readywriter.ReadyWriter;
import org.zzzyxwvut.readywriter.service.PathWriterVisitor;

/** A service provider capable of writing to a path. */
final class PathWriter implements PathWriterProvider
{
	private static final Set<StandardOpenOption> TRUNCATE_OPTS =
				Set.of(StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
	private static final Set<StandardOpenOption> APPEND_OPTS =
				Set.of(StandardOpenOption.CREATE,
					StandardOpenOption.APPEND,
					StandardOpenOption.WRITE);
	private static final FileAttribute<?>[] FILE_ATTRIBUTES =
				(FileSystems.getDefault()
					.supportedFileAttributeViews()
					.contains("posix"))
		? new FileAttribute<?>[] {
			PosixFilePermissions.asFileAttribute(
				PosixFilePermissions.fromString("rw-r-----"))
		}
		: new FileAttribute<?>[0];
	private static final Writer FORCER = (channel, buffer) -> {
		channel.write(buffer);
		channel.force(false);
	};	/* fsync(3): EINVAL for pipes, sockets, FIFOs. */
	private static final Writer WRITER = FileChannel::write;

	private final Object lock = new Object();
	private final PathWriterVisitor pwVisitor;
	private final Set<StandardOpenOption> openOptions;
	private final Seeker seeker;

	/**
	 * Constructs a new {@code PathWriter} object.
	 *
	 * @param pwVisitor a supported visitor
	 */
	PathWriter(PathWriterVisitor pwVisitor)
	{
		this.pwVisitor = Objects.requireNonNull(pwVisitor, "pwVisitor");

		if (pwVisitor.appendable()) {
			openOptions = APPEND_OPTS;
			seeker = channel -> channel;
		} else {
			openOptions = TRUNCATE_OPTS;
			seeker = channel -> channel.position(0L);
		}	/* lseek(3): ESPIPE for pipes, sockets, FIFOs. */
	}

	/**
	 * Constructs a new {@code PathWriter} object. It is set to write to
	 * a new templet-named path, appending to the end of a file and using
	 * the UTF-8 character set and the big-endian byte order.
	 */
	PathWriter()
	{
		this(new PathWriterVisitor(null, true));
	}

	private void doWrite(Writer writer, String message)
	{
		Objects.requireNonNull(message, "message");
		final ByteBuffer buffer = ByteBuffer.wrap(
						message.getBytes(
							pwVisitor.charset()));
		buffer.order(pwVisitor.byteOrder());

		synchronized (lock) {
			try (FileChannel channel = FileChannel.open(
							pwVisitor.path(),
							openOptions,
							FILE_ATTRIBUTES)) {
				/* Closing the channel releases the lock. */
				channel.lock();
				writer.write(seeker.seek(channel), buffer);
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	/**
	 * Writes a message to a path.
	 *
	 * @implNote Writing is guarded with a private monitor.
	 *
	 * @param message a message to write
	 * @throws UncheckedIOException if an I/O error occurs
	 */
	@Override
	public void write(String message)	{ doWrite(WRITER, message); }

	/**
	 * Writes a message to a path, forcing any changes to the containing
	 * storage device.
	 *
	 * @implNote Writing is guarded with a private monitor.
	 *
	 * @param message a message to write
	 * @throws UncheckedIOException if an I/O error occurs
	 */
	@Override
	public void writeAndForce(String message) { doWrite(FORCER, message); }

	@Override
	public Kind kind()			{ return Kind.PATH; }

	@Override
	public <T extends Visitor<? extends T>> Optional<ReadyWriter> accept(
								T visitor)
	{
		return Objects.requireNonNull(visitor, "visitor")
			.visit(this)
			.filter(PathWriterVisitor.class::isInstance)
			.map(Function.<PathWriter>identity()
				.<PathWriterVisitor>compose(PathWriter::new)
				.compose(PathWriterVisitor.class::cast));
	}

	@FunctionalInterface
	private interface Seeker
	{
		FileChannel seek(FileChannel channel) throws IOException;
	}

	@FunctionalInterface
	private interface Writer
	{
		void write(FileChannel channel, ByteBuffer buffer)
							throws IOException;
	}
}
