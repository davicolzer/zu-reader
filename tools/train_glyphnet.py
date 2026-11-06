"""Treina a rede de glifos do Zu Reader (MLP 256 → 96 → 25) com numpy.

Uso: python3 tools/train_glyphnet.py <pasta com sintetico.bin e real.bin> <saida glyphnet.bin>

Os dados vêm de GlyphNetDataGenerator (core/vision, testes). Cada amostra: 256 floats (16×16,
tinta = 1) e um int com a classe (0–23 = glifo de ZuAlphabet, 24 = não é glifo), big-endian.
A saída: int magic 'ZUNN', ints in/hidden/out, depois
W1 (hidden×in), b1, W2 (out×hidden), b2 em float32 big-endian.
"""
import struct
import sys

import numpy as np

IN, HIDDEN, OUT = 256, 96, 25


def load(path):
    raw = np.fromfile(path, dtype=">u4")
    rec = raw.reshape(-1, IN + 1)
    x = rec[:, :IN].copy().view(">f4").astype(np.float32)
    y = rec[:, IN].astype(np.int64)
    return x, y


def main():
    folder, out = sys.argv[1], sys.argv[2]
    x, y = load(f"{folder}/sintetico.bin")
    xr, yr = load(f"{folder}/real.bin")
    rng = np.random.default_rng(7)
    # as fotos reais pesam mais (são poucas, mas são o caso que importa)
    x = np.concatenate([x] + [xr] * 20)
    y = np.concatenate([y] + [yr] * 20)
    perm = rng.permutation(len(x))
    x, y = x[perm], y[perm]
    n_val = len(x) // 20
    xv, yv, xt, yt = x[:n_val], y[:n_val], x[n_val:], y[n_val:]
    print(f"treino {len(xt)}  validação {len(xv)}  classes {np.bincount(y, minlength=OUT)}")

    w1 = rng.normal(0, np.sqrt(2 / IN), (HIDDEN, IN)).astype(np.float32)
    b1 = np.zeros(HIDDEN, np.float32)
    w2 = rng.normal(0, np.sqrt(2 / HIDDEN), (OUT, HIDDEN)).astype(np.float32)
    b2 = np.zeros(OUT, np.float32)
    params = [w1, b1, w2, b2]
    m = [np.zeros_like(p) for p in params]
    v = [np.zeros_like(p) for p in params]
    lr, beta1, beta2, eps, decay = 2e-3, 0.9, 0.999, 1e-8, 1e-4
    step = 0
    batch = 256

    def forward(xb):
        h = np.maximum(xb @ w1.T + b1, 0)
        z = h @ w2.T + b2
        z -= z.max(axis=1, keepdims=True)
        p = np.exp(z)
        p /= p.sum(axis=1, keepdims=True)
        return h, p

    for epoch in range(25):
        perm = rng.permutation(len(xt))
        for i in range(0, len(xt), batch):
            idx = perm[i:i + batch]
            xb, yb = xt[idx], yt[idx]
            # aumento: deslocamento de 1 pixel e brilho
            if rng.random() < 0.5:
                xb = np.roll(xb.reshape(-1, 16, 16), rng.integers(-1, 2), axis=rng.integers(1, 3)).reshape(-1, IN)
            xb = np.clip(xb * rng.uniform(0.8, 1.1) + rng.normal(0, 0.03, xb.shape), 0, 1).astype(np.float32)
            h, p = forward(xb)
            g = p.copy()
            g[np.arange(len(yb)), yb] -= 1
            g /= len(yb)
            gw2 = g.T @ h + decay * w2
            gb2 = g.sum(0)
            gh = (g @ w2) * (h > 0)
            gw1 = gh.T @ xb + decay * w1
            gb1 = gh.sum(0)
            step += 1
            for k, (pa, gr) in enumerate(zip(params, [gw1, gb1, gw2, gb2])):
                m[k] = beta1 * m[k] + (1 - beta1) * gr
                v[k] = beta2 * v[k] + (1 - beta2) * gr * gr
                mh = m[k] / (1 - beta1 ** step)
                vh = v[k] / (1 - beta2 ** step)
                pa -= (lr * mh / (np.sqrt(vh) + eps)).astype(np.float32)
        if epoch in (14, 20):
            lr /= 3
        _, pv = forward(xv)
        _, pr = forward(xr)
        print(f"época {epoch + 1:2d}: validação {np.mean(pv.argmax(1) == yv):.4f}  fotos {np.mean(pr.argmax(1) == yr):.4f}")

    with open(out, "wb") as f:
        f.write(b"ZUNN")
        f.write(struct.pack(">iii", IN, HIDDEN, OUT))
        for pa in params:
            f.write(pa.astype(">f4").tobytes())
    print(f"pesos em {out}")


if __name__ == "__main__":
    main()
