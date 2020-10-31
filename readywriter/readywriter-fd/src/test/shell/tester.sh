#!/bin/sh -e

case "`pwd`" in
*readywriter-fd/src/test/shell)
	;;
*)	echo >&2 "Usage: ./tester.sh"
	exit 2
	;;
esac

mvn -e --file ../../../pom.xml -P tester antrun:run@exec-shell-fd-tests \
	-Dant.home=${ANT_HOME:?} -Dmaven.antrun.skip=false
