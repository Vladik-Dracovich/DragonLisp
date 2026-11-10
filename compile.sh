#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ "$#" -ne 2 ]; then echo 'Usage: ./compile.sh input.dlisp ClassName' >&2; exit 2; fi
./build.sh
java -cp build/classes org.dragonlisp.Compiler --compile "$1" "$2"
