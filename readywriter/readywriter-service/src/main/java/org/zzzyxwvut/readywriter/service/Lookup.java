package org.zzzyxwvut.readywriter.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader.Provider;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.zzzyxwvut.readywriter.ReadyWriter.Kind;
import org.zzzyxwvut.readywriter.ReadyWriter.Visitor;
import org.zzzyxwvut.readywriter.ReadyWriter;
import org.zzzyxwvut.readywriter.annotation.Namable;

/**
 * A service loader for {@code ReadyWriter} providers.
 * <p>
 * Examples.
 * <pre>
 * Obtain a provider:
 *
 *	<code>Optional&lt;ReadyWriter&gt; writer0 = Lookup.readyWriter(null);</code>
 *
 * Obtain a provider of the specified kind:
 *
 *	<code>Optional&lt;ReadyWriter&gt; writer1 = Lookup.readyWriter(
 *				new DefaultVisitor(Kind.PATH));</code>
 *
 * Obtain the specified provider:
 *
 *	<code>Optional&lt;ReadyWriter&gt; writer2 = Lookup.readyWriter(
 *				"org.foo.AlphaProvider", null);</code>
 *
 * Obtain a provider and re-configure it:
 *
 *	<code>Optional&lt;ReadyWriter&gt; writer3 = Lookup.readyWriter(
 *		new PathWriterVisitor(Path.of("/tmp/sink"), true));</code>
 *
 * Obtain the specified provider and re-configure it:
 *
 *	<code>Optional&lt;ReadyWriter&gt; writer4 = Lookup.readyWriter(
 *				"org.foo.BetaProvider",
 *		new PathWriterVisitor(Path.of("/tmp/sink"), true));</code>
 *
 * Attempt writing a message using the obtained provider, if any:
 *
 *	<code>Lookup.readyWriter(new DefaultVisitor(Kind.FILE_DESCRIPTOR))
 *		.ifPresent(Lookup.messager()
 *			.apply("hello world\n"));</code>
 * </pre>
 */
public interface Lookup
{
	/**
	 * Looks up a service provider in the specified module layer and its
	 * ancestors.
	 * <p>
	 * The found provider is reconfigured with the passed visitor, if any.
	 *
	 * @param <T> the type of a visitor
	 * @param moduleLayer a module layer
	 * @param visitor a visitor for a provider to reconfigure with, or
	 *	{@code null}
	 * @return an optional with a service provider, if found and its kind
	 *	is supported by the passed visitor, if any, otherwise an empty
	 *	optional
	 */
	static <T extends Visitor<? extends T>> Optional<ReadyWriter> readyWriter(
					ModuleLayer moduleLayer, T visitor)
	{
		Objects.requireNonNull(moduleLayer, "moduleLayer");
		return (visitor == null)
			? ServiceLoader.load(moduleLayer, ReadyWriter.class)
				.stream()
				.limit(1L)
				.map(Provider::get)
				.peek(Lookup.<ReadyWriter>peeker())
				.findAny()
			: ServiceLoader.load(moduleLayer, ReadyWriter.class)
				.stream()
				.map(Provider::get)
				.peek(Lookup.<ReadyWriter>peeker())
				.flatMap(Lookup.<T>configurer()
					.apply(visitor))
				.findAny();
	}

	/**
	 * Looks up a service provider in the boot module layer.
	 * <p>
	 * The found provider is reconfigured with the passed visitor, if any.
	 *
	 * @param <T> the type of a visitor
	 * @param visitor a visitor for a provider to reconfigure with, or
	 *	{@code null}
	 * @return an optional with a service provider, if found and its kind
	 *	is supported by the passed visitor, if any, otherwise an empty
	 *	optional
	 * @see #readyWriter(ModuleLayer, Visitor)
	 */
	static <T extends Visitor<? extends T>> Optional<ReadyWriter> readyWriter(
								T visitor)
	{
		return readyWriter(ModuleLayer.boot(), visitor);
	}

