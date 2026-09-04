"""
Converte o modelo Keras treinado (models/posture_classifier.keras) pro formato
TFLite, pronto pra embarcar no app Android (A.1.4 do cronograma do TCC02).

Uso:
    python export_tflite.py
"""
from pathlib import Path

import tensorflow as tf

MODELS_DIR = Path(__file__).parent / "models"
KERAS_PATH = MODELS_DIR / "posture_classifier.keras"
TFLITE_PATH = MODELS_DIR / "posture_classifier.tflite"


def main() -> None:
    if not KERAS_PATH.exists():
        raise SystemExit(f"Modelo não encontrado em {KERAS_PATH} — rode train.py primeiro.")

    model = tf.keras.models.load_model(KERAS_PATH)
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    # Quantização dinâmica: reduz o tamanho do modelo sem exigir dataset de calibração.
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    TFLITE_PATH.write_bytes(tflite_model)
    size_kb = TFLITE_PATH.stat().st_size / 1024
    print(f"Modelo TFLite salvo em {TFLITE_PATH} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
