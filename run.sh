#!/bin/bash
set -e

# Prefer macOS standard JDK 21
if [ -d "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home" ]; then
    JAVA_BIN="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/java"
    JAVAC_BIN="/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/bin/javac"
else
    JAVA_BIN="java"
    JAVAC_BIN="javac"
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p bin
"$JAVAC_BIN" -d bin $(find src/main/java -name "*.java")
"$JAVA_BIN" -cp bin schafkopf.SchafkopfApp "$@"
