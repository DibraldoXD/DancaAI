"""
Treina o classificador de postura (MLP pequeno em Keras) sobre os dados rotulados
manualmente na planilha e salva o modelo treinado em models/ (A.1.4 do cronograma
do TCC02).

Uso:
    python train.py caminho/para/capturas.csv
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import train_test_split

from load_data import build_features, load_labeled_dataframe

MODELS_DIR = Path(__file__).parent / "models"


def build_model(input_dim: int) -> tf.keras.Model:
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=(input_dim,)),
        tf.keras.layers.Dense(32, activation="relu"),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(16, activation="relu"),
        tf.keras.layers.Dense(1, activation="sigmoid"),
    ])
    model.compile(optimizer="adam", loss="binary_crossentropy", metrics=["accuracy"])
    return model


def main(csv_path: str) -> None:
    df = load_labeled_dataframe(csv_path)
    X, y = build_features(df)

    if len(np.unique(y)) < 2:
        raise SystemExit(
            "Dataset com só uma classe rotulada — precisa de exemplos 'correta' e "
            "'incorreta' pra treinar. Rotule mais capturas antes de rodar de novo."
        )

    X_train, X_temp, y_train, y_temp = train_test_split(
        X, y, test_size=0.3, random_state=42, stratify=y,
    )
    X_val, X_test, y_val, y_test = train_test_split(
        X_temp, y_temp, test_size=0.5, random_state=42, stratify=y_temp,
    )

    model = build_model(X.shape[1])
    model.fit(
        X_train, y_train,
        validation_data=(X_val, y_val),
        epochs=100,
        batch_size=16,
        callbacks=[tf.keras.callbacks.EarlyStopping(patience=10, restore_best_weights=True)],
        verbose=2,
    )

    y_pred = (model.predict(X_test) > 0.5).astype(int).ravel()
    print("\n== Avaliação no conjunto de teste ==")
    print(classification_report(y_test, y_pred, target_names=["incorreta", "correta"]))
    print("Matriz de confusão:")
    print(confusion_matrix(y_test, y_pred))

    MODELS_DIR.mkdir(exist_ok=True)
    out_path = MODELS_DIR / "posture_classifier.keras"
    model.save(out_path)
    print(f"\nModelo salvo em {out_path}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Uso: python train.py caminho/para/capturas.csv")
        sys.exit(1)
    main(sys.argv[1])
