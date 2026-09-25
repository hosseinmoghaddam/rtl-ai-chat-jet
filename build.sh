#!/usr/bin/env bash
# Builds dist/persian-rtl-ai-chat-<version>.zip, installable via Settings | Plugins | ⚙ | Install Plugin from Disk.
# Compiles against the jars of a locally installed JetBrains IDE (no Gradle needed).
#   IDE_HOME=/path/to/IDE.app/Contents ./build.sh
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' src/main/resources/META-INF/plugin.xml)
NAME=persian-rtl-ai-chat

if [[ -z "${IDE_HOME:-}" ]]; then
  for c in "$HOME"/Applications/*.app/Contents /Applications/*.app/Contents; do
    if [[ -f "$c/lib/app.jar" && -f "$c/lib/intellij.libraries.compose.foundation.desktop.jar" ]]; then IDE_HOME=$c; break; fi
  done
fi
[[ -n "${IDE_HOME:-}" && -d "$IDE_HOME/lib" ]] || { echo "Set IDE_HOME to a JetBrains IDE (…/Contents on macOS)"; exit 1; }
echo "Compiling against: $IDE_HOME"

OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/classes" "$OUT/agent-classes" "$OUT/pkg/$NAME/lib" "$OUT/pkg/$NAME/agent" dist

javac --release 17 -nowarn -d "$OUT/agent-classes" $(find agent/src -name '*.java')
jar cfm "$OUT/pkg/$NAME/agent/rtl-ai-chat-agent.jar" agent/MANIFEST.MF -C "$OUT/agent-classes" .

javac --release 17 -nowarn -cp "$IDE_HOME/lib/*" -d "$OUT/classes" $(find src/main/java -name '*.java')
cp -R src/main/resources/. "$OUT/classes/"
jar cf "$OUT/pkg/$NAME/lib/$NAME.jar" -C "$OUT/classes" .

rm -f "dist/$NAME-$VERSION.zip"
(cd "$OUT/pkg" && zip -qr "../../dist/$NAME-$VERSION.zip" "$NAME")
echo "Built dist/$NAME-$VERSION.zip"
