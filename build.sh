#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/main/java/rr/*.java src/test/java/rr/*.java
echo "Compilado em ./out"
