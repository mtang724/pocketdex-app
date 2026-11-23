import sys, os, json, torch
from transformers import ViTForImageClassification
SP = sys.argv[1]; M = os.path.join(SP, "model"); O = os.path.join(SP, "onnx"); os.makedirs(O, exist_ok=True)
model = ViTForImageClassification.from_pretrained(M, use_safetensors=True).eval()
class Wrap(torch.nn.Module):
    def __init__(s, m): super().__init__(); s.m = m
    def forward(s, pixel_values): return s.m(pixel_values=pixel_values).logits.softmax(-1)
x = torch.randn(1, 3, 224, 224)
fp32 = os.path.join(O, "pokemon_vit_fp32.onnx")
try:
    torch.onnx.export(Wrap(model), (x,), fp32, input_names=["pixel_values"], output_names=["probs"], opset_version=17, dynamo=False)
except Exception as e:
    print("legacy export failed:", e); torch.onnx.export(Wrap(model), (x,), fp32, input_names=["pixel_values"], output_names=["probs"], opset_version=18, dynamo=True, external_data=False)
from onnxruntime.quantization import quantize_dynamic, QuantType
q = os.path.join(O, "pokemon_vit_int8.onnx")
# Only MatMul/Gemm: ConvInteger has no kernel in the onnxruntime-android build.
quantize_dynamic(fp32, q, weight_type=QuantType.QInt8, op_types_to_quantize=["MatMul", "Gemm"])
labels = [model.config.id2label[i] for i in range(len(model.config.id2label))]
json.dump(labels, open(os.path.join(O, "labels_en.json"), "w"))
for f in os.listdir(O): print(f, round(os.path.getsize(os.path.join(O, f)) / 1e6, 1), "MB")
