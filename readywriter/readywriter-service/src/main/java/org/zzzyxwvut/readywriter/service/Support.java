package org.zzzyxwvut.readywriter.service;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import java.util.logging.Level;

import org.zzzyxwvut.julics.annotation.Loggable;
import org.zzzyxwvut.julics.naming.LoggerRef;

import org.zzzyxwvut.readywriter.ReadyWriter.Kind;
import org.zzzyxwvut.readywriter.ReadyWriter.Visitor;
import org.zzzyxwvut.readywriter.ReadyWriter;

/** This class lends service-related support. */
@Loggable
class Support
{
	private Support() { /* No instantiation. */ }

	/**
	 * A functional interface that takes a message and, as a side effect,
	 * logs the message at {@code Level.FINE}, using a logger bound to
	 * this class, and does not return a value.
	 */
	static final Consumer<Supplier<String>> FINER = LoggerRef.logger()
		.apply(new LoggerRef(Support.class))
		.apply(Level.FINE);

	/**
	 * Returns a functional interface that takes an object and
	 * returns a functional interface that takes an object and
	 * returns the leftmost object.
	 *
	 * @param <T> a type
	 * @param <U> a type
	 * @return a curried function
	 */
	static <T, U> Function<T, Function<U, T>> constant()
	{
		return t -> u -> t;
	}

	/**
	 * Returns a functional interface that takes a provider kind and
	 * returns a functional interface that takes a visitor and
	 * returns a functional interface that takes a writer and
	 * returns an optional with the visitor, if the kind of the passed
	 * writer matches the passed kind, else an empty optional.
	 *
	 * @param <T> the type of a visitor
	 * @return a curried function
	 */
	static <T extends Visitor<T>> Function<Kind,
			Function<T,
			Function<ReadyWriter, Optional<T>>>> visitor()
	{
		return kind -> visitor -> writer -> Optional.of(writer)
			.filter(matcher()
				.apply(kind))
			.map(Support.<T, ReadyWriter>constant()
				.apply(visitor));
	}

	private static Function<Kind, Predicate<ReadyWriter>> matcher()
	{
		return kind -> writer -> writer.kind() == kind;
	}
}
