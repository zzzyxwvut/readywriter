package org.zzzyxwvut.readywriter.service;

import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import java.util.logging.Level;

import org.zzzyxwvut.julics.annotation.Loggable;
import org.zzzyxwvut.julics.naming.LoggerRef;

import org.zzzyxwvut.readywriter.ReadyWriter.Kind;
import org.zzzyxwvut.readywriter.ReadyWriter.Visitor;
import org.zzzyxwvut.readywriter.ReadyWriter;

/**
 * A {@link Visitor} fit for
 * the {@link org.zzzyxwvut.readywriter.ReadyWriter.Kind#PATH PATH} service
 * provider kind.
 */
public final class PathWriterVisitor implements Visitor<PathWriterVisitor>
{
	private final Function<ReadyWriter,
			Optional<PathWriterVisitor>> visitor =
				Support.<PathWriterVisitor>visitor()
		.apply(Kind.PATH)
		.apply(this);
	private final Path path;
	private final boolean appendable;
	private final Charset charset;
	private final ByteOrder byteOrder;

	/**
	 * Constructs a new {@code PathWriterVisitor} object.
	 *
	 * @implNote
	 * For {@code null} paths, a new templet-named file shall be attempted
	 * to be created in the ${java.io.tmpdir}/jrw_messages directory, based
	 * on the jrw_{@literal<PID>}_[:xdigit:]{10}.msg pattern.
	 *
	 * @param path the path to a file, or a templet-named path, if
	 *	{@code null}
	 * @param appendable whether to write to the end of a file rather than
	 *	its beginning
	 * @param charset the character set of a file
	 * @param byteOrder the byte order of a file
	 */
	public PathWriterVisitor(Path path, boolean appendable,
					Charset charset, ByteOrder byteOrder)
	{
		this.path = (path == null)
			? TmpDirectoryPathGenerator.generatePath()
			: path;
		this.appendable = appendable;
		this.charset = Objects.requireNonNull(charset, "charset");
		this.byteOrder = Objects.requireNonNull(byteOrder, "byteOrder");
	}

	/**
	 * Constructs a new {@code PathWriterVisitor} object. It is set to use
	 * the UTF-8 character set and the big-endian byte order for a file.
	 *
	 * @param path the path to a file, or a templet-named path, if
	 *	{@code null}
	 * @param appendable whether to write to the end of a file rather than
	 *	its beginning
	 * @see #PathWriterVisitor(Path, boolean, Charset, ByteOrder)
	 */
	public PathWriterVisitor(Path path, boolean appendable)
	{
		this(path, appendable, StandardCharsets.UTF_8,
							ByteOrder.BIG_ENDIAN);
	}

	/**
	 * Returns the path to a file.
	 *
	 * @return the path to a file
	 */
	public Path path()		{ return path; }

	/**
	 * Returns whether the file is appendable.
	 *
	 * @return whether to write to the end of a file rather than its
	 *	beginning
	 */
	public boolean appendable()	{ return appendable; }

	/**
	 * Returns the character set of a file.
	 *
	 * @return the character set of a file
	 */
	public Charset charset()	{ return charset; }

	/**
	 * Returns the byte order of a file.
	 *
	 * @return the byte order of a file
	 */
	public ByteOrder byteOrder()	{ return byteOrder; }

	@Override
	public Optional<PathWriterVisitor> visit(ReadyWriter writer)
	{
		return visitor.apply(writer);
	}

	/**
	 * This class serves for generation of templet-named temporary paths.
	 */
	@Loggable
	static class TmpDirectoryPathGenerator
	{
		private static final String TMP_JRW_DIR;
		private static final long MASK = 0x7fffffffL;
		private static final byte[] FILE_PREFIX;

		static {
			final Function<Throwable, Consumer<Supplier<String>>>
						warner = LoggerRef.errorLogger()
				.apply(new LoggerRef(
					TmpDirectoryPathGenerator.class))
				.apply(Level.WARNING);
			long pid = 0L;
			final String tmpDir = System.getProperty(
							"java.io.tmpdir");

			try {
				pid = ProcessHandle.current().pid();
			} catch (final UnsupportedOperationException ignored) {
				warner.apply(ignored)
					.accept(() ->
						"Failed to obtain the process ID");
			}

			FILE_PREFIX = new StringBuilder(32)
				.append("jrw_")
				.append(pid)
				.append("_")		/* Apes "%0*d". */
				.append(String.format(String.format("%%0%dd",
					Long.toString(MASK).length()), 0))
				.toString()
				.getBytes(StandardCharsets.UTF_8);
			final Path jrwPath = Path.of(tmpDir, "jrw_messages");
			final FileAttribute<?>[] dirAttributes =
					(FileSystems.getDefault()
						.supportedFileAttributeViews()
						.contains("posix"))
				? new FileAttribute<?>[] {
					PosixFilePermissions.asFileAttribute(
						PosixFilePermissions
							.fromString("rwxr-x---"))
				}
				: new FileAttribute<?>[0];
			String jrwDir = tmpDir;

			try {
				if (!Files.isDirectory(jrwPath))
					Files.createDirectory(jrwPath,
							dirAttributes);

				jrwDir = jrwPath.toString();
			} catch (final IOException ignored) {
				final Function<String, Supplier<String>>
						messager = message -> () ->
					String.format("Failed to create %s",
								message);
				warner.apply(ignored)
					.accept(messager
						.apply(jrwPath.toString()));
			}

			TMP_JRW_DIR = jrwDir;
		}

		private TmpDirectoryPathGenerator() { /* No instantiation. */ }

		/**
		 * Generates a jrw_{@literal<PID>}_[:xdigit:]{10}.msg pattern
		 * path in the ${java.io.tmpdir}/jrw_messages directory.
		 *
		 * @return a templet-named path
		 */
		static Path generatePath()
		{
			final String part = Long.toHexString(MASK
				& ThreadLocalRandom.current().nextLong());
			return Path.of(TMP_JRW_DIR, new StringBuilder(32)
				.append(new String(FILE_PREFIX, 0,
					FILE_PREFIX.length - part.length()))
				.append(part)
				.append(".msg")
				.toString());
		}
	}
}
