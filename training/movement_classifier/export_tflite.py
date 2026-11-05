"""
Exportação do classificador de passos para TFLite — Dança AI (TCC02, A.2.2).

Converte o modelo salvo por `train.py`, confere que o modelo convertido responde
igual ao original e imprime o contrato de integração: a ordem exata das entradas
e das saídas, que é o que o lado Kotlin precisa reproduzir.

Essa impressão não é documentação opcional. O modelo recebe um vetor de floats
sem nomes; se o Kotlin montar as features em outra ordem, o modelo continua
respondendo — com valores errados e sem nenhum erro em lugar nenhum.

Uso:
    python export_tflite.py
"""

from __future__ import annotations

import json
import sys
import tempfile
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(errors="replace")

import numpy as np
import tensorflow as tf

MODELOS_DIR = Path(__file__).parent / "models"
CAMINHO_KERAS = MODELOS_DIR / "movement_classifier.keras"
CAMINHO_META = MODELOS_DIR / "movement_classifier_meta.json"
CAMINHO_TFLITE = MODELOS_DIR / "movement_classifier.tflite"


def converter(modelo: tf.keras.Model) -> bytes:
    """Converte para TFLite sem quantização.

    Sem quantizar de propósito: o modelo exportado tem poucos milhares de
    parâmetros e já cabe em alguns KB, então a quantização economizaria espaço
    irrelevante em troca de deslocar as saídas. Num classificador de 6 classes
    decidido por `argmax`, um deslocamento pequeno troca o passo previsto perto
    do empate — exatamente entre abertura e giro em linha, que é o par difícil.
    """
    try:
        return tf.lite.TFLiteConverter.from_keras_model(modelo).convert()
    except Exception:
        # Keras 3 exige passar por SavedModel para a conversão.
        destino = tempfile.mkdtemp()
        modelo.export(destino)
        return tf.lite.TFLiteConverter.from_saved_model(destino).convert()


def main() -> None:
    if not CAMINHO_KERAS.exists():
        raise SystemExit(f"Modelo não encontrado em {CAMINHO_KERAS} — rode train.py primeiro.")
    if not CAMINHO_META.exists():
        raise SystemExit(
            f"Metadados não encontrados em {CAMINHO_META}. Sem a ordem das features o "
            "modelo não é embarcável; rode train.py de novo."
        )

    meta = json.loads(CAMINHO_META.read_text(encoding="utf-8"))
    modelo = tf.keras.models.load_model(CAMINHO_KERAS)

    blob = converter(modelo)
    CAMINHO_TFLITE.write_bytes(blob)
    print(f"TFLite exportado: {CAMINHO_TFLITE.name} ({len(blob) / 1024:.1f} KB)")

    # Conferência: o TFLite tem de responder o mesmo que o Keras. Uma divergência
    # aqui é erro de conversão, e passaria batido no app como "o modelo erra no
    # celular e acerta no notebook".
    entradas = meta["features"]
    amostra = np.zeros((1, len(entradas)), dtype="float32")
    interpretador = tf.lite.Interpreter(model_content=blob)
    interpretador.allocate_tensors()
    detalhe_entrada = interpretador.get_input_details()[0]
    detalhe_saida = interpretador.get_output_details()[0]
    interpretador.set_tensor(detalhe_entrada["index"], amostra)
    interpretador.invoke()
    saida_tflite = interpretador.get_tensor(detalhe_saida["index"])[0]
    saida_keras = modelo.predict(amostra, verbose=0)[0]
    print(f"Maior diferença Keras x TFLite: {np.abs(saida_tflite - saida_keras).max():.2e}")

    passos = meta["classes"]
    print(
        f"""
Para embarcar
-------------
. Estrategia de janelamento: {meta['estrategia']}@{meta['janela']}  (arquitetura {meta['arquitetura']})
  Acuracia fora da amostra (LOPO, {len(meta['participantes'])} participantes,
  {meta['gravacoes']} gravacoes): {meta['acuracia_lopo_janela']:.3f} por janela,
  {meta['acuracia_lopo_gravacao']:.3f} por gravacao no voto majoritario.

. Entradas: {len(entradas)} floats, NESTA ORDEM EXATA:
  {entradas}

. Saidas: {len(passos)} floats (softmax, somam 1), nesta ordem:
  {passos}
  O passo previsto e o argmax; o valor correspondente e a confianca. Para o modo
  Livre vale exigir um minimo de confianca antes de mostrar um passo, senao a
  tela troca de rotulo a cada janela durante a transicao entre passos.

. A normalizacao esta DENTRO do grafo: passe as features cruas, sem escalar.

. O app nao deve decidir por uma janela so. A acuracia por gravacao acima vem de
  voto majoritario entre as janelas da execucao, e e essa a decisao que
  corresponde a experiencia de uso.

. Como montar as features em Kotlin (ver features.py):
  1. Normalize tudo pelo COMPRIMENTO DO TRONCO (distancia xy entre o ponto medio
     dos ombros e o ponto medio dos quadris), nunca pelo span de ombros: o span
     colapsa quando o dancarino fica de perfil, e divide-se por quase zero
     exatamente nos quadros que distinguem abertura de giro em linha.
  2. O sinal de rotacao e o deslocamento horizontal ASSINADO entre os ombros
     (OMB-D_x - OMB-E_x), nao o modulo. O modulo dobra a curva no zero e
     transforma uma rotacao suave em oscilacao de aparencia aleatoria.
  3. O yaw calibrado usa como referencia o percentil 95 de |dx| da janela. No
     app, essa referencia deve vir do enquadramento frontal inicial (guia de
     camera), medida uma vez por sessao, e nao da janela corrente.
  4. Derive as velocidades pelo tempo real entre amostras, nao pelo indice: com
     frames perdidos, derivar pelo indice subestima justamente os trechos
     rapidos.
"""
    )


if __name__ == "__main__":
    main()
