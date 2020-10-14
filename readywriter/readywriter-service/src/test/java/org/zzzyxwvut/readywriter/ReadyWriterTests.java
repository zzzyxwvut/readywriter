package org.zzzyxwvut.readywriter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import org.zzzyxwvut.readywriter.ReadyWriter.Kind;
import org.zzzyxwvut.readywriter.ReadyWriter.Visitor;
import org.zzzyxwvut.readywriter.service.Lookup.DefaultReadyWriter;
import org.zzzyxwvut.readywriter.service.Lookup.DefaultVisitor;
import org.zzzyxwvut.readywriter.service.Lookup;

public class ReadyWriterTests
{
	private static Function<ReadyWriter,
				Function<String, Executable>> messager()
	{
		return writer -> message -> () -> writer.write(message);
	}

	@Test
	public void testProvidersUnavailable()
	{
		assertTrue(Lookup.readyWriter(null).isEmpty());
		assertTrue(Lookup.names().isEmpty());
	}

	@Test
	public void testNewInstanceTransition()
	{
		final ReadyWriter writer0 = new CandidateWriter(
						new CandidateWriterVisitor());
		final Executable helloer0 = messager()
			.apply(writer0)
			.apply("hello");
		assertDoesNotThrow(helloer0);

		final ReadyWriter writer1 = writer0.accept(
						new CandidateWriterVisitor(
					System.err,
					StandardCharsets.UTF_8))
			.orElseThrow(IllegalStateException::new);
		assertNotEquals(writer0, writer1);

		final Optional<String> closedMessage = Optional.ofNullable(
				assertThrows(UncheckedIOException.class,
								helloer0)
					.getCause())
			.map(Throwable::getMessage)
			.filter(Pattern.compile("(?i).*?closed.*?")
				.asMatchPredicate());
		assertTrue(closedMessage.isPresent());

		final Executable helloer1 = messager()
			.apply(writer1)
			.apply("hello");
		assertDoesNotThrow(helloer1);
	}

	@Test
	public void testEscapingOfThisWithDefaultVisitor()
	{
		final ReadyWriter writer = new DefaultReadyWriter(
						new CandidateWriter(
					new CandidateWriterVisitor()));
		new Thread(runner()
				.apply(writer)
				.apply(new DefaultVisitor(Kind.OTHER)))
			.start();
	}	/* Cf. JLS-11, $17.5.3. */

	private static Function<ReadyWriter,
				Function<DefaultVisitor, Runnable>> runner()
	{
		return writer -> visitor -> () -> {
			final ReadyWriter readyWriter = writer.accept(visitor)
				.orElseThrow(IllegalStateException::new);
			final Executable helloer = messager()
				.apply(readyWriter)
				.apply("hello");
			assertEquals(writer, readyWriter);
			assertDoesNotThrow(helloer);
		};
	}

	static final class CandidateWriter implements ReadyWriter
	{
		private final Object lock = new Object();
		private final OutputStream stream;
		private final Charset charset;
		private final Function<CandidateWriterVisitor, ReadyWriter>
								writer;

		CandidateWriter(CandidateWriterVisitor visitor)
		{
			Objects.requireNonNull(visitor, "visitor");
			stream = visitor.stream();
			charset = visitor.charset();
			writer = writer()
				.apply(lock)
				.apply(stream);
		}

		@Override
		public void write(String message)
		{
			Objects.requireNonNull(message, "message");

			try {
				synchronized (lock) {
					stream.write(message.getBytes(charset));
				}
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Override
		public void writeAndForce(String message)
		{
			Objects.requireNonNull(message, "message");

			try {
				synchronized (lock) {
					stream.write(message.getBytes(charset));
					stream.flush();
				}
			} catch (final IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Override
		public Kind kind()	{ return Kind.OTHER; }

		/**
		 * {@inheritDoc}
		 *
		 * @implSpec
		 * This implementation may block, with supported visitors,
		 * until closing of the current stream is carried out.
		 *
		 * @param visitor {@inheritDoc}
		 */
		@Override
		public <T extends Visitor<? extends T>> Optional<ReadyWriter>
							accept(T visitor)
		{
			return Objects.requireNonNull(visitor, "visitor")
				.visit(this)
				.filter(CandidateWriterVisitor.class::isInstance)
				.map(writer.compose(
					CandidateWriterVisitor.class::cast));
		}

		private static Function<Object,
					Function<OutputStream,
					Function<CandidateWriterVisitor,
							ReadyWriter>>> writer()
		{
			return lock -> oldStream -> newConfig -> {
				try {
					synchronized (lock) {
						oldStream.close();
					}
				} catch (final IOException e) {
					throw new UncheckedIOException(e);
				}

				return new CandidateWriter(newConfig);
			};
		}
	}

	static final class CandidateWriterVisitor implements
					Visitor<CandidateWriterVisitor>
	{
		private final OutputStream stream;
		private final Charset charset;

		CandidateWriterVisitor(OutputStream stream, Charset charset)
		{
			this.stream = Objects.requireNonNull(stream, "stream");
			this.charset = Objects.requireNonNull(charset, "charset");
		}

		CandidateWriterVisitor()
		{
			this(OutputStream.nullOutputStream(),
						StandardCharsets.UTF_8);
		}

		OutputStream stream()			{ return stream; }
		Charset charset()			{ return charset; }

		@Override
		public Optional<CandidateWriterVisitor> visit(ReadyWriter writer)
		{
			return (writer.kind() == Kind.OTHER)
				? Optional.of(this)
				: Optional.empty();
		}
	}
}
