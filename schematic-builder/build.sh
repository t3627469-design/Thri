#!/usr/bin/env bash
# Builds dist/theone-client-<version>.jar without Gradle/Loom.
#
# Sources are written against yarn names. They compile against small API stubs (tools/stubs),
# every stub member is checked against the real yarn 1.21.11 mappings, and the compiled classes
# are remapped to intermediary names (what Fabric runs in production) by tools/src/Remap.java.
# Any Minecraft reference that does not exist in 1.21.11 fails the build.
set -euo pipefail

cd "$(dirname "$0")"
VERSION="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' src/main/resources/fabric.mod.json)"
YARN_BRANCH="1.21.11"
ASM_VERSION="9.8"
CACHE="tools/.cache"
OUT="build"

mkdir -p "$CACHE" "$OUT" dist

if [ ! -d "$CACHE/yarn/mappings" ]; then
    echo "> fetching yarn $YARN_BRANCH mappings"
    git clone -q --depth 1 -b "$YARN_BRANCH" https://github.com/FabricMC/yarn.git "$CACHE/yarn"
fi
for a in asm asm-commons asm-tree; do
    if [ ! -f "$CACHE/$a-$ASM_VERSION.jar" ]; then
        echo "> fetching $a $ASM_VERSION"
        curl -sSfL --retry 5 --retry-delay 3 -o "$CACHE/$a-$ASM_VERSION.jar" "https://repo1.maven.org/maven2/org/ow2/asm/$a/$ASM_VERSION/$a-$ASM_VERSION.jar"
    fi
done
if [ ! -f "$CACHE/intermediary-$YARN_BRANCH.tiny" ]; then
    echo "> fetching intermediary $YARN_BRANCH"
    curl -sSfL --retry 5 --retry-delay 3 -o "$CACHE/intermediary-$YARN_BRANCH.tiny" "https://raw.githubusercontent.com/FabricMC/intermediary/master/mappings/$YARN_BRANCH.tiny"
fi
ASM_CP="$CACHE/asm-$ASM_VERSION.jar:$CACHE/asm-commons-$ASM_VERSION.jar:$CACHE/asm-tree-$ASM_VERSION.jar"

rm -rf "$OUT/test" "$OUT/stubs" "$OUT/tools" "$OUT/classes" "$OUT/remapped" "$OUT/jar"

echo "> compiling stubs"
javac -nowarn --release 21 -d "$OUT/stubs" $(find tools/stubs/src -name '*.java')

echo "> compiling remapper"
javac --release 21 -cp "$ASM_CP" -d "$OUT/tools" tools/src/Remap.java

echo "> checking stubs against yarn $YARN_BRANCH"
java -cp "$OUT/tools:$ASM_CP" Remap check "$CACHE/yarn/mappings" "$OUT/stubs"

echo "> testing schematic readers"
javac --release 21 -Xlint:all -Werror -d "$OUT/test" $(find src/main/java/dev/theone/schematic/format src/test/java -name '*.java')
java -cp "$OUT/test" dev.theone.schematic.format.SchematicTest

echo "> compiling addon"
javac --release 21 -Xlint:all,-processing,-serial -Werror -cp "$OUT/stubs" -d "$OUT/classes" $(find src/main/java -name '*.java')

echo "> remapping to intermediary"
java -cp "$OUT/tools:$ASM_CP" Remap remap "$CACHE/yarn/mappings" "$OUT/stubs" "$OUT/classes" "$OUT/remapped"

echo "> verifying against intermediary $YARN_BRANCH"
java -cp "$OUT/tools:$ASM_CP" Remap verify "$CACHE/yarn/mappings" "$OUT/stubs" "$CACHE/intermediary-$YARN_BRANCH.tiny" "$OUT/remapped"

echo "> packaging"
mkdir -p "$OUT/jar"
cp -r "$OUT/remapped/." "$OUT/jar/"
cp -r src/main/resources/. "$OUT/jar/"
# Index every bundled schematic so the addon can copy them into .minecraft/schematics.
BUNDLED="$OUT/jar/assets/theone-client/schematics"
mkdir -p "$BUNDLED"
(cd "$BUNDLED" && find . -maxdepth 1 -type f \( -name '*.litematic' -o -name '*.schem' -o -name '*.nbt' -o -name '*.schematic' \) -printf '%f\n' | sort > index.txt)
echo "bundled schematics: $(wc -l < "$BUNDLED/index.txt")"
JAR="dist/theone-client-$VERSION.jar"
rm -f "$JAR"
(cd "$OUT/jar" && jar --create --file "../../$JAR" --no-manifest .)
echo "built $JAR"
