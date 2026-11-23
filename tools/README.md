# tools

Scripts that generate the app's assets. 生成 App 资源的脚本。

| Script | Input | Output |
|---|---|---|
| `build_data.py <csv_dir> <out.json> [zh_dir]` | PokeAPI CSVs (`data/v2/csv` in github.com/PokeAPI/pokeapi); optional `zh_dir` = `data/pokemon/*.json` from github.com/42arch/pokemon-dataset-zh for missing Chinese entries | `app/src/main/assets/dex.json` |
| `export_onnx.py <work_dir>` | `<work_dir>/model/` = files from huggingface.co/skshmjn/Pokemon-classifier-gen9-1025 | `<work_dir>/onnx/pokemon_vit_int8.onnx` → copy to `app/src/main/assets/model/` |

`export_onnx.py` needs `torch transformers onnx onnxruntime onnxscript` (e.g. in a uv venv with Python 3.12).

Artwork is not generated here: the app downloads it from PokeAPI on first launch (see `ArtStore.kt`).
