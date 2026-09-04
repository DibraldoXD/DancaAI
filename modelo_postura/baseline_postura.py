"""
SUPERADO por `treinar_multilabel.py`. Mantido como registro da exploração inicial.

A premissa aqui é multiclasse — quatro classes mutuamente exclusivas —, e ela
está errada: no uso real do app os desvios coexistem (ombro esquerdo caído E
ombro à frente ao mesmo tempo). Este script também avalia o PostureValidator
forçando precedência entre desvios, o que penaliza as regras injustamente, já
que elas sempre devolveram uma lista. Use o script multirrótulo.

O que ainda vale daqui: a comparação entre conjuntos de features (coordenadas
cruas × features geométricas), que motivou a escolha do conjunto reduzido.

────────────────────────────────────────────────────────────────────────────

Baseline do classificador de postura — Dança AI (TCC02, atividade A.1.3).

Roda de cima para baixo, no Colab ou local (`python baseline_postura.py`).
As marcações `# %%` delimitam células caso você abra num editor compatível.

O que este script responde:

  1. O dataset separa as quatro classes de postura?
  2. Um modelo treinado nas coordenadas ganha das regras geométricas que o
     `PostureValidator.kt` já aplica hoje? Sem essa comparação não há como
     justificar embarcar um modelo, já que as regras são mais baratas.
  3. O desempenho se sustenta em dados de uma sessão de coleta que o modelo
     nunca viu?

Dependências: pandas, numpy, scikit-learn, matplotlib (todas já no Colab).
"""

# %% ────────────────────────────── imports ──────────────────────────────

import hashlib
from datetime import datetime

import numpy as np
import pandas as pd
from sklearn.dummy import DummyClassifier
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
from sklearn.model_selection import GroupKFold, cross_val_predict
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

RANDOM_STATE = 42

# %% ─────────────────────── 1. carregar e congelar ───────────────────────

SHEET_ID = "1hNIBCHZfQR7AEAeGuJ4ga6K_XTPrlMExZEBIXffpqUc"
CSV_URL = f"https://docs.google.com/spreadsheets/d/{SHEET_ID}/export?format=csv&gid=0"

# A planilha está em locale pt-BR: os números saem com vírgula decimal e entre
# aspas ("0,4778"). Ler tudo como texto e converter explicitamente funciona nos
# dois locales, sem depender de como a planilha estiver configurada no dia.
df = pd.read_csv(CSV_URL, dtype=str)

COLUNAS_TEXTO = ("Timestamp", "Rotulo")
for coluna in [c for c in df.columns if c not in COLUNAS_TEXTO]:
    df[coluna] = pd.to_numeric(
        df[coluna].str.replace(",", ".", regex=False), errors="coerce"
    )

# A planilha continua crescendo; a monografia precisa citar uma versão exata.
# O snapshot congelado é o dataset de referência — a planilha é só a entrada.
snapshot_name = f"dataset_postura_{datetime.now():%Y%m%d_%H%M}.csv"
df.to_csv(snapshot_name, index=False)
snapshot_hash = hashlib.sha256(df.to_csv(index=False).encode()).hexdigest()[:12]

print(f"Snapshot: {snapshot_name}")
print(f"SHA-256 (12): {snapshot_hash}")
print(f"Linhas: {len(df)}  |  Colunas: {len(df.columns)}")

# %% ─────────────────────── 2. verificações básicas ───────────────────────

LABEL = "Rotulo"

print("\nDistribuição das classes:")
print(df[LABEL].value_counts().to_string())

faltantes = df.isna().sum()
faltantes = faltantes[faltantes > 0]
print("\nValores ausentes por coluna:")
print(faltantes.to_string() if not faltantes.empty else "  nenhum")

# Coluna constante não carrega informação: entra no relatório e sai do treino.
numericas = df.select_dtypes(include=[np.number])
constantes = [c for c in numericas.columns if numericas[c].nunique() <= 1]
print(f"\nColunas constantes (descartadas do treino): {constantes or 'nenhuma'}")

