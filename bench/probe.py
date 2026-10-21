"""Linear probe on cached CLIP vectors (run clip_gate_bench.py first). Same honest splits:
held-out bank layout for receipts, held-out category for non-receipts."""
import glob, json, os
import numpy as np

HOME = os.path.expanduser("~/mnt")
Z = np.load(f"{HOME}/paymentverificationwizard/bench/embeddings.npz")
E, AUG = Z["E"], Z["AUG"]
meta = json.loads(str(Z["meta"])); augmeta = json.loads(str(Z["augmeta"]))
names = [m[0] for m in meta]
g = np.array([m[2] for m in meta])
y = np.array([m[3] for m in meta])
ag = np.array([a[1] for a in augmeta])


def fit(X, Y, lam=0.0005, it=6000, lr=0.5):  # the service defaults (gate.probe.*)
    w = np.zeros(X.shape[1]); b = 0.0
    pw = (Y == 0).sum() / max(1, (Y == 1).sum())  # balance classes
    sw = np.where(Y == 1, pw, 1.0)
    for _ in range(it):
        p = 1 / (1 + np.exp(-(X @ w + b)))
        gr = (p - Y) * sw
        w -= lr * (X.T @ gr / len(Y) + lam * w); b -= lr * gr.mean()
    return w, b


P = np.zeros(len(E)); PA = np.zeros(len(AUG))
for grp in sorted(set(g)):
    tr = g != grp
    Xtr = np.vstack([E[tr], AUG[np.isin(ag, list(set(g[tr])))]])
    Ytr = np.concatenate([y[tr], np.ones(np.isin(ag, list(set(g[tr]))).sum())])
    w, b = fit(Xtr, Ytr)
    te = g == grp
    P[te] = 1 / (1 + np.exp(-(E[te] @ w + b)))
    PA[ag == grp] = 1 / (1 + np.exp(-(AUG[ag == grp] @ w + b)))

for i in np.argsort(P):
    print(f"{'RECEIPT' if y[i] else 'other  '} p={P[i]:.3f}  {g[i]:<22} {names[i]}")
print("\naugmented receipts, worst 12:")
for i in np.argsort(PA)[:12]:
    print(f"  p={PA[i]:.3f} {augmeta[i][2]:<12} {augmeta[i][1]}")
print()
for th in (0.2, 0.3, 0.4, 0.5, 0.6):
    print(f"th {th}: receipts blocked {((P<th)&(y==1)).sum()}/16  augmented blocked {(PA<th).sum()}/80  non-receipts through {((P>=th)&(y==0)).sum()}/39")
json.dump({"names": names, "p": P.tolist(), "aug": [a + [float(p)] for a, p in zip(augmeta, PA)]},
          open(f"{HOME}/paymentverificationwizard/bench/probe_scores.json", "w"))
