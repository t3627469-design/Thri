#!/bin/sh
# Offline harness: runs the real patched only-build against a simulated world.
# Usage: harness/build.sh <extracted-patched-jar-dir>   (then: java -cp harness/build/classes:<dir> Driver [ticks])
set -e
export JAVA_TOOL_OPTIONS=
HERE=$(cd "$(dirname "$0")" && pwd)
CHK=$1
rm -rf "$HERE/build/gen" "$HERE/build/classes"
mkdir -p "$HERE/build/gen" "$HERE/build/classes"
python3 "$HERE/gen_stubs.py" "$CHK" "$HERE/build/gen" >/dev/null
cp -r "$HERE/hand/." "$HERE/build/gen/"
find "$HERE/build/gen" -name '*.java' > "$HERE/build/srcs.txt"
javac -nowarn -d "$HERE/build/classes" -cp "$CHK" @"$HERE/build/srcs.txt" 2>&1 | grep -v "^Note" || true
