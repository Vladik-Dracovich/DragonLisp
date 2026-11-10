#!/bin/sh
set -eu
cd "$(dirname "$0")"
./build.sh
for pair in 'compiler-1.2 DragonCompiler12' 'compiler-1.2-macros DragonMacro12'; do
  set -- $pair
  java -jar build/dragonlisp.jar "tests/$1.dlisp" > "build/$1.interpreted"
  java -cp build/classes org.dragonlisp.Compiler --compile "tests/$1.dlisp" "$2"
  java -cp build/classes:build/compiled "$2" > "build/$1.compiled"
  diff -u "build/$1.interpreted" "build/$1.compiled"
done
printf 'DragonLisp 1.2 compiler parity: PASS\n'
