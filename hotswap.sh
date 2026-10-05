#!/bin/zsh
# Dev only: recompile and push changed method bodies into a running client started with
#   JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 ./gradlew run
# Structural changes (new methods/fields) still need a relaunch.
set -e
cd "${0:a:h}"
export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}
./gradlew -q compileJava
cmds=""
for f in build/classes/java/main/**/*.class; do
	cls=${${f#build/classes/java/main/}%.class}
	cmds+="redefine ${cls//\//.} $f\n"
done
print -n "${cmds}exit\n" | "$JAVA_HOME/bin/jdb" -attach 5005 2>&1 | grep -viE "^(Initializing|Set uncaught|Set deferred|> *$)" || true
