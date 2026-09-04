"""
Classificador multirrótulo de postura — Dança AI (TCC02, atividade A.1.3).

Substitui o baseline multiclasse, que estava errado na premissa: no uso real do
app os desvios coexistem (ombro esquerdo caído E ombro à frente ao mesmo tempo).
Aqui cada desvio vira um detector binário independente, e "postura boa" é o caso
em que nenhum detector dispara — não uma quarta classe concorrente.

Saídas deste script:
  1. Quais coordenadas realmente importam para cada desvio (importância por
     permutação), respondendo se vale carregar os 33 valores ou um subconjunto.
  2. Comparação justa contra o PostureValidator, que também é multirrótulo.
  3. Modelo TFLite pronto para embarcar, com a normalização embutida no grafo.

Dependências: pandas, numpy, scikit-learn, tensorflow (todas já no Colab).
"""

# %% ────────────────────────────── imports ──────────────────────────────

import hashlib
import tempfile
from datetime import datetime

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.ensemble import RandomForestClassifier
from sklearn.inspection import permutation_importance
from sklearn.metrics import f1_score, precision_recall_fscore_support
from sklearn.model_selection import GroupKFold

RANDOM_STATE = 42
np.random.seed(RANDOM_STATE)
tf.random.set_seed(RANDOM_STATE)

# %% ─────────────────────── 1. carregar e congelar ───────────────────────

SHEET_ID = "1hNIBCHZfQR7AEAeGuJ4ga6K_XTPrlMExZEBIXffpqUc"
CSV_URL = f"https://docs.google.com/spreadsheets/d/{SHEET_ID}/export?format=csv&gid=0"

# A planilha está em locale pt-BR (vírgula decimal, campos entre aspas). Ler como
# texto e converter explicitamente funciona nos dois locales.
df = pd.read_csv(CSV_URL, dtype=str)
COLUNAS_TEXTO = ("Timestamp", "Rotulo")
for coluna in [c for c in df.columns if c not in COLUNAS_TEXTO]:
    df[coluna] = pd.to_numeric(df[coluna].str.replace(",", ".", regex=False), errors="coerce")

snapshot = f"dataset_postura_{datetime.now():%Y%m%d_%H%M}.csv"
df.to_csv(snapshot, index=False)
print(f"Snapshot: {snapshot}")
print(f"SHA-256 (12): {hashlib.sha256(df.to_csv(index=False).encode()).hexdigest()[:12]}")
print(f"Linhas: {len(df)}")

# Agrupamento por sessão de coleta: evita que linhas quase idênticas caiam ao
# mesmo tempo no treino e no teste. Derivado do Timestamp, não identifica ninguém.
GAP_MINUTOS = 5
df["Timestamp"] = pd.to_datetime(df["Timestamp"])
df = df.sort_values("Timestamp").reset_index(drop=True)
df["sessao"] = (df["Timestamp"].diff() > pd.Timedelta(minutes=GAP_MINUTOS)).cumsum()
grupos = df["sessao"].to_numpy()
print(f"Sessões de coleta detectadas: {df['sessao'].nunique()}")

# %% ─────────────────────── 2. alvos multirrótulo ───────────────────────

# Cada desvio é um problema binário próprio. Postura boa = nenhum ativo.
DESVIOS = ["OE", "OD", "OF"]
NOMES_DESVIOS = {
    "OE": "Ombro esquerdo caído",
    "OD": "Ombro direito caído",
    "OF": "Ombros à frente",
}

Y = np.column_stack([(df["Rotulo"] == f"post_ruim_{d}").to_numpy(int) for d in DESVIOS])

print("\nPositivos por desvio (o restante é negativo):")
for i, d in enumerate(DESVIOS):
    print(f"  {d} ({NOMES_DESVIOS[d]}): {Y[:, i].sum()} de {len(Y)}")
print(f"  Linhas sem nenhum desvio (postura boa): {(Y.sum(axis=1) == 0).sum()}")
print(
    "\n⚠  A coleta induziu um desvio por vez: nenhuma linha tem dois rótulos ativos.\n"
    "   Os detectores aprendem cada desvio isolado; a resposta a desvios\n"
    "   SIMULTÂNEOS é extrapolação e precisa ser validada em coleta futura."
)

