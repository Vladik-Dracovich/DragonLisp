#!/bin/sh
set -eu
cd "$(dirname "$0")"
./test.sh
./test-asm.sh
java -jar build/dragonlisp.jar tests/compiler-1.4.dlisp > build/compiler-1.4.interpreted
./compile-asm.sh tests/compiler-1.4.dlisp DragonASM14
java -cp build/classes:build/compiled DragonASM14 > build/compiler-1.4.compiled
diff -u build/compiler-1.4.interpreted build/compiler-1.4.compiled
printf 'DragonLisp 1.4 compiler parity: PASS\n'
