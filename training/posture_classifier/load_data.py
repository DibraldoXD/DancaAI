"""
Carrega o CSV exportado da planilha de capturas (Google Sheets -> Arquivo -> Fazer
download -> Valores separados por vírgula), valida as colunas esperadas e monta a
matriz de features + rótulo pro treino do classificador de postura (A.1.4 do
cronograma do TCC02).

Uso:
    python load_data.py caminho/para/capturas.csv
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pandas as pd

# Mesma ordem de landmarks que o app captura em TrainingScreen.kt (toSheetRow()).
LANDMARK_NAMES = [
    "NAR", "ORE-E", "ORE-D", "OMB-E", "OMB-D",
    "QDR-E", "QDR-D", "JOE-E", "JOE-D", "TRN-E", "TRN-D",
]
COORD_COLUMNS = [f"{name}_{axis}" for name in LANDMARK_NAMES for axis in ("x", "y", "z")]

# Coluna adicionada manualmente na planilha (não é enviada pelo app) — ver README.
LABEL_COLUMN = "Rotulo_Postura"
LABEL_MAP = {
    "correta": 1, "correto": 1, "ok": 1, "bom": 1, "boa": 1, "1": 1,
    "incorreta": 0, "incorreto": 0, "erro": 0, "ruim": 0, "0": 0,
}


def load_labeled_dataframe(csv_path: str | Path) -> pd.DataFrame:
    """Lê o CSV e mantém só as linhas com Rotulo_Postura preenchido e reconhecido."""
    df = pd.read_csv(csv_path)

    missing = [c for c in COORD_COLUMNS if c not in df.columns]
    if missing:
        raise ValueError(
            f"Colunas de landmark ausentes no CSV: {missing}. "
            "Confira se a legenda foi colada certinho na planilha (ver README)."
        )
    if LABEL_COLUMN not in df.columns:
        raise ValueError(
            f"Coluna '{LABEL_COLUMN}' não encontrada. Adicione essa coluna na planilha "
            "e rotule manualmente cada captura como 'correta' ou 'incorreta' antes de treinar."
        )

    df["_label_norm"] = df[LABEL_COLUMN].astype(str).str.strip().str.lower()
    labeled = df[df["_label_norm"].isin(LABEL_MAP)].copy()

    unlabeled_count = len(df) - len(labeled)
    if unlabeled_count:
        print(f"Aviso: {unlabeled_count} captura(s) sem rótulo reconhecido foram ignoradas.")

    labeled["label"] = labeled["_label_norm"].map(LABEL_MAP)
    return labeled.drop(columns=["_label_norm"])


def build_features(df: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
    """
    Monta a matriz de features: coordenadas normalizadas (invariantes à distância da
    câmera e à estatura da pessoa) + as métricas derivadas já calculadas pelo app.

    A normalização espelha a lógica de PostureValidator.kt: centraliza no ponto médio
    dos ombros e divide pelo span de ombros — assim o modelo não aprende a depender de
    o quão perto da câmera a pessoa estava.
    """
    def col(name: str, axis: str) -> np.ndarray:
        return df[f"{name}_{axis}"].to_numpy(dtype=np.float32)

    span = np.abs(col("OMB-D", "x") - col("OMB-E", "x"))
    span = np.where(span < 1e-4, 1e-4, span)  # evita divisão por zero em capturas ruins

    mid_x = (col("OMB-E", "x") + col("OMB-D", "x")) / 2
    mid_y = (col("OMB-E", "y") + col("OMB-D", "y")) / 2

    normalized = []
    for name in LANDMARK_NAMES:
        normalized.append((col(name, "x") - mid_x) / span)
        normalized.append((col(name, "y") - mid_y) / span)
        normalized.append(col(name, "z") / span)
    features = np.stack(normalized, axis=1)

    zdiff_norm = df["ZDiff"].to_numpy(dtype=np.float32) / span
    extra = np.stack([span, zdiff_norm], axis=1)

    X = np.concatenate([features, extra], axis=1).astype(np.float32)
    y = df["label"].to_numpy(dtype=np.float32)
    return X, y


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Uso: python load_data.py caminho/para/capturas.csv")
        sys.exit(1)

    dataframe = load_labeled_dataframe(sys.argv[1])
    X, y = build_features(dataframe)
    print(f"{len(dataframe)} captura(s) rotulada(s) — {int(y.sum())} corretas, {int((1 - y).sum())} incorretas.")
    print(f"Shape das features: {X.shape}")
