package org.zzzyxwvut.readywriter.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.zzzyxwvut.readywriter.demo.DemoLauncherTests.BespokeLoggingConfiguration;

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.logging.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import org.zzzyxwvut.julics.annotation.Loggable;
import org.zzzyxwvut.julics.core.DefaultLoggingConfiguration;
import org.zzzyxwvut.julics.naming.LoggerRef;

import org.zzzyxwvut.readywriter.ReadyWriter.Kind;
import org.zzzyxwvut.readywriter.ReadyWriter.Visitor;
import org.zzzyxwvut.readywriter.ReadyWriter;
import org.zzzyxwvut.readywriter.service.FileDescriptorWriterVisitor;
import org.zzzyxwvut.readywriter.service.Lookup.DefaultVisitor;
import org.zzzyxwvut.readywriter.service.Lookup;
import org.zzzyxwvut.readywriter.service.PathWriterVisitor;

@Loggable(retainsValues = false, configClass = BespokeLoggingConfiguration.class)
public class DemoLauncherTests
{
	private static final String FD_MODULE_NAME =
					"org.zzzyxwvut.readywriter.fd";
	private static final String PATH_MODULE_NAME =
					"org.zzzyxwvut.readywriter.path";

	private static ModuleLayer moduleLayer = ModuleLayer.boot();

	private List<ReadyWriter> providers;

	static {
		new LoggerRef(DemoLauncherTests.class);
	}	/* Load logging.properties. */

	@BeforeAll
	public static void setUpClass()
	{
		final List<Path> paths = Stream.of(
				new SimpleImmutableEntry<String,
						Optional<Supplier<Path>>>(
					FD_MODULE_NAME,
					Optional.of(() -> Path.of("..",
							"readywriter-fd",
							"target",
							"classes"))),
				new SimpleImmutableEntry<String,
						Optional<Supplier<Path>>>(
					PATH_MODULE_NAME,
					Optional.of(() -> Path.of("..",
							"readywriter-path",
							"target",
							"classes"))))
			.flatMap(entry -> ModuleLayer.boot()
				.findModule(entry.getKey())
				.map(value -> Optional.<Supplier<Path>>empty())
				.orElseGet(entryValuer()
					.apply(entry))
				.stream()
				.map(Supplier::get))
			.collect(Collectors.toUnmodifiableList());

		if (paths.isEmpty())
			return;

		final ModuleLayer parentLayer = ModuleLayer.boot();
		final Configuration configuration = parentLayer
			.configuration()
			.resolveAndBind(ModuleFinder.of(paths.toArray(
						new Path[paths.size()])),
				ModuleFinder.of(),
				Set.of(FD_MODULE_NAME, PATH_MODULE_NAME));
		Function.<Void>identity()
			.compose((ModuleLayer layer) -> {
				LoggerRef.logger()
					.apply(new LoggerRef(
						DemoLauncherTests.class))
					.apply(Level.INFO)
					.accept(messager()
						.apply(layer));
				return null;
			})
			.apply(moduleLayer = parentLayer
					.defineModulesWithOneLoader(
				configuration,
				ClassLoader.getSystemClassLoader()));
	} /* Cf. java.instrument/java.lang.instrument.Instrumentation#redefineModule */

	private static Function<Entry<String, Optional<Supplier<Path>>>,
				Supplier<Optional<Supplier<Path>>>> entryValuer()
	{
		return entry -> entry::getValue;
	}

	private static Function<ModuleLayer, Supplier<String>> messager()
	{
		return layer -> () -> layer.modules()
			.stream()
			.map(module -> {
				final String name = module.toString();
				return name.substring(name.indexOf(' ') + 1);
			}).collect(Collectors.joining(", ", "Modules added ",
									""));
	}

	private static <T> Function<Function<T, Optional<ReadyWriter>>,
				Function<T, Stream<ReadyWriter>>> writer()
	{
		return f -> Function.<Stream<ReadyWriter>>identity()
			.<Optional<ReadyWriter>>compose(Optional::stream)
			.compose(f);
	}

	private static Function<ModuleLayer,
				Function<String,
				Function<Visitor<?>, Optional<ReadyWriter>>>>
								finder()
	{
		return layer -> providerName -> visitor ->
			Lookup.readyWriter(providerName, layer, visitor);
	}

	private static Function<Function<String,
					Function<Visitor<?>,
						Optional<ReadyWriter>>>,
				Function<List<Visitor<?>>,
				Function<String, Optional<ReadyWriter>>>>
								seeker()
	{
		return finder -> visitors -> providerName -> visitors
			.stream()
			.flatMap(DemoLauncherTests.<Visitor<?>>writer()
				.apply(finder
					.apply(providerName)))
			.findAny();
	}

	@BeforeEach
	public void setUp()
	{
		providers = Function.<List<ReadyWriter>>identity()
			.compose((ModuleLayer layer) -> Lookup
				.names(layer)
				.stream()
				.flatMap(DemoLauncherTests.<String>writer()
					.apply(seeker()
						.apply(finder()
							.apply(layer))
						.apply(List.of(
							new DefaultVisitor(
								Kind.PATH),
							new DefaultVisitor(
								Kind.FILE_DESCRIPTOR)))))
				.collect(Collectors.toUnmodifiableList()))
			.apply(moduleLayer);
	}

