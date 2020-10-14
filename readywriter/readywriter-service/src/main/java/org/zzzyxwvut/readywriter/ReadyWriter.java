package org.zzzyxwvut.readywriter;

import java.util.Optional;

/** A service capable of writing to a sink. */
public interface ReadyWriter
{
	/**
	 * Writes a message to a sink.
	 *
	 * @param message a message to write
	 */
	void write(String message);

	/**
	 * Writes a message to a sink, forcing any changes to the containing
	 * storage device.
	 *
	 * @param message a message to write
	 */
	void writeAndForce(String message);

	/**
	 * Returns the kind of this service provider.
	 *
	 * @return the kind of this service provider
	 */
	Kind kind();

	/**
	 * Returns a new instance of this service provider for the supported
	 * visitor.
	 *
	 * @param <T> the type of a visitor
	 * @param visitor a visitor to apply to this service provider
	 * @return an optional with a new instance of this service provider if
	 *	the passed visitor is supported, otherwise an empty optional
	 */
	<T extends Visitor<? extends T>> Optional<ReadyWriter> accept(T visitor);

	/**
	 * A visitor for a service provider.
	 *
	 * @param <T> the type of a visitor
	 */
	interface Visitor<T extends Visitor<? extends T>>
	{
		/**
		 * Visits a service provider.
		 *
		 * @param visitable a service provider to visit
		 * @return an optional with a visitor for the passed service
		 *	provider if the same {@link ReadyWriter.Kind} is
		 *	claimed by both, otherwise an empty optional
		 */
		Optional<T> visit(ReadyWriter visitable);
	}

	/** This enumeration specifies kinds of service providers. */
	enum Kind
	{
		/** The other kind. */
		OTHER,

		/** The file descriptor kind. */
		FILE_DESCRIPTOR,

		/** The path kind. */
		PATH
	}
}
