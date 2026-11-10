#!/bin/sh
set -eu
cd "$(dirname "$0")"
./test-1.4.sh
java -jar build/dragonlisp.jar tests/compiler-1.5.dlisp > build/1.5.interpreted
./compile-asm.sh tests/compiler-1.5.dlisp DragonASM15
java -cp build/classes:build/compiled DragonASM15 > build/1.5.compiled
diff -u build/1.5.interpreted build/1.5.compiled
javap -classpath build/compiled -p DragonASM15 | grep 'dragonFunction' >/dev/null
printf 'DragonLisp 1.5 function/macro parity: PASS\n'
