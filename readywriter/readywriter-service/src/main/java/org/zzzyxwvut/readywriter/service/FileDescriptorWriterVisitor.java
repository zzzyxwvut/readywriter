package org.zzzyxwvut.readywriter.service;

import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.zzzyxwvut.readywriter.ReadyWriter.Kind;
import org.zzzyxwvut.readywriter.ReadyWriter.Visitor;
import org.zzzyxwvut.readywriter.ReadyWriter;

/**
 * A {@link Visitor} fit for
 * the {@link org.zzzyxwvut.readywriter.ReadyWriter.Kind#FILE_DESCRIPTOR FILE_DESCRIPTOR}
 * service provider kind.
 */
public final class FileDescriptorWriterVisitor implements
					Visitor<FileDescriptorWriterVisitor>
{
	private static Pattern NO_DOTS = Pattern.compile("(?m)^[^.]+$");

	private final Function<ReadyWriter,
			Optional<FileDescriptorWriterVisitor>> visitor =
				Support.<FileDescriptorWriterVisitor>visitor()
		.apply(Kind.FILE_DESCRIPTOR)
		.apply(this);
	private final int fdNumber;
	private final Pattern fileName;
	private final Charset charset;
	private final ByteOrder byteOrder;

	/**
	 * Constructs a new {@code FileDescriptorWriterVisitor} object.
	 *
	 * @param fdNumber an open-file descriptor number, or the standard
	 *	output descriptor number, if less than 1
	 * @param fileName a file name pattern that should match the name of
	 *	an open file to which the passed file descriptor number is
	 *	assigned
	 * @param charset the character set of an open file
	 * @param byteOrder the byte order of an open file
	 */
	public FileDescriptorWriterVisitor(int fdNumber, Pattern fileName,
					Charset charset, ByteOrder byteOrder)
	{
		this.fdNumber = (fdNumber < 1)
			? 1
			: fdNumber;
		this.fileName = Objects.requireNonNull(fileName, "fileName");
		this.charset = Objects.requireNonNull(charset, "charset");
		this.byteOrder = Objects.requireNonNull(byteOrder, "byteOrder");
	}

	/**
	 * Constructs a new {@code FileDescriptorWriterVisitor} object. It is
	 * set to use the contains-no-dots file name pattern and the UTF-8
	 * character set and the big-endian byte order for an open file.
	 *
	 * @param fdNumber an open-file descriptor number, or the standard
	 *	output descriptor number, if less than 1
	 */
	public FileDescriptorWriterVisitor(int fdNumber)
	{
		this(fdNumber, NO_DOTS, StandardCharsets.UTF_8,
							ByteOrder.BIG_ENDIAN);
	}

	/**
	 * Returns an inherited file descriptor number.
	 *
	 * @return an inherited file descriptor number
	 */
	public int fdNumber()		{ return fdNumber; }

	/**
	 * Returns a file name pattern that should match the name of an open
	 * file to which this visitor's file-descriptor-number was assigned.
	 *
	 * @return a file name pattern
	 */
	public Pattern fileName()	{ return fileName; }

	/**
	 * Returns the character set of an open file.
	 *
	 * @return the character set of an open file
	 */
	public Charset charset()	{ return charset; }

	/**
	 * Returns the byte order of an open file.
	 *
	 * @return the byte order of an open file
	 */
	public ByteOrder byteOrder()	{ return byteOrder; }

	@Override
	public Optional<FileDescriptorWriterVisitor> visit(ReadyWriter writer)
	{
		return visitor.apply(writer);
	}
}
