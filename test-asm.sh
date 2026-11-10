#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
for pair in 'compiler-1.2 DragonASM12' 'compiler-1.2-macros DragonASMMacros'; do
  set -- $pair
  java -jar build/dragonlisp.jar "tests/$1.dlisp" > "build/$1.asm.interpreted"
  java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -cp build/classes org.dragonlisp.ASMCompiler --compile "tests/$1.dlisp" "$2"
  java -cp build/classes:build/compiled "$2" > "build/$1.asm.compiled"
  diff -u "build/$1.asm.interpreted" "build/$1.asm.compiled"
done
printf 'Direct ASM backend parity: PASS\n'
