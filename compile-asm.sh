#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ "$#" -ne 2 ]; then echo 'Usage: ./compile-asm.sh input.dlisp ClassName' >&2; exit 2; fi
./build.sh
java --add-exports java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED -cp build/classes org.dragonlisp.ASMCompiler --compile "$1" "$2"
