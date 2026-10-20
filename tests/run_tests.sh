#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/../coreshift java"
exec ./tests/run_tests.sh