# %% ──────────────────── 3. agrupar por sessão de coleta ────────────────────

# Sem identificador de participante, o Timestamp é o que temos para evitar que
# linhas quase idênticas caiam ao mesmo tempo no treino e no teste. Capturas de
# uma mesma pessoa formam um bloco contínuo; um intervalo grande indica troca de
# sessão. Isso NÃO identifica ninguém — é só um agrupamento temporal.
GAP_MINUTOS = 5

df["Timestamp"] = pd.to_datetime(df["Timestamp"])
df = df.sort_values("Timestamp").reset_index(drop=True)

gap = df["Timestamp"].diff() > pd.Timedelta(minutes=GAP_MINUTOS)
df["sessao"] = gap.cumsum()

print(f"\nSessões detectadas (intervalo > {GAP_MINUTOS} min): {df['sessao'].nunique()}")
print(pd.crosstab(df["sessao"], df[LABEL]).to_string())

# %% ─────────────────────── 4. normalização e features ───────────────────────

# Prefixos das colunas, conforme o cabeçalho gerado pelo app:
#   NAR = nariz | ORE = orelha | OMB = ombro | QDR = quadril
#   JOE = joelho | TRN = tornozelo | -E / -D = esquerdo / direito
SUPERIORES = ["NAR", "ORE-E", "ORE-D", "OMB-E", "OMB-D", "QDR-E", "QDR-D"]
INFERIORES = ["JOE-E", "JOE-D", "TRN-E", "TRN-D"]


def xyz(marco: str) -> np.ndarray:
    """Matriz (n, 3) com as coordenadas do landmark informado."""
    return df[[f"{marco}_x", f"{marco}_y", f"{marco}_z"]].to_numpy(dtype=float)


# Referencial do corpo: origem no meio do quadril, escala pela distância entre
# ombros. Sem isso o modelo aprende a distância da câmera e a estatura da pessoa
# em vez da postura — é a mesma normalização que o PostureValidator já faz.
quadril_centro = (xyz("QDR-E") + xyz("QDR-D")) / 2.0
ombro_esq, ombro_dir = xyz("OMB-E"), xyz("OMB-D")
escala = np.linalg.norm(ombro_esq[:, :2] - ombro_dir[:, :2], axis=1)
escala = np.where(escala < 1e-6, np.nan, escala)  # evita divisão por zero


def normalizar(marco: str) -> np.ndarray:
    return (xyz(marco) - quadril_centro) / escala[:, None]


def bloco(marcos: list[str]) -> pd.DataFrame:
    dados = {}
    for m in marcos:
        v = normalizar(m)
        dados[f"{m}_xn"], dados[f"{m}_yn"], dados[f"{m}_zn"] = v[:, 0], v[:, 1], v[:, 2]
    return pd.DataFrame(dados, index=df.index)


# --- Conjunto A: coordenadas normalizadas do tronco para cima -----------------
X_a = bloco(SUPERIORES)

# --- Conjunto B: todas as coordenadas normalizadas ----------------------------
X_b = bloco(SUPERIORES + INFERIORES)

# --- Conjunto C: features geométricas derivadas -------------------------------
# São as mesmas grandezas que as regras usam, mais alguns indicadores de cabeça
# projetada à frente. Serve para responder se features interpretáveis bastam.
span = np.abs(ombro_dir[:, 0] - ombro_esq[:, 0])
span_seguro = np.where(span < 1e-6, np.nan, span)

ombro_dy = ombro_esq[:, 1] - ombro_dir[:, 1]  # positivo = ombro esquerdo mais baixo
angulo_ombros = np.degrees(np.arctan2(ombro_dy, np.abs(ombro_dir[:, 0] - ombro_esq[:, 0])))

z_ombros = (ombro_esq[:, 2] + ombro_dir[:, 2]) / 2.0
z_quadril = quadril_centro[:, 2]
zdiff_norm = (z_ombros - z_quadril) / span_seguro