# %% ─────────────────────── 3. features ───────────────────────

SUPERIORES = ["NAR", "ORE-E", "ORE-D", "OMB-E", "OMB-D", "QDR-E", "QDR-D"]
INFERIORES = ["JOE-E", "JOE-D", "TRN-E", "TRN-D"]


def xyz(marco: str) -> np.ndarray:
    return df[[f"{marco}_x", f"{marco}_y", f"{marco}_z"]].to_numpy(dtype=float)


# Referencial do corpo: origem no meio do quadril, escala pela distância entre
# ombros — remove distância da câmera e estatura, que senão viram o "sinal".
quadril_centro = (xyz("QDR-E") + xyz("QDR-D")) / 2.0
ombro_esq, ombro_dir = xyz("OMB-E"), xyz("OMB-D")
escala = np.linalg.norm(ombro_esq[:, :2] - ombro_dir[:, :2], axis=1)
escala = np.where(escala < 1e-6, np.nan, escala)


def bloco_coordenadas(marcos: list[str]) -> pd.DataFrame:
    dados = {}
    for m in marcos:
        v = (xyz(m) - quadril_centro) / escala[:, None]
        dados[f"{m}_xn"], dados[f"{m}_yn"], dados[f"{m}_zn"] = v[:, 0], v[:, 1], v[:, 2]
    return pd.DataFrame(dados, index=df.index)


X_todas = bloco_coordenadas(SUPERIORES + INFERIORES)  # 33 coordenadas

# Features geométricas: as mesmas grandezas que as regras usam, mais indicadores
# de cabeça projetada à frente.
span = np.abs(ombro_dir[:, 0] - ombro_esq[:, 0])
span_seguro = np.where(span < 1e-6, np.nan, span)
ombro_dy = ombro_esq[:, 1] - ombro_dir[:, 1]  # positivo = ombro esquerdo mais baixo
angulo_ombros = np.degrees(np.arctan2(ombro_dy, np.abs(ombro_dir[:, 0] - ombro_esq[:, 0])))
z_ombros = (ombro_esq[:, 2] + ombro_dir[:, 2]) / 2.0
z_quadril = quadril_centro[:, 2]
zdiff_norm = (z_ombros - z_quadril) / span_seguro
ombro_meio = (ombro_esq + ombro_dir) / 2.0
tilt_lateral = np.degrees(
    np.arctan2(ombro_meio[:, 0] - quadril_centro[:, 0], quadril_centro[:, 1] - ombro_meio[:, 1])
)
orelha_esq, orelha_dir, nariz = xyz("ORE-E"), xyz("ORE-D"), xyz("NAR")

X_geo = pd.DataFrame(
    {
        "angulo_ombros": angulo_ombros,
        "zdiff_norm": zdiff_norm,
        "tilt_lateral": tilt_lateral,
        "assimetria_z_ombros": (ombro_esq[:, 2] - ombro_dir[:, 2]) / span_seguro,
        "nariz_z_norm": (nariz[:, 2] - z_quadril) / span_seguro,
        "orelhas_z_norm": ((orelha_esq[:, 2] + orelha_dir[:, 2]) / 2 - z_quadril) / span_seguro,
        "orelha_ombro_dy_esq": (orelha_esq[:, 1] - ombro_esq[:, 1]) / span_seguro,
        "orelha_ombro_dy_dir": (orelha_dir[:, 1] - ombro_dir[:, 1]) / span_seguro,
        "razao_tronco_ombros": np.linalg.norm(ombro_meio[:, :2] - quadril_centro[:, :2], axis=1)
        / span_seguro,
    },
    index=df.index,
)

CONJUNTOS = {"coordenadas (33)": X_todas, "geométricas (9)": X_geo}
for nome, X in CONJUNTOS.items():
    CONJUNTOS[nome] = X.fillna(X.median(numeric_only=True))

# %% ───────────── 4. regras do app como baseline multirrótulo ─────────────

