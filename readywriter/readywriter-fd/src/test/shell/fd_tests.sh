#!/bin/sh -e
#
# Shell dependencies: ls, mkfifo, mktemp, rm (of coreutils); mvn.
#
# Maven dependencies (as LOCALLY-INSTALLED artifacts): julics-{core,naming},
# impedimenta, readywriter-service (with its parent POM, at least).
#
# To test refactored classes, do local installation of changed artifacts anew.
#
# If this script silently fails with resolvable artifacts, comment out
# the testWritingToDefaultDescriptors mvn invocation, and run the rest.

case "`pwd`" in
*readywriter-fd/src/test/shell)
	;;
*)	echo >&2 "Usage: ./fd_tests.sh"
	exit 2
	;;
esac

unset mask package pipe_01 pipe_02 pipe_03 pipe_04 tmpdir
test -x /proc/self/fd || exit 4
test ! -e /proc/self/fd/3 || { echo >&2 "Closing fd 3"; exec 3>&-; }
test ! -e /proc/self/fd/4 || { echo >&2 "Closing fd 4"; exec 4>&-; }
test ! -e /proc/self/fd/8 || { echo >&2 "Closing fd 8"; exec 8>&-; }
test ! -e /proc/self/fd/9 || { echo >&2 "Closing fd 9"; exec 9>&-; }

# Duplicate stdout and stderr:
exec 8>&1 9>&2

umask 0077	# (0777 & ~0700), retain file accessibility.
tmpdir=`mktemp --tmpdir="${TMPDIR:-/tmp}" --directory -- XXXXXXXXXXXXXXXX` || exit 8
trap 'test -d "${tmpdir}" && rm --verbose --recursive --force "${tmpdir}" || :
exec 1>&- 2>&- 3<&- 4<&-
exec 1>&8 2>&9
exec 8>&- 9>&-'				EXIT HUP INT QUIT TERM

pipe_01=`mktemp --tmpdir="${tmpdir}" --dry-run -- pipe_01_XXXXXXXX` || exit 16
pipe_02=`mktemp --tmpdir="${tmpdir}" --dry-run -- pipe_02_XXXXXXXX` || exit 16
pipe_03=`mktemp --tmpdir="${tmpdir}" --dry-run -- pipe_03_XXXXXXXX` || exit 16
pipe_04=`mktemp --tmpdir="${tmpdir}" --dry-run -- pipe_04_XXXXXXXX` || exit 16
mkfifo "${pipe_01}" "${pipe_02}" "${pipe_03}" "${pipe_04}"

# Re-purpose stdout and stderr (see pipe(7)):
eval exec "1<>${pipe_01}" "2<>${pipe_02}" "3<>${pipe_03}" "4<>${pipe_04}"

# Unlink all created files:
rm --verbose --recursive --force "${tmpdir}" 1>&8 2>&9

package=org.zzzyxwvut.readywriter.fd.internal
mvn -e -Dtest="${package}.FileDescriptorWriterProviderTests#testWritingToDefaultDescriptors" \
	--file ../../../pom.xml -P tester --quiet --log-file /dev/null surefire:test
echo >&9 "If we may pass, we will...
"

# Reset stdout and stderr:
exec 1>&8 2>&9
ls -lF /proc/self/fd

# The tests below fail, if run with the default surefire settings:
#	-Dsurefire.forkCount=1 -Dsurefire.useSystemClassLoader=true \
mvn -e -Dtest="${package}.FileDescriptorWriterProviderTests#testWritingToBespokeDescriptors,#testExchangingOfDescriptors" \
	--file ../../../pom.xml -P tester surefire:test

# When execked children close inherited file descriptors (see fcntl(3posix),
# os::fopen(char*, char*) in hotspot/share/runtime/os.cpp), no file channels
# should be bound to any requested file descriptors greater than 2.
#
# Corrupted victims, if any, could be identified from the output pointed by
# FileDescriptorWriterProviderTests#logFileName() and inspected as follows, e.g.:
#	(victim=/path/to/library.jar; size=`stat --printf=%s "${victim}"`; \
#	dd if="${victim}" bs=1 skip=$((${size} - 128)) | hd)