	private static Function<ReadyWriter,
				Function<PathWriterVisitor, Runnable>>
								pathRunner()
	{
		return writer -> visitor -> () -> {
			final PathWriterVisitor pathVisitor =
							visitor.visit(writer)
				.orElseThrow(IllegalStateException::new);
			assertIterableEquals(
				List.of(visitor.path(), visitor.appendable(),
					visitor.charset(), visitor.byteOrder()),
				List.of(pathVisitor.path(),
						pathVisitor.appendable(),
						pathVisitor.charset(),
						pathVisitor.byteOrder()));
		};
	}

	@Test
	public void testEscapingOfThisWithPathKind()
	{
		final ReadyWriter writer = providers
			.stream()
			.filter(provider -> provider.kind() == Kind.PATH)
			.findAny()
			.orElseThrow(IllegalStateException::new);
		new Thread(pathRunner()
				.apply(writer)
				.apply(new PathWriterVisitor(
						Path.of("foo.bar"), true)))
			.start();
	}

	private static Function<ReadyWriter,
				Function<FileDescriptorWriterVisitor, Runnable>>
							fileDescriptorRunner()
	{
		return writer -> visitor -> () -> {
			final FileDescriptorWriterVisitor fdVisitor =
							visitor.visit(writer)
				.orElseThrow(IllegalStateException::new);
			assertIterableEquals(
				List.of(visitor.fdNumber(), visitor.fileName(),
					visitor.charset(), visitor.byteOrder()),
				List.of(fdVisitor.fdNumber(),
						fdVisitor.fileName(),
						fdVisitor.charset(),
						fdVisitor.byteOrder()));
		};
	}

	@Test
	public void testEscapingOfThisWithFileDescriptorKind()
	{
		final ReadyWriter writer = providers
			.stream()
			.filter(provider -> provider.kind()
						== Kind.FILE_DESCRIPTOR)
			.findAny()
			.orElseThrow(IllegalStateException::new);
		new Thread(fileDescriptorRunner()
				.apply(writer)
				.apply(new FileDescriptorWriterVisitor(1)))
			.start();
	}

	@Test
	public void testProviderAvailability()
	{
		final List<String> expected = Arrays.asList(FD_MODULE_NAME,
							PATH_MODULE_NAME);
		final List<String> obtained = providers.stream()
			.map(writer -> writer.getClass().getModule().getName())
			.collect(Collectors.toCollection(ArrayList::new));
		obtained.sort(null);
		assertLinesMatch(expected, obtained);
	}

	private static Function<Supplier<Visitor<?>>,
				Function<ModuleLayer,
				Supplier<Optional<ReadyWriter>>>> supplier()
	{
		return visitor -> layer -> () ->
			Lookup.readyWriter(layer, visitor.get());
	}

	@ParameterizedTest
	@EnumSource(names = { "FILE_DESCRIPTOR", "PATH" })
	public void testOtherProviderUnavailability(Kind expectedKind)
	{
		final Optional<ReadyWriter> otherWriter =
				Lookup.readyWriter(moduleLayer,
					new DefaultVisitor(Kind.OTHER));
		final Supplier<Optional<ReadyWriter>> fdwSupplier =
						DemoLauncherTests.supplier()
			.apply(() -> new DefaultVisitor(Kind.FILE_DESCRIPTOR))
			.apply(moduleLayer);
		final Supplier<Optional<ReadyWriter>> pwSupplier =
						DemoLauncherTests.supplier()
			.apply(() -> new DefaultVisitor(Kind.PATH))
			.apply(moduleLayer);
		final Kind obtainedKind = ((expectedKind
						== Kind.FILE_DESCRIPTOR)
				? otherWriter
					.or(fdwSupplier)
					.or(pwSupplier)
				: otherWriter
					.or(pwSupplier)
					.or(fdwSupplier))
			.map(ReadyWriter::kind)
			.orElse(Kind.OTHER);
		assertEquals(expectedKind, obtainedKind);
	}

	private static Function<Visitor<?>,
				Function<ReadyWriter, Optional<ReadyWriter>>>
								acceptor()
	{
		return visitor -> writer -> writer.accept(visitor);
	}

	@ParameterizedTest
	@ValueSource(strings = { FD_MODULE_NAME, PATH_MODULE_NAME })
	public void testProviderConfigurability(String moduleName)
	{
		final Optional<ReadyWriter> readyWriter = providers
			.stream()
			.flatMap(DemoLauncherTests.<ReadyWriter>writer()
				.apply(acceptor()
					.apply((FD_MODULE_NAME.equals(moduleName))
						? new FileDescriptorWriterVisitor(
							2,
							Pattern.compile(
								"(?m)^[^.]+$"),
							StandardCharsets.UTF_8,
							ByteOrder.BIG_ENDIAN)
						: new PathWriterVisitor(
							Path.of("foo.bar"),
							true,
							StandardCharsets.UTF_8,
							ByteOrder.BIG_ENDIAN))))
			.findAny();
		assertTrue(readyWriter.isPresent());
	}

	static final class BespokeLoggingConfiguration extends
						DefaultLoggingConfiguration
	{
		BespokeLoggingConfiguration()
		{
			super(new Abbreviator() {
				@Override
				public String reduce(String name)
				{
					return "<...>";
				}
			});
		}
	}
}
