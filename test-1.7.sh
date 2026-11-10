#!/bin/sh
set -eu
cd "$(dirname "$0")"
./test-1.6.sh
java -jar build/dragonlisp.jar tests/compiler-1.7.dlisp > build/1.7.interpreted
./compile-asm.sh tests/compiler-1.7.dlisp DragonASM17
java -cp build/classes:build/compiled DragonASM17 > build/1.7.compiled
diff -u build/1.7.interpreted build/1.7.compiled
javap -classpath build/compiled -p DragonASM17 | grep dragonLambda >/dev/null
printf 'DragonLisp 1.7 variadic closure and method-handle parity: PASS\n'