ombro_meio = (ombro_esq + ombro_dir) / 2.0
dx = ombro_meio[:, 0] - quadril_centro[:, 0]
dy = quadril_centro[:, 1] - ombro_meio[:, 1]
tilt_lateral = np.degrees(np.arctan2(dx, dy))

orelha_esq, orelha_dir = xyz("ORE-E"), xyz("ORE-D")
nariz = xyz("NAR")

X_c = pd.DataFrame(
    {
        "angulo_ombros": angulo_ombros,
        "zdiff_norm": zdiff_norm,
        "tilt_lateral": tilt_lateral,
        "assimetria_z_ombros": (ombro_esq[:, 2] - ombro_dir[:, 2]) / span_seguro,
        "nariz_z_norm": (nariz[:, 2] - z_quadril) / span_seguro,
        "orelhas_z_norm": ((orelha_esq[:, 2] + orelha_dir[:, 2]) / 2 - z_quadril) / span_seguro,
        "orelha_ombro_dy_esq": (orelha_esq[:, 1] - ombro_esq[:, 1]) / span_seguro,
        "orelha_ombro_dy_dir": (orelha_dir[:, 1] - ombro_dir[:, 1]) / span_seguro,
        "razao_tronco_ombros": np.linalg.norm(ombro_meio[:, :2] - quadril_centro[:, :2], axis=1) / span_seguro,
    },
    index=df.index,
)

CONJUNTOS = {
    "A · coords tronco p/ cima (21)": X_a,
    "B · todas as coords (33)": X_b,
    "C · features geométricas (9)": X_c,
}

y = df[LABEL].to_numpy()
grupos = df["sessao"].to_numpy()

# %% ────────────────── 5. baseline por regras (porte do app) ──────────────────

# Porte fiel do PostureValidator.kt, com uma ressalva: o app também exige
# visibility >= 0.4 nos landmarks, e essa coluna não é exportada para a planilha.
# Aqui a checagem de visibilidade não existe, então o baseline é uma aproximação
# ligeiramente otimista das regras.
LIMIAR_NIVEL_OMBROS = 5.0    # graus
LIMIAR_OMBROS_FRENTE = 0.40  # Zdiff normalizado
SPAN_FRONTAL_MINIMO = 0.15   # só avalia de frente


def regras_postura() -> np.ndarray:
    """Classe prevista pelas regras, no vocabulário da planilha."""
    fora_de_frente = span < SPAN_FRONTAL_MINIMO

    encurvado = (zdiff_norm < -LIMIAR_OMBROS_FRENTE) & ~fora_de_frente
    desnivelado = np.abs(angulo_ombros) > LIMIAR_NIVEL_OMBROS
    esquerdo_caido = desnivelado & (ombro_dy > 0)
    direito_caido = desnivelado & (ombro_dy <= 0)

    # Precedência: ombros à frente vem antes do desnivelamento lateral, por serem
    # eixos distintos. Só afeta a métrica multiclasse; a binária independe disso.
    saida = np.full(len(df), "post_boa", dtype=object)
    saida[esquerdo_caido] = "post_ruim_OE"
    saida[direito_caido] = "post_ruim_OD"
    saida[encurvado] = "post_ruim_OF"
    return saida


y_regras = regras_postura()

print(f"\nLinhas fora da vista frontal (span < {SPAN_FRONTAL_MINIMO}): "
      f"{int((span < SPAN_FRONTAL_MINIMO).sum())} de {len(df)}")

# %% ───────────────────────── 6. avaliação comparada ─────────────────────────


def para_binario(v: np.ndarray) -> np.ndarray:
    return np.where(v == "post_boa", "boa", "ruim")


n_grupos = df["sessao"].nunique()
n_splits = min(5, n_grupos)

resultados = []

if n_grupos < 2:
    print(
        "\n⚠  Só uma sessão foi detectada. Sem grupos distintos não há como validar "
        "generalização — os números abaixo medem memorização, não aprendizado.\n"
        "   Ajuste GAP_MINUTOS ou colete em sessões separadas."
    )