	/**
	 * Looks up the requested service provider in the specified module
	 * layer and its ancestors.
	 * <p>
	 * The found provider is reconfigured with the passed visitor, if any.
	 *
	 * @param <T> the type of a visitor
	 * @param providerName the name of a provider
	 * @param moduleLayer the module layer
	 * @param visitor a visitor for a provider to reconfigure with, or
	 *	{@code null}
	 * @return an optional with a service provider, if found and its kind
	 *	is supported by the passed visitor, if any, otherwise an empty
	 *	optional
	 */
	static <T extends Visitor<? extends T>> Optional<ReadyWriter> readyWriter(
							String providerName,
							ModuleLayer moduleLayer,
							T visitor)
	{
		Objects.requireNonNull(providerName, "providerName");
		Objects.requireNonNull(moduleLayer, "moduleLayer");
		return (visitor == null)
			? ServiceLoader.load(moduleLayer, ReadyWriter.class)
				.stream()
				.filter(matcher()
					.apply(providerName))
				.peek(Lookup.<Provider<ReadyWriter>>peeker())
				.limit(1L)
				.map(Provider::get)
				.findAny()
			: ServiceLoader.load(moduleLayer, ReadyWriter.class)
				.stream()
				.filter(matcher()
					.apply(providerName))
				.peek(Lookup.<Provider<ReadyWriter>>peeker())
				.flatMap(Function.<Stream<ReadyWriter>>identity()
					.<ReadyWriter>compose(
						Lookup.<T>configurer()
							.apply(visitor))
					.compose(Provider::get))
				.findAny();
	}

	/**
	 * Looks up the requested service provider in the boot module layer.
	 * <p>
	 * The found provider is reconfigured with the passed visitor, if any.
	 *
	 * @param <T> the type of a visitor
	 * @param providerName the name of a provider
	 * @param visitor a visitor for a provider to reconfigure with, or
	 *	{@code null}
	 * @return an optional with the requested service provider, if found
	 *	and its kind is supported by the passed visitor, if any,
	 *	otherwise an empty optional
	 * @see #readyWriter(String, ModuleLayer, Visitor)
	 */
	static <T extends Visitor<? extends T>> Optional<ReadyWriter> readyWriter(
					String providerName, T visitor)
	{
		return readyWriter(providerName, ModuleLayer.boot(), visitor);
	}

	private static Function<Class<? extends ReadyWriter>, Supplier<String>>
								typeNamer()
	{
		return klass -> klass::getName;
	}

	private static Function<Provider<ReadyWriter>, String> namer()
	{
		return provider -> Optional.ofNullable(provider.type()
				.getAnnotation(Namable.class))
			.map(Namable::value)
			.orElseGet(typeNamer()
				.apply(provider.type()));
	}

	/**
	 * Lists names of all found providers in the specified module layer and
	 * its ancestors, without instantiating providers.
	 *
	 * @param moduleLayer the module layer
	 * @return the list of names of all found providers
	 * @throws UnsupportedOperationException if any modification of
	 *	the returned list is attempted
	 */
	static List<String> names(ModuleLayer moduleLayer)
	{
		Objects.requireNonNull(moduleLayer, "moduleLayer");
		return Function.<List<String>>identity()
						.<ModuleLayer>compose(layer ->
				ServiceLoader.load(layer, ReadyWriter.class)
					.stream()
					.map(namer())
					.peek(Lookup.<String>peeker())
					.sorted(Comparator.naturalOrder())
					.collect(Collectors.collectingAndThen(
						Collectors.toCollection(
							ArrayList::new),
						Collections::unmodifiableList)))
			.apply(moduleLayer);
	}

	/**
	 * Lists names of all found providers in the boot module layer, without
	 * instantiating providers.
	 *
	 * @return the list of names of all found providers
	 * @throws UnsupportedOperationException if any modification of
	 *	the returned list is attempted
	 * @see #names(ModuleLayer)
	 */
	static List<String> names()	{ return names(ModuleLayer.boot()); }

