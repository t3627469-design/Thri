#!/bin/sh
# Rebuild: ./build.sh <original-1.2.9-jar>   (needs JDK 21, asm-9.7.jar + asm-tree-9.7.jar from Maven Central in ./tools)
set -e
export JAVA_TOOL_OPTIONS=
CP=tools/asm-9.7.jar:tools/asm-tree-9.7.jar
rm -rf build && mkdir -p build/stubs build/guard build/patch build/work
javac -nowarn -d build/stubs $(find stubs -name '*.java')
(cd build/work && unzip -q "../../$1")
javac -nowarn -source 21 -target 21 -cp build/stubs:build/work -d build/guard src/dev/rex/farmbuilder/modules/Guard.java
javac -nowarn -cp $CP -d build/patch Patch.java
java -cp build/patch:$CP Patch build/work
cp build/guard/dev/rex/farmbuilder/modules/Guard*.class build/work/dev/rex/farmbuilder/modules/
echo "now zip build/work back into a jar (see git history for the exact repack script)"