else:
    if n_grupos < 5:
        print(
            f"\n⚠  Apenas {n_grupos} sessões de coleta. A validação abaixo mede se o "
            "modelo\n   atravessa DIAS de gravação — não se atravessa PESSOAS, a menos "
            "que cada\n   sessão tenha sido gravada com um participante diferente. "
            "Trate os números\n   como indício de viabilidade, não como resultado final."
        )

    cv = GroupKFold(n_splits=n_splits)

    # Referência mínima: sempre chutar a classe majoritária.
    trivial = cross_val_predict(
        DummyClassifier(strategy="most_frequent"), X_c.fillna(0), y, cv=cv, groups=grupos
    )
    resultados.append(
        {
            "abordagem": "Trivial (classe majoritária)",
            "acc_multiclasse": accuracy_score(y, trivial),
            "acc_binaria": accuracy_score(para_binario(y), para_binario(trivial)),
        }
    )

    # As regras não treinam, então não passam por CV — o valor é direto.
    resultados.append(
        {
            "abordagem": "Regras geométricas (PostureValidator)",
            "acc_multiclasse": accuracy_score(y, y_regras),
            "acc_binaria": accuracy_score(para_binario(y), para_binario(y_regras)),
        }
    )

    modelos = {
        "Regressão logística": lambda: make_pipeline(
            StandardScaler(), LogisticRegression(max_iter=2000, random_state=RANDOM_STATE)
        ),
        "Random forest": lambda: RandomForestClassifier(
            n_estimators=300, random_state=RANDOM_STATE
        ),
    }

    predicoes = {}
    for nome_conjunto, X in CONJUNTOS.items():
        X_limpo = X.fillna(X.median(numeric_only=True))
        for nome_modelo, construir in modelos.items():
            pred = cross_val_predict(construir(), X_limpo, y, cv=cv, groups=grupos)
            predicoes[(nome_conjunto, nome_modelo)] = pred
            resultados.append(
                {
                    "abordagem": f"{nome_modelo} — {nome_conjunto}",
                    "acc_multiclasse": accuracy_score(y, pred),
                    "acc_binaria": accuracy_score(para_binario(y), para_binario(pred)),
                }
            )

    tabela = pd.DataFrame(resultados).sort_values("acc_binaria", ascending=False)
    print("\n" + "=" * 78)
    print(f"Validação cruzada por sessão (GroupKFold, {n_splits} folds, "
          f"{n_grupos} sessões)")
    print("=" * 78)
    print(tabela.to_string(index=False, float_format=lambda v: f"{v:.3f}"))

# %% ────────────────────── 7. detalhe do melhor modelo ──────────────────────

if n_grupos >= 2:
    melhor = max(predicoes.items(), key=lambda kv: accuracy_score(y, kv[1]))
    (conj, mod), pred = melhor

    print(f"\nMelhor combinação: {mod} — {conj}\n")
    print(classification_report(y, pred, zero_division=0))

    classes = sorted(set(y))
    print("Matriz de confusão (linha = real, coluna = previsto):")
    print(pd.DataFrame(
        confusion_matrix(y, pred, labels=classes), index=classes, columns=classes
    ).to_string())

    print("\nRegras geométricas, para comparação:")
    print(classification_report(y, y_regras, zero_division=0))

# %% ─────────────────────────── 8. como interpretar ───────────────────────────

print(
    """
Leitura dos resultados
──────────────────────
· Compare sempre contra as DUAS referências. Ganhar do trivial é o mínimo;
  o que justifica embarcar um modelo é ganhar das regras geométricas, que já
  estão implementadas e custam quase nada em tempo de execução.

· Se o conjunto C (9 features interpretáveis) empatar com A ou B, prefira C:
  menos parâmetros, menos risco de decorar ruído com poucas amostras, e mais
  fácil de defender na monografia.

· Acurácia alta com poucas sessões engana. Com um número pequeno de grupos,
  cada fold treina em pouca gente — o intervalo de confiança é largo. Trate
  estes números como indicação de viabilidade, não como resultado final.

· O próximo ganho real vem de mais sessões de coleta, não de trocar o modelo.
"""
)
