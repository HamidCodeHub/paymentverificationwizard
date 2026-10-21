"""Benchmark: can CLIP (the wizard project's ONNX vision encoder) tell payment receipts from everything else?

Mirrors wizard's ClipImageEncoder: RGB -> 224, CLIP mean/std, centre crop + full-frame squash averaged,
L2-normalised pooler_output. Image-to-image only (no text encoder needed in Java).

Honest splits:
  receipts      leave-one-BANK-LAYOUT-out  (would a bank we have never seen still pass?)
  non-receipts  leave-one-CATEGORY-out     (would a kind of image we never showed it still be blocked?)
"""
import glob, io, json, os, sys, time
import numpy as np
import onnxruntime as ort
from PIL import Image, ImageFilter, ImageOps

HOME = os.path.expanduser("~/mnt")
MODEL = f"{HOME}/wizard/models/clip-vit-base-patch32-vision.onnx"
CACHE = f"{HOME}/paymentverificationwizard/bench/embeddings.npz"
MEAN = np.array([0.48145466, 0.4578275, 0.40821073], np.float32)
STD = np.array([0.26862954, 0.26130258, 0.27577711], np.float32)

# --- data: the service's references/ folder (receipts/<group>/*, non-receipts/<group>/*) ---------
REF = f"{HOME}/paymentverificationwizard/references"
def _scan(label_dir):
    out = []
    for p in sorted(glob.glob(f"{REF}/{label_dir}/**/*.*", recursive=True)):
        if p.lower().endswith((".jpg", ".jpeg", ".png")):
            rel = os.path.relpath(p, f"{REF}/{label_dir}")
            out.append((p, rel.split(os.sep)[0] if os.sep in rel else os.path.splitext(rel)[0]))
    return out
receipts = _scan("receipts")
negs = _scan("non-receipts")


# --- encoder (port of wizard ClipImageEncoder + ImagePreprocessor) ----------------------
sess = None


def tensor(im):
    a = (np.asarray(im, np.float32) / 255 - MEAN) / STD
    return a.transpose(2, 0, 1)[None]


def resize(im, w, h):
    while im.width // 2 >= w and im.height // 2 >= h:
        im = im.resize((im.width // 2, im.height // 2), Image.BILINEAR)
    return im.resize((w, h), Image.BILINEAR)


def embed(im):
    global sess
    if sess is None:
        o = ort.SessionOptions(); o.intra_op_num_threads = 2
        sess = ort.InferenceSession(MODEL, o)
    im = ImageOps.exif_transpose(im).convert("RGB")
    s = 224 / min(im.size)
    w, h = int(np.ceil(im.width * s)), int(np.ceil(im.height * s))
    r = resize(im, w, h)
    cc = r.crop(((w - 224) // 2, (h - 224) // 2, (w - 224) // 2 + 224, (h - 224) // 2 + 224))
    sq = resize(im, 224, 224)
    out_name = "image_embeds" if "image_embeds" in [o.name for o in sess.get_outputs()] else "pooler_output"
    vs = []
    for t in (cc, sq):
        v = sess.run([out_name], {"pixel_values": tensor(t)})[0][0]
        vs.append(v / np.linalg.norm(v))
    v = vs[0] + vs[1]
    return v / np.linalg.norm(v)


def augment(im):
    """Ways a real customer's receipt can differ from a clean screenshot."""
    w, h = im.size
    yield "top-60%", im.crop((0, 0, w, int(h * .6)))
    yield "bottom-60%", im.crop((0, int(h * .4), w, h))
    yield "rotated-90", im.rotate(90, expand=True)
    yield "blur+lowq", Image.open(io.BytesIO(_jpeg(im.filter(ImageFilter.GaussianBlur(2)), 35)))
    yield "small-240px", im.resize((240, int(240 * h / w)))


def _jpeg(im, q):
    b = io.BytesIO(); im.convert("RGB").save(b, "JPEG", quality=q); return b.getvalue()


# --- embed everything (cached) ----------------------------------------------------------
t0 = time.time()
items = [(p, g, 1, "orig") for p, g in receipts] + [(p, g, 0, "orig") for p, g in negs]
if os.path.exists(CACHE) and json.loads(str(np.load(CACHE)["meta"])) == [[os.path.basename(i[0]), i[3], i[1], i[2]] for i in items]:
    E = np.load(CACHE)["E"]; AUG = np.load(CACHE)["AUG"]; AUGMETA = json.loads(str(np.load(CACHE)["augmeta"]))
else:
    E = np.stack([embed(Image.open(p)) for p, *_ in items])
    AUG, AUGMETA = [], []
    for p, g in receipts:
        for name, a in augment(Image.open(p).convert("RGB")):
            AUG.append(embed(a)); AUGMETA.append([os.path.basename(p), g, name])
    AUG = np.stack(AUG)
    os.makedirs(os.path.dirname(CACHE), exist_ok=True)
    np.savez(CACHE, E=E, AUG=AUG, meta=json.dumps([[os.path.basename(i[0]), i[3], i[1], i[2]] for i in items]), augmeta=json.dumps(AUGMETA))
print(f"embedded {len(E)} images + {len(AUG)} augmented receipts in {time.time()-t0:.1f}s, dim={E.shape[1]}")

y = np.array([i[2] for i in items]); grp = np.array([i[1] for i in items])
names = [os.path.basename(i[0]) for i in items]


def margin(v, keep):
    """max cosine to a receipt reference minus max cosine to a non-receipt reference."""
    sims = E[keep] @ v
    yk = y[keep]
    return sims[yk == 1].max() - sims[yk == 0].max(), sims[yk == 1].max()


# leave-group-out margins for every original image
M = np.zeros(len(items)); TOP = np.zeros(len(items))
for i in range(len(items)):
    keep = grp != grp[i]
    M[i], TOP[i] = margin(E[i], keep)

# augmented receipts: hold out their whole bank group too
MA = np.array([margin(v, grp != g)[0] for v, (_, g, _) in zip(AUG, AUGMETA)])

print("\n--- leave-group-out margin (receipt-ref minus non-receipt-ref), threshold 0 ---")
for i in np.argsort(M):
    print(f"{'RECEIPT' if y[i] else 'other  '}  {M[i]:+.4f}  top-receipt-cos {TOP[i]:.3f}  {grp[i]:<22} {names[i]}")

print("\n--- augmented receipts (bank layout held out) ---")
for v, (n, g, a) in sorted(zip(MA, AUGMETA)):
    print(f"  {v:+.4f}  {a:<12} {g:<22} {n}")

print("\n--- threshold sweep ---")
for th in (-0.06, -0.04, -0.03, -0.02, -0.01, 0.0, 0.01, 0.02):
    fn = int(((M < th) & (y == 1)).sum()); fp = int(((M >= th) & (y == 0)).sum())
    fna = int((MA < th).sum())
    print(f"  th {th:+.2f}: receipts blocked {fn}/{(y==1).sum()}  augmented blocked {fna}/{len(MA)}  non-receipts let through {fp}/{(y==0).sum()}")
