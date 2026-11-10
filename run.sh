#!/bin/sh
set -eu
cd "$(dirname "$0")"
[ -f build/dragonlisp.jar ] || ./build.sh
exec java -jar build/dragonlisp.jar "$@"
