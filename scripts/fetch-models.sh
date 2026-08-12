#!/usr/bin/env bash
# Downloads the SenseVoice + Silero VAD models into app/src/main/assets/models/
# so a release build can bundle them inside the APK (no first-run download).
# URLs mirror ModelDownloader.kt. Idempotent: skips files already present with
# the expected size; verifies byte counts after each download.
set -euo pipefail

BASE="https://hf-mirror.com/csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17/resolve/main"
VAD_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEST_DIR="$SCRIPT_DIR/../app/src/main/assets/models"

# relative path -> expected bytes (must match ModelDownloader.kt constants)
SENSE_MODEL_BYTES=239233841
SENSE_TOKENS_BYTES=315894
VAD_BYTES=643854 # actual size of the asr-models release asset (was mis-stated as 2.1MB)

fetch() {
  local rel="$1" url="$2" expect="$3"
  local out="$DEST_DIR/$rel"
  if [[ -f "$out" ]]; then
    local got
    got="$(stat -c%s "$out")"
    if [[ "$got" == "$expect" ]]; then
      echo "OK (skip): $rel ($got bytes)"
      return 0
    fi
    echo "WARN: $rel size $got != expected $expect, re-downloading"
    rm -f "$out"
  fi
  mkdir -p "$(dirname "$out")"
  echo "Downloading $rel ..."
  curl -fL --retry 3 -o "$out" "$url"
  got="$(stat -c%s "$out")"
  if [[ "$got" != "$expect" ]]; then
    echo "ERROR: $rel size $got != expected $expect" >&2
    rm -f "$out"
    exit 1
  fi
  echo "OK: $rel ($got bytes)"
}

mkdir -p "$DEST_DIR"
fetch "sensevoice/model.int8.onnx" "$BASE/model.int8.onnx" "$SENSE_MODEL_BYTES"
fetch "sensevoice/tokens.txt"      "$BASE/tokens.txt"      "$SENSE_TOKENS_BYTES"
fetch "silero_vad.onnx"            "$VAD_URL"               "$VAD_BYTES"
echo "All models ready under $DEST_DIR"