	/**
	 * Returns a functional interface that takes a message and returns
	 * a functional interface that takes a writer and, as a side effect,
	 * writes the message and does not return a value.
	 *
	 * @return a curried function
	 */
	static Function<String, Consumer<ReadyWriter>> messager()
	{
		return message -> writer -> writer.write(message);
	}

	/**
	 * Returns a functional interface that takes a message and returns
	 * a functional interface that takes a writer and, as a side effect,
	 * writes the message, forcing any changes to the containing storage
	 * device, and does not return a value.
	 *
	 * @return a curried function
	 */
	static Function<String, Consumer<ReadyWriter>> forcingMessager()
	{
		return message -> writer -> writer.writeAndForce(message);
	}

	private static <T> Function<T, Supplier<String>> stringer()
	{
		return element -> element::toString;
	}

	private static <T> Consumer<T> peeker()
	{
		return element -> Support.FINER.accept(Lookup.<T>stringer()
							.apply(element));
	}

	private static Function<String, Predicate<Provider<ReadyWriter>>>
								matcher()
	{
		return name -> provider -> name.equals(namer()
							.apply(provider));
	}

	private static <T extends Visitor<? extends T>>
			Function<T,
				Function<ReadyWriter, Stream<ReadyWriter>>>
								configurer()
	{
		/*
		 * An instance of Lookup.DefaultVisitor, when used, allows
		 * targeted selection, via ReadyWriter.Kind matching, of
		 * a service provider without a necessity of fleshing out a new
		 * instance of the provider. However, the acceptance path of
		 * Lookup.DefaultReadyWriter is never taken; for that provider
		 * is not bound to its service and, therefore, cannot be
		 * located and loaded by java.util.ServiceLoader.
		 */
		return visitor -> writer -> (DefaultVisitor.class
							.isInstance(visitor))
			? visitor.visit(writer)
				.stream()
				.map(Support.<ReadyWriter, T>constant()
					.apply(writer))
			: writer.accept(visitor)
				.stream();
	}

	/** A delegating kind of {@code ReadyWriter}. */
	@Namable("org.zzzyxwvut.readywriter.DefaultReadyWriter")
	final class DefaultReadyWriter implements ReadyWriter
	{
		private final ReadyWriter writer;

		/**
		 * Constructs a new {@code DefaultReadyWriter} object.
		 * <p>
		 * (This provider is not bound to its service.)
		 *
		 * @param writer a service provider
		 */
		public DefaultReadyWriter(ReadyWriter writer)
		{
			this.writer = Objects.requireNonNull(writer, "writer");
		}

		@Override
		public void write(String message)
		{
			Objects.requireNonNull(message, "message");
			writer.write(message);
		}

		@Override
		public void writeAndForce(String message)
		{
			Objects.requireNonNull(message, "message");
			writer.writeAndForce(message);
		}

		@Override
		public Kind kind()	{ return writer.kind(); }

		@Override
		public <T extends Visitor<? extends T>> Optional<ReadyWriter>
							accept(T visitor)
		{
			return Objects.requireNonNull(visitor, "visitor")
				.visit(this)
				.filter(DefaultVisitor.class::isInstance)
				.map(Support.<ReadyWriter, T>constant()
					.apply(this));
		}
	}

	/** A delegating kind of {@code Visitor}. */
	final class DefaultVisitor implements Visitor<DefaultVisitor>
	{
		private final Function<ReadyWriter,
					Optional<DefaultVisitor>> visitor;

		/**
		 * Constructs a new {@code DefaultVisitor} object.
		 *
		 * @param kind the kind of a service provider
		 */
		public DefaultVisitor(Kind kind)
		{
			Objects.requireNonNull(kind, "kind");
			visitor = Support.<DefaultVisitor>visitor()
				.apply(kind)
				.apply(this);
		}

		@Override
		public Optional<DefaultVisitor> visit(ReadyWriter writer)
		{
			return visitor.apply(writer);
		}
	}
}
