#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/classes
javac --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -encoding UTF-8 -d build/classes src/main/java/org/dragonlisp/*.java
jar --create --file build/dragonlisp.jar --main-class org.dragonlisp.Main -C build/classes .
echo 'Built build/dragonlisp.jar'
