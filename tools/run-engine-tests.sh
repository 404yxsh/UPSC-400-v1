#!/usr/bin/env bash
# Compiles the pure-Java scheduling engine and runs app/src/test/.../EngineTest.java against a tiny JUnit stand-in.
# This is how the engine tests were run before the first GitHub build (real JUnit 4 runs them in CI).
set -e
cd "$(dirname "$0")/.."
rm -rf /tmp/engine-out && mkdir -p /tmp/engine-out
if command -v javac >/dev/null 2>&1; then JAVAC="javac"; else JAVAC="java -m jdk.compiler/com.sun.tools.javac.Main"; fi
$JAVAC -d /tmp/engine-out $(find tools/junit-shim -name '*.java') $(find app/src/main/java -name '*.java' -path '*engine*') app/src/test/java/com/yash/upsc400/engine/EngineTest.java tools/Runner.java
java -cp /tmp/engine-out Runner
