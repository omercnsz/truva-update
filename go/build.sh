#!/usr/bin/env bash
# Script to build gomobile .aar for xray-core and copy to Android project
set -e

# ensure gomobile is installed
if ! command -v gomobile &> /dev/null; then
    echo "gomobile not found, installing..."
    go install golang.org/x/mobile/cmd/gomobile@latest
    gomobile init
fi

# build aar — output goes to project root libs/ folder
OUTPUT="../libs/xray.aar"
mkdir -p ../libs

echo "Building xray.aar..."
gomobile bind -target=android -o "$OUTPUT" ./...

echo "Done. aar placed at $OUTPUT"
