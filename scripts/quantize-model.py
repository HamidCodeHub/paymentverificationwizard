"""Make the small int8 copy of the CLIP model used on the 1 GB Oracle micro machines.

    py -m pip install onnxruntime onnx
    py scripts\\quantize-model.py

Reads  models/clip-vit-base-patch32-vision.onnx       (350 MB, fp32)
Writes models/clip-vit-base-patch32-vision-int8.onnx  (~96 MB, int8 weights)

Dynamic int8 quantisation of the MatMul/Gemm weights. Measured against the fp32 model:
  - memory of the ONNX session 470 MB -> 185 MB, inference ~3x faster on CPU
  - receipt gate --verify: identical verdicts (16/16 receipts pass, 0 non-receipts pass)
  - style match --verify: identical leave-one-out predictions on the theme photos
Embeddings differ slightly (cosine 0.97-0.99 to the fp32 ones), so each service rebuilds its embedding cache
on first start with this model; nothing else changes.
"""
import os
import sys

from onnxruntime.quantization import QuantType, quantize_dynamic

HERE = os.path.dirname(os.path.abspath(__file__))
MODELS = os.path.join(HERE, "..", "models")
SRC = os.path.join(MODELS, "clip-vit-base-patch32-vision.onnx")
DST = os.path.join(MODELS, "clip-vit-base-patch32-vision-int8.onnx")

if not os.path.exists(SRC):
    sys.exit(f"Missing {SRC} - run scripts/download-models first.")

quantize_dynamic(SRC, DST, weight_type=QuantType.QInt8, op_types_to_quantize=["MatMul", "Gemm"])
print(f"Wrote {DST} ({os.path.getsize(DST) / 1e6:.0f} MB)")
