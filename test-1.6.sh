#!/bin/sh
set -eu
cd "$(dirname "$0")"
./test-1.5.sh
java -jar build/dragonlisp.jar tests/compiler-1.6.dlisp > build/1.6.interpreted
./compile-asm.sh tests/compiler-1.6.dlisp DragonASM16
java -cp build/classes:build/compiled DragonASM16 > build/1.6.compiled
diff -u build/1.6.interpreted build/1.6.compiled
javap -classpath build/compiled -p DragonASM16 | grep 'dragonLambda' >/dev/null
printf 'DragonLisp 1.6 lexical closure parity: PASS\n'
