#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/.." && pwd)"
test_build="$(mktemp -d)"
trap 'rm -rf "$test_build"' EXIT
mapfile -t sources < <(find "$project_root/core/src/main/java" "$project_root/core/src/test/java" -name '*.java' -print)
if command -v javac >/dev/null 2>&1; then
  javac -encoding UTF-8 -d "$test_build" "${sources[@]}"
else
  java com.sun.tools.javac.Main -encoding UTF-8 -d "$test_build" "${sources[@]}"
fi
java -ea -cp "$test_build" lol.palmer.boardlens.core.AllTests
