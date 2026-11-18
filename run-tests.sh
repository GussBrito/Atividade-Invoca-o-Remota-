#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
./build.sh
java -Dstdout.encoding=UTF-8 -cp out rr.TestRunner
