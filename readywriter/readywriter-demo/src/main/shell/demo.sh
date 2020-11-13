#!/bin/sh -e
#
# The <0x23><0x7b>dotted.name<0x7d><0x23> placeholders are managed by
# maven-assembly-plugin (see its "delimiters" for assembly:single).
#
# Version: #{readywriter-demo.script.version}#

launcher='#{readywriter-demo.launcher.name}#'
prefix='#{readywriter-demo.modules.prefix}#'
core='#{readywriter-demo.modules.julics.core}#'
naming='#{readywriter-demo.modules.julics.naming}#'

case "$1" in
'' | -h | --help)
	echo >&2 "Usage: $0 Harkee [...]

Each argument is written \"as is\".

DEPENDENCIES:

The path to the following JPMS modules, either packaged or
exploded,
\t${core}
\t${naming}
\t${prefix}.service
\t${prefix}.demo
\t${prefix}.path
\t${prefix}.fd

Of the latter two, *.path and *.fd, one at least would do.
By default, all modules are looked up in the bundled lib
directory.

ENVIRONMENT:

Define any of the following environment variables if needed:

READYWRITER_MODULE_PATH (default: lib)
(Its value is used as the --module-path option argument for
a JVM.)

READYWRITER_DEMO_FD	(default: 1)
(Its value is used for a file descriptor allocation: [3-9].)

EXAMPLES:

[env] READYWRITER_DEMO_FD=6 READYWRITER_DEBUG=y \\
\t$0 Hail\ fellow,\ well\ met.
[env] READYWRITER_MODULE_PATH=\`find ~/.m2/repository/org/zzzyxwvut/{julics-{core,naming},readywriter-{demo,fd,path,service}} \\
-name \*.jar -printf %p:\` \\
\t$0 Harkee,\ I\ say\!"
	exit 2
	;;
esac

if test ! -x "`command -v findmnt`"
then
	echo >&2 "$0: \`findmnt' executable utility not found."
	exit 4
fi

case "`findmnt --noheadings --types proc --output TARGET`" in
/proc)	;;
*)	echo >&2 "$0: /proc mount point not found."
	exit 8
	;;
esac

unset fd reject
fd="${READYWRITER_DEMO_FD:-1}"
reject=1

case "${fd}" in
[3-9])	reject=0
	;;
esac

test "${reject}" -ne 0 || eval exec "${fd}>&1"
java --module-path ${READYWRITER_MODULE_PATH:-lib} --module ${launcher} "$@"
test "${reject}" -ne 0 || {
	if test -n "${READYWRITER_DEBUG}"
	then
		set +f				# Enable pathname expansion.
		printf >&2 '%s\n' '' Open\ File\ Descriptors: /proc/self/fd/*
	fi

	eval exec "${fd}>&-"
}