# Port do PostureValidator.kt. Ele SEMPRE foi multirrótulo (devolve List<Issue>);
# avaliá-lo por rótulo é a comparação justa. Ressalva: o app também exige
# visibility >= 0.4, coluna que não vai para a planilha — o baseline aqui é
# ligeiramente otimista.
LIMIAR_NIVEL_OMBROS = 5.0
LIMIAR_OMBROS_FRENTE = 0.40
SPAN_FRONTAL_MINIMO = 0.15

vista_frontal = span >= SPAN_FRONTAL_MINIMO
desnivelado = np.abs(angulo_ombros) > LIMIAR_NIVEL_OMBROS

Y_regras = np.column_stack(
    [
        desnivelado & (ombro_dy > 0),                              # OE
        desnivelado & (ombro_dy <= 0),                             # OD
        (zdiff_norm < -LIMIAR_OMBROS_FRENTE) & vista_frontal,      # OF
    ]
).astype(int)

# %% ──────────── 5. quais coordenadas importam para cada desvio ────────────

print("\n" + "=" * 78)
print("IMPORTÂNCIA DAS COORDENADAS (permutação, por desvio)")
print("=" * 78)

cv = GroupKFold(n_splits=min(5, df["sessao"].nunique()))
treino_idx, teste_idx = next(cv.split(X_todas, Y[:, 0], grupos))

for i, d in enumerate(DESVIOS):
    rf = RandomForestClassifier(n_estimators=400, random_state=RANDOM_STATE)
    rf.fit(X_todas.iloc[treino_idx], Y[treino_idx, i])
    imp = permutation_importance(
        rf, X_todas.iloc[teste_idx], Y[teste_idx, i],
        n_repeats=20, random_state=RANDOM_STATE, scoring="f1",
    )
    ranking = (
        pd.Series(imp.importances_mean, index=X_todas.columns)
        .sort_values(ascending=False)
        .head(8)
    )
    print(f"\n{d} — {NOMES_DESVIOS[d]} (top 8 de 33):")
    print(ranking.to_string(float_format=lambda v: f"{v:+.4f}"))

# Quanto do sinal vem do tronco para cima? Se os membros inferiores não
# contribuem, carregá-los custa latência sem ganho.
inferiores_cols = [c for c in X_todas.columns if c.split("_")[0] in INFERIORES]
print(f"\nColunas de membros inferiores no conjunto: {len(inferiores_cols)} de {X_todas.shape[1]}")

# %% ─────────────── 6. MLP multirrótulo com validação por sessão ───────────────


def construir_mlp(n_features: int, X_ajuste: np.ndarray) -> tf.keras.Model:
    """MLP pequena com 3 saídas sigmoid — multirrótulo por construção.

    A normalização entra como camada do grafo, então o TFLite recebe as features
    cruas e o Kotlin não precisa replicar média/desvio à mão.
    """
    normalizacao = tf.keras.layers.Normalization()
    normalizacao.adapt(X_ajuste)
    modelo = tf.keras.Sequential(
        [
            tf.keras.layers.Input(shape=(n_features,)),
            normalizacao,
            tf.keras.layers.Dense(16, activation="relu"),
            tf.keras.layers.Dense(8, activation="relu"),
            tf.keras.layers.Dense(len(DESVIOS), activation="sigmoid"),
        ]
    )
    modelo.compile(optimizer="adam", loss="binary_crossentropy")
    return modelo


def avaliar(Y_real: np.ndarray, Y_pred: np.ndarray, titulo: str) -> dict:
    print(f"\n{titulo}")
    print(f"{'desvio':<28}{'precisão':>10}{'recall':>10}{'f1':>8}")
    for i, d in enumerate(DESVIOS):
        p, r, f, _ = precision_recall_fscore_support(
            Y_real[:, i], Y_pred[:, i], average="binary", zero_division=0
        )
        print(f"{d + ' — ' + NOMES_DESVIOS[d]:<28}{p:>10.3f}{r:>10.3f}{f:>8.3f}")

    exato = (Y_real == Y_pred).all(axis=1).mean()
    ruim_real, ruim_pred = Y_real.any(axis=1), Y_pred.any(axis=1)
    passou_batido = (ruim_real & ~ruim_pred).sum() / max(ruim_real.sum(), 1)
    print(f"{'todos os rótulos corretos':<28}{exato:>28.3f}")
    print(f"{'boa vs ruim (acurácia)':<28}{(ruim_real == ruim_pred).mean():>28.3f}")
    print(f"{'postura ruim liberada':<28}{passou_batido:>28.3f}")
    return {"f1_macro": f1_score(Y_real, Y_pred, average="macro", zero_division=0)}


