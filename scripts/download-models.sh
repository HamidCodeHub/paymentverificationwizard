#!/usr/bin/env bash
# Puts the two model files this service needs into ./models/ :
#   models/clip-vit-base-patch32-vision.onnx   copied from ../wizard/models if present, else downloaded (~350 MB)
#   models/tessdata/fas.traineddata            Tesseract's Persian model, tessdata_best (~3 MB)
# and checks that Tesseract itself is installed.
#
#   ./scripts/download-models.sh
#
# Run from the project root.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODELS="$ROOT/models"
mkdir -p "$MODELS/tessdata"

size_mb() { echo $(( $(stat -c%s "$1" 2>/dev/null || stat -f%z "$1") / 1048576 )); }

CLIP="$MODELS/clip-vit-base-patch32-vision.onnx"
SIBLING="$(dirname "$ROOT")/wizard/models/clip-vit-base-patch32-vision.onnx"
if [[ -f "$CLIP" ]]; then
  echo "CLIP model already present."
elif [[ -f "$SIBLING" ]]; then
  echo "Copying the CLIP model from the style-match project ..."
  cp "$SIBLING" "$CLIP"
else
  echo "Downloading the CLIP vision encoder (~350 MB) ..."
  curl -fL --progress-bar -o "$CLIP" \
    "https://huggingface.co/Xenova/clip-vit-base-patch32/resolve/main/onnx/vision_model.onnx"
fi
(( $(size_mb "$CLIP") >= 20 )) || { rm -f "$CLIP"; echo "CLIP download looks truncated; retry." >&2; exit 1; }

FAS="$MODELS/tessdata/fas.traineddata"
if [[ -f "$FAS" ]]; then
  echo "fas.traineddata already present."
else
  echo "Downloading fas.traineddata (tessdata_best) ..."
  curl -fL --progress-bar -o "$FAS" "https://github.com/tesseract-ocr/tessdata_best/raw/main/fas.traineddata"
fi
(( $(stat -c%s "$FAS" 2>/dev/null || stat -f%z "$FAS") > 1000000 )) || { rm -f "$FAS"; echo "fas.traineddata looks truncated; retry." >&2; exit 1; }

if command -v tesseract >/dev/null; then
  echo "Tesseract found: $(tesseract --version 2>&1 | head -1)"
else
  echo "Tesseract is not installed:  sudo apt install tesseract-ocr   (macOS: brew install tesseract)" >&2
  exit 1
fi

echo
echo "Ready. Check accuracy on the references:  mvn spring-boot:run -Dspring-boot.run.arguments=--verify"
