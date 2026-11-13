"""Exporte les modèles PyTorch en ONNX pour le téléphone (exécuté en CI).

Usage : python export.py <dossier_sortie> demucs|beat
- demucs : htdemucs (réseau seul, sans STFT) via le fork sevagh/demucs.onnx
- beat   : Beat This! small0 et final0 (entrée : spectrogramme (1, 1500, 128))

Les poids sont ensuite stockés en float16 (fp16_storage) : fichier 2× plus
petit, calcul inchangé en float32 (ONNX Runtime convertit au chargement).
"""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import onnx
from onnx import TensorProto, helper, numpy_helper


def fp16_storage(src: Path, dst: Path, min_elems: int = 4096) -> None:
    """Remplace les grands poids float32 par float16 + Cast vers float32."""
    m = onnx.load(str(src))
    g = m.graph
    input_names = {i.name for i in g.input}
    new_inits = []
    casts = []
    for init in g.initializer:
        if init.data_type == TensorProto.FLOAT and np.prod(init.dims) >= min_elems:
            arr = numpy_helper.to_array(init).astype(np.float16)
            h = numpy_helper.from_array(arr, init.name + "__fp16")
            new_inits.append(h)
            casts.append(helper.make_node("Cast", [h.name], [init.name], to=TensorProto.FLOAT, name=init.name + "__cast"))
        else:
            new_inits.append(init)
    del g.initializer[:]
    g.initializer.extend(new_inits)
    # Les poids convertis ne doivent plus figurer parmi les entrées du graphe.
    cast_outs = {c.output[0] for c in casts}
    keep = [i for i in g.input if i.name not in cast_outs or i.name not in input_names]
    del g.input[:]
    g.input.extend(keep)
    nodes = list(g.node)
    del g.node[:]
    g.node.extend(casts + nodes)
    onnx.checker.check_model(m)
    onnx.save(m, str(dst))


def set_meta(path: Path, meta: dict) -> None:
    m = onnx.load(str(path))
    for k, v in meta.items():
        e = m.metadata_props.add()
        e.key, e.value = k, str(v)
    onnx.save(m, str(path))


def export_demucs(out: Path) -> None:
    import torch
    from torch.nn import functional as F
    from demucs.htdemucs import HTDemucs, standalone_magnitude, standalone_spec
    from demucs.pretrained import get_model

    model = get_model("htdemucs")
    core = model if isinstance(model, HTDemucs) else model.models[0]
    core.eval()
    seg = int(core.segment * core.samplerate)
    wav = torch.randn(1, 2, seg)
    mag = standalone_magnitude(standalone_spec(F.pad(wav, (0, seg - wav.shape[-1]))))
    raw = out / "htdemucs.fp32.onnx"
    torch.onnx.export(
        core,
        (wav, mag),
        str(raw),
        export_params=True,
        opset_version=17,
        do_constant_folding=True,
        input_names=["mix", "spec"],
        output_names=["out_spec", "out_wave"],
        dynamo=False,
    )
    set_meta(raw, {"segment": seg, "sources": ",".join(core.sources), "samplerate": core.samplerate})
    fp16_storage(raw, out / "htdemucs.onnx")
    print("demucs", core.sources, seg)


def export_beat(out: Path) -> None:
    import torch
    from beat_this.inference import load_model

    class Wrap(torch.nn.Module):
        def __init__(self, m):
            super().__init__()
            self.m = m

        def forward(self, x):
            o = self.m(x)
            return o["beat"], o["downbeat"]

    for name in ("small0", "final0"):
        m = load_model(name, "cpu").eval()
        raw = out / f"beat_this_{name}.fp32.onnx"
        with torch.no_grad():
            torch.onnx.export(
                Wrap(m),
                (torch.randn(1, 1500, 128),),
                str(raw),
                export_params=True,
                opset_version=17,
                do_constant_folding=True,
                input_names=["spect"],
                output_names=["beat", "downbeat"],
                dynamo=False,
            )
        set_meta(raw, {"fps": 50, "chunk": 1500, "border": 6, "n_mels": 128})
        fp16_storage(raw, out / f"beat_this_{name}.onnx")
        print("beat", name)


if __name__ == "__main__":
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    what = sys.argv[2]
    if what == "demucs":
        export_demucs(out)
    elif what == "beat":
        export_beat(out)
    elif what == "int8":
        # Quantification dynamique des produits matriciels (transformeurs) : poids int8.
        from onnxruntime.quantization import QuantType, quantize_dynamic

        src = Path(sys.argv[3])
        quantize_dynamic(str(src), str(out / sys.argv[4]), op_types_to_quantize=["MatMul", "Gemm"], weight_type=QuantType.QInt8)
        print("int8", src.name, (out / sys.argv[4]).stat().st_size)
    elif what == "fp16":
        fp16_storage(Path(sys.argv[3]), out / Path(sys.argv[3]).name)
    else:
        raise SystemExit("demucs|beat|fp16")
