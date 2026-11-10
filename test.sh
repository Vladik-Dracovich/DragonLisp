#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
java -jar build/dragonlisp.jar tests/foundations.dlisp
java -jar build/dragonlisp.jar tests/interop.dlisp
java -jar build/dragonlisp.jar tests/release-0.7.dlisp
# Check interpreter vs generated JVM class output.
./compile.sh tests/release-0.9.dlisp DragonParity
java -jar build/dragonlisp.jar tests/release-0.9.dlisp > build/interpreted.out
java -cp build/classes:build/compiled DragonParity > build/compiled.out
diff -u build/interpreted.out build/compiled.out
printf 'Compiler bootstrap parity: PASS\n'