resultados = {}
for nome_conjunto, X in CONJUNTOS.items():
    Xv = X.to_numpy(dtype="float32")
    Y_pred = np.zeros_like(Y)
    for tr, te in cv.split(Xv, Y[:, 0], grupos):
        modelo = construir_mlp(Xv.shape[1], Xv[tr])
        modelo.fit(
            Xv[tr], Y[tr], epochs=300, batch_size=16, verbose=0,
            validation_split=0.2,
            callbacks=[tf.keras.callbacks.EarlyStopping(patience=30, restore_best_weights=True)],
        )
        Y_pred[te] = (modelo.predict(Xv[te], verbose=0) > 0.5).astype(int)
    resultados[nome_conjunto] = avaliar(
        Y, Y_pred, f"MLP multirrótulo — features {nome_conjunto}"
    )

avaliar(Y, Y_regras, "Regras geométricas do app (PostureValidator), comparação justa")

# %% ────────────────── 7. modelo final e exportação TFLite ──────────────────

MELHOR = max(resultados, key=lambda k: resultados[k]["f1_macro"])
print(f"\nConjunto escolhido: {MELHOR}")

X_final = CONJUNTOS[MELHOR].to_numpy(dtype="float32")
modelo_final = construir_mlp(X_final.shape[1], X_final)
modelo_final.fit(
    X_final, Y, epochs=300, batch_size=16, verbose=0, validation_split=0.2,
    callbacks=[tf.keras.callbacks.EarlyStopping(patience=30, restore_best_weights=True)],
)


def exportar_tflite(modelo: tf.keras.Model, caminho: str = "postura.tflite") -> int:
    try:
        blob = tf.lite.TFLiteConverter.from_keras_model(modelo).convert()
    except Exception:
        # Keras 3 exige passar por SavedModel para a conversão.
        destino = tempfile.mkdtemp()
        modelo.export(destino)
        blob = tf.lite.TFLiteConverter.from_saved_model(destino).convert()
    with open(caminho, "wb") as arquivo:
        arquivo.write(blob)
    return len(blob)


tamanho = exportar_tflite(modelo_final)
print(f"\nTFLite exportado: postura.tflite ({tamanho / 1024:.1f} KB)")

# Confere que o modelo convertido responde igual ao original.
interpretador = tf.lite.Interpreter(model_path="postura.tflite")
interpretador.allocate_tensors()
entrada = interpretador.get_input_details()[0]
saida = interpretador.get_output_details()[0]
interpretador.set_tensor(entrada["index"], X_final[:1])
interpretador.invoke()
print("Saída TFLite (amostra 1):   ", np.round(interpretador.get_tensor(saida["index"])[0], 4))
print("Saída Keras  (amostra 1):   ", np.round(modelo_final.predict(X_final[:1], verbose=0)[0], 4))

# %% ─────────────────────── 8. integração no Android ───────────────────────

print(
    f"""
Para embarcar
─────────────
· Ordem das entradas do modelo ({X_final.shape[1]} floats), a ser reproduzida em Kotlin:
  {list(CONJUNTOS[MELHOR].columns)}

· Ordem das saídas ({len(DESVIOS)} floats, sigmoid independente): {DESVIOS}
  Cada valor é a probabilidade daquele desvio, entre 0 e 1. Postura boa é o caso
  em que os três ficam abaixo do limiar — não existe saída "boa" no modelo.

· A normalização está dentro do grafo: passe as features cruas, sem escalar.

· O limiar de 0,5 é um ponto de partida. Calibrá-lo por desvio é a atividade
  A.3.3 do cronograma, e é onde se decide o equilíbrio entre alarme falso e
  postura ruim liberada — para um app de treino, liberar postura ruim é o erro
  mais caro.
"""
)
