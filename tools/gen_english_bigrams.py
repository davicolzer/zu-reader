"""Gera core/zu/src/main/kotlin/.../EnglishBigrams.kt a partir de textos em inglês.

Uso: python3 tools/gen_english_bigrams.py <pasta com .txt> <saida.kt>

Conta pares de letras (A-Z) e fronteiras de palavra ('_'), aplica suavização de
Laplace e grava log-probabilidades condicionais P(b | a) em milésimos de nat.
Usado com o corpus Gutenberg do NLTK (livros em domínio público).
"""
import math
import os
import sys

SYMBOLS = "_ABCDEFGHIJKLMNOPQRSTUVWXYZ"
N = len(SYMBOLS)


def main(src, out):
    counts = [[0] * N for _ in range(N)]
    files = sorted(f for f in os.listdir(src) if f.endswith(".txt"))
    for name in files:
        with open(os.path.join(src, name), encoding="latin-1") as fh:
            prev = 0
            for ch in fh.read().upper():
                cur = SYMBOLS.find(ch) if "A" <= ch <= "Z" else 0
                if cur == 0 and prev == 0:
                    continue
                counts[prev][cur] += 1
                prev = cur
    table = []
    for a in range(N):
        total = sum(counts[a]) + N
        table.extend(round(1000 * math.log((counts[a][b] + 1) / total)) for b in range(N))
    lines = []
    for a in range(N):
        row = ", ".join(str(v) for v in table[a * N:(a + 1) * N])
        lines.append(f"        {row}, // {SYMBOLS[a]}")
    with open(out, "w") as fh:
        fh.write(f"""package com.davicolzer.zureader.zu

// Arquivo gerado por tools/gen_english_bigrams.py. Não editar à mão.
// Fonte: corpus Gutenberg do NLTK ({len(files)} livros em domínio público).

/**
 * Log-probabilidades P(próximo | anterior) de letras do inglês, em milésimos de nat.
 * Índice 0 = fronteira de palavra; 1..26 = A..Z. Linha = anterior, coluna = próximo.
 */
internal object EnglishBigrams {{
    const val SIZE = {N}

    val logProb: IntArray = intArrayOf(
{chr(10).join(lines)}
    )
}}
""")
    return table


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
