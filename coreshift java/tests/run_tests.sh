#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/.."

# 1. Recompile latest source and package JAR (send all compiler output to stderr)
if command -v cmd.exe >/dev/null 2>&1; then
    cmd.exe /c mvn -q compile jar:jar -DskipTests 1>&2
elif command -v mvn >/dev/null 2>&1; then
    mvn -q compile jar:jar -DskipTests 1>&2
elif [ -x "./mvnw" ]; then
    ./mvnw -q compile jar:jar -DskipTests 1>&2
elif command -v mvn.cmd >/dev/null 2>&1; then
    mvn.cmd -q compile jar:jar -DskipTests 1>&2
else
    ./mvnw -q compile jar:jar -DskipTests 1>&2
fi

JAVA_CMD="java"
if ! command -v java >/dev/null 2>&1 && command -v java.exe >/dev/null 2>&1; then
    JAVA_CMD="java.exe"
fi

# Determine classpath separator (: on Linux, ; on Windows or when using java.exe)
CP_SEP=":"
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" || "$OSTYPE" == "win32" ]] || [[ "$OS" == "Windows_NT" ]] || [[ "$JAVA_CMD" == *".exe"* ]]; then
    CP_SEP=";"
fi

CP="target/classes${CP_SEP}target/app.jar"

# 2. Run TestRunner directly, printing pure JSON to stdout
exec "$JAVA_CMD" -cp "$CP" com.coreshift.TestRunner
