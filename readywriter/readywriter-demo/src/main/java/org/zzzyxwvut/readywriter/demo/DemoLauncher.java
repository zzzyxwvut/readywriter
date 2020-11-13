package org.zzzyxwvut.readywriter.demo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.zzzyxwvut.readywriter.ReadyWriter.Kind;
import org.zzzyxwvut.readywriter.ReadyWriter.Visitor;
import org.zzzyxwvut.readywriter.ReadyWriter;
import org.zzzyxwvut.readywriter.service.FileDescriptorWriterVisitor;
import org.zzzyxwvut.readywriter.service.Lookup.DefaultVisitor;
import org.zzzyxwvut.readywriter.service.Lookup;

import org.zzzyxwvut.julics.annotation.Loggable;
import org.zzzyxwvut.julics.naming.LoggerRef;

/** The entry point class of this demonstration. */
@Loggable(retainsValues = false)
public class DemoLauncher
{
	private static String HELP_MESSAGE = String.format(
			"Usage: [READYWRITER_DEMO_FD=3] \\"
		+ "%n\tjava --module-path `find ~/.m2/repository/org/zzzyxwvut/"
		+ "{julics-{core,naming},readywriter-{demo,fd,path,service}} \\"
		+ "%n\t\t-name \\*.jar -printf %%p:` \\"
		+ "%n\t\t--module org.zzzyxwvut.readywriter.demo"
		+ " Harkee,\\ I\\ say\\!"
		+ "%n%nSend a message string to the stdout stream (or"
		+ " the READYWRITER_DEMO_FD"
		+ "%ninherited file descriptor, see _sine qua non_ below) and"
		+ " to a new"
		+ "%ntemplet-named file in the [java.io.tmpdir]/jrw_messages"
		+ " directory."
		+ "%n%n(The value of the READYWRITER_DEMO_FD environment"
		+ " variable shall only"
		+ "%nbe used iff:"
		+ "%n(*) this JVM instance is run by a Linux kernel"
		+ "%n(*) the proc virtual file system is mounted at /proc"
		+ "%n(*) the value is in a [3, SOFT_LIMIT) range, where"
		+ " SOFT_LIMIT can be"
		+ "%nretrieved as follows:"
		+ "%n\tJVM_PID=12345\t# Consider using `jps -l`."
		+ "%n\tprlimit --output SOFT --noheadings --nofile --pid ${JVM_PID}"
		+ "%n%n(*) the file descriptor number is valid; viz, it refers"
		+ " to an open"
		+ "%nfile, managed by this process, that is writable and not"
		+ " a directory.)");
	private static final int STANDARD_OUT = 1;
	private static final int BESPOKE_OUT = ("linux".equalsIgnoreCase(
						System.getProperty("os.name"))
				&& Files.isExecutable(Path.of("/proc/self")))
		? Optional.ofNullable(System.getenv("READYWRITER_DEMO_FD"))
			.flatMap(value -> {
				try {
					return Optional.of(Integer.valueOf(
								value));
				} catch (final NumberFormatException ignored) {
					return Optional.empty();
				}
			})
			.filter(fd -> Files.exists(Path.of("/proc/self/fd",
						Integer.toString(fd))))
			.orElse(STANDARD_OUT)
		: STANDARD_OUT;

	static {
		new LoggerRef(DemoLauncher.class);
	}	/* Load logging.properties. */

	private DemoLauncher() { /* No instantiation. */ }

	private static <T extends Visitor<? extends T>> Function<List<T>,
						Consumer<String>> dispatcher()
	{
		return visitors -> message -> visitors
			.stream()
			.flatMap(Function.<Stream<ReadyWriter>>identity()
				.<Optional<ReadyWriter>>compose(
							Optional::stream)
				.compose(Lookup::readyWriter))
			.forEach(Lookup.messager()
					.apply(message));
	}

	private static List<String> parseArgs(List<String> argsView)
	{
		final int optLastPos = argsView.indexOf("--");
		final List<String> optionView, operandView;

		if (optLastPos > -1) {
			final List<String> copy = new ArrayList<>(argsView);
			optionView = copy.subList(0, optLastPos);
			operandView = copy.subList(optLastPos + 1,
							argsView.size());
		} else {
			optionView = argsView;
			operandView = argsView;
		}

		return (operandView.isEmpty() || optionView.contains("-h")
					|| optionView.contains("--help"))
			? List.of()
			: List.copyOf(operandView);
	}

	/**
	 * Runs a demonstration.
	 *
	 * @param args an array of command line arguments, if any
	 */
	public static void main(String[] args)
	{
		final List<String> parsedView = parseArgs(List.of(args));

		if (parsedView.isEmpty()) {
			System.err.println(HELP_MESSAGE);
			return;
		}

		DemoLauncher.<Visitor<?>>dispatcher()
			.apply(List.of((BESPOKE_OUT != STANDARD_OUT)
					? new FileDescriptorWriterVisitor(
								BESPOKE_OUT)
					: new DefaultVisitor(
						Kind.FILE_DESCRIPTOR),
				new DefaultVisitor(Kind.PATH)))
			.accept(parsedView.stream()
				.collect(Collectors.joining()));
	}
}
