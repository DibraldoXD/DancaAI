"""
Classificador dos 6 passos de forró — Dança AI (TCC02, atividade A.2.2).

O cronograma pede "acurácia documentada" para o classificador de movimentos.
Este script produz esse número sob a única validação que significa algo aqui:
**leave-one-participant-out** nos 7 participantes da coleta de 04/11/2026.

Por que a validação tem de ser por participante
───────────────────────────────────────────────
Cada participante gravou uma execução de cada passo, a 10 Hz. Duas janelas
vizinhas da mesma gravação são quase a mesma coisa; uma divisão aleatória
colocaria janelas irmãs em treino e teste e mediria interpolação, não
generalização. Pior: deixaria a MESMA PESSOA nos dois lados, e aí o modelo pode
acertar reconhecendo o biotipo em vez do passo. Agrupar por participante resolve
os dois casos de uma vez, porque uma gravação pertence a um participante só.

Duas referências acompanham todo número deste script:

  · chute na classe majoritária — o piso. Abaixo dele o modelo não aprendeu nada.
  · estratégia A, medida aqui — a reprodução do teste exploratório que deu 0,776
    de acurácia por janela de 3 s. Comparar contra ela, medida sob esta mesma
    validação, é o que mostra se as mudanças ajudaram ou se o número só mudou de
    régua.

O problema em aberto
────────────────────
Abertura e giro em linha se confundem mutuamente (f1 0,47 e 0,62 no teste
exploratório, contra 0,77-1,00 nos outros quatro passos). As 3 primeiras pisadas
dos dois passos são praticamente idênticas; o que separa é a rotação continuar
até a 5ª pisada no giro em linha contra a pausa que vem depois da 3ª marcação na
abertura. A seção de auditoria no fim deste script mede diretamente esse par e
inspeciona as 14 gravações envolvidas, porque "o modelo não separa" e "as
gravações não são separáveis" são diagnósticos diferentes e pedem ações
diferentes.

Uso:
    python train.py                      # baixa da planilha
    python train.py data/capturas.csv    # usa um CSV já baixado
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

# O console padrão do Windows usa cp1252, que não tem os sinais tipográficos
# que um relatório tende a acumular. Sem isto, uma linha de aviso derruba o
# script inteiro com UnicodeEncodeError depois de meia hora de treino.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(errors="replace")

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, confusion_matrix, f1_score
from sklearn.model_selection import LeaveOneGroupOut
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

import features
from load_data import NOMES_PASSOS, PASSOS, carregar

RANDOM_STATE = 42
np.random.seed(RANDOM_STATE)
tf.random.set_seed(RANDOM_STATE)

# Semente sozinha não basta: no CPU o TensorFlow paraleliza reduções em ordem
# não determinística, e duas execuções do mesmo script davam 0,886 e 0,898 para
# a mesma arquitetura. Diferença dessa ordem é maior que a diferença entre as
# arquiteturas comparadas, o que tornaria a escolha — e o número documentado no
# README — um sorteio. Custa desempenho e vale: o número tem de ser reproduzível.
try:
    tf.config.experimental.enable_op_determinism()
except Exception as erro:  # versão de TF sem suporte
    print(f"AVISO: determinismo não pôde ser ativado ({erro}). O número vai variar entre execuções.")

MODELOS_DIR = Path(__file__).parent / "models"

# Número do teste exploratório que antecedeu este módulo, para a comparação
# aparecer no relatório mesmo quando a estratégia A der outro valor (a coleta e
# a segmentação de participante mudaram desde então).
ACURACIA_EXPLORATORIA = 0.776

# Estratégia e duração de janela de cada configuração avaliada. Ver o cabeçalho
# de features.py para o que cada letra significa.
CONFIGURACOES = [
    ("A", 3.0),
    ("A", 4.5),
    ("B", 3.0),
    ("B", 4.5),
    ("C", 3.0),
    ("C", 4.5),
    ("D", None),
]


def construir_estimadores() -> dict:
    """Dois modelos de naturezas diferentes, não dois ajustes do mesmo.

    A floresta captura interação entre features sem que ninguém a escreva, o que
    importa quando a feature discriminante é "yaw alto NO 4º sub-intervalo".
    A regressão logística é o controle: se ela empata com a floresta, a
    estrutura que a floresta encontrou não existe ou não transfere entre pessoas.
    """
    return {
        "floresta": RandomForestClassifier(
            n_estimators=300,
            min_samples_leaf=2,
            class_weight="balanced",
            random_state=RANDOM_STATE,
            n_jobs=-1,
        ),
        "linear": make_pipeline(
            StandardScaler(),
            LogisticRegression(
                max_iter=3000, class_weight="balanced", random_state=RANDOM_STATE
            ),
        ),
    }


def prever_lopo(X: pd.DataFrame, meta: pd.DataFrame, estimador) -> np.ndarray:
    """Predição fora da amostra de cada janela, com LOPO por participante.

    Cada janela é prevista por um modelo que nunca viu nenhuma janela daquela
    pessoa — e, por consequência, nenhuma janela daquela gravação.
    """
    y = meta["passo"].to_numpy()
    grupos = meta["participante"].to_numpy()
    predito = np.empty(len(y), dtype=object)

    for treino, teste in LeaveOneGroupOut().split(X, y, grupos):
        modelo = clonar(estimador)
        modelo.fit(X.iloc[treino], y[treino])
        predito[teste] = modelo.predict(X.iloc[teste])
    return predito


def clonar(estimador):
    from sklearn.base import clone

    return clone(estimador)


def voto_por_gravacao(meta: pd.DataFrame, predito: np.ndarray) -> pd.DataFrame:
    """Agrega as janelas de cada gravação por voto majoritário.

    É assim que o app decidiria "que passo é este": não por uma janela isolada,
    mas pelo que a maioria das janelas recentes diz. A acurácia por gravação é
    quase sempre maior que a por janela, e é a que corresponde à experiência de
    uso — vale reportar as duas, porque a por janela é a que mede o modelo.
    """
    tabela = meta.copy()
    tabela["predito"] = predito
    linhas = []
    for gravacao, g in tabela.groupby("gravacao", sort=False):
        linhas.append(
            {
                "gravacao": gravacao,
                "participante": g["participante"].iloc[0],
                "real": g["passo"].iloc[0],
                "votado": g["predito"].mode().iloc[0],
                "janelas": len(g),
                "concordancia": float((g["predito"] == g["passo"].iloc[0]).mean()),
            }
        )
    return pd.DataFrame(linhas)


def imprimir_matriz(real, predito, titulo: str) -> None:
    rotulos = [p for p in PASSOS if p in set(real) | set(predito)]
    matriz = confusion_matrix(real, predito, labels=rotulos)
    tabela = pd.DataFrame(
        matriz,
        index=[NOMES_PASSOS[r] for r in rotulos],
        columns=[NOMES_PASSOS[r][:12] for r in rotulos],
    )
    print(f"\n{titulo} (linha = real, coluna = previsto)")
    print(tabela.to_string())


def imprimir_f1_por_passo(real, predito) -> None:
    f1 = f1_score(real, predito, labels=PASSOS, average=None, zero_division=0)
    print(f"\n{'passo':<26}{'f1':>8}")
    for passo, valor in zip(PASSOS, f1):
        print(f"{NOMES_PASSOS[passo]:<26}{valor:>8.3f}")


def avaliar_configuracoes(mov, sinais) -> tuple[pd.DataFrame, dict]:
    """Varredura: cada configuração × cada modelo, sempre sob LOPO."""
    print("\n" + "=" * 78)
    print("VARREDURA — acurácia por janela, leave-one-participant-out (7 dobras)")
    print("=" * 78)

    resultados, guardadas = [], {}
    for estrategia, duracao in CONFIGURACOES:
        X, meta = features.montar(estrategia, mov, sinais, duracao)
        y = meta["passo"].to_numpy()
        majoritaria = pd.Series(y).value_counts(normalize=True).iloc[0]
        nome_janela = (
            f"{estrategia}@{duracao:.1f}s" if duracao else f"{estrategia}@ciclo"
        )

        for nome_modelo, estimador in construir_estimadores().items():
            predito = prever_lopo(X, meta, estimador)
            chave = f"{nome_janela} · {nome_modelo}"
            guardadas[chave] = (X, meta, predito)
            resultados.append(
                {
                    "configuracao": chave,
                    "features": X.shape[1],
                    "janelas": len(X),
                    "majoritaria": majoritaria,
                    "acuracia": accuracy_score(y, predito),
                    "f1_macro": f1_score(y, predito, average="macro", zero_division=0),
                    "f1_abertura": f1_score(
                        y, predito, labels=["mov_abertura"], average="macro",
                        zero_division=0,
                    ),
                    "f1_giro_linha": f1_score(
                        y, predito, labels=["mov_giro_linha"], average="macro",
                        zero_division=0,
                    ),
                    "acuracia_gravacao": accuracy_score(
                        *voto_por_gravacao(meta, predito)[["real", "votado"]].T.to_numpy()
                    ),
                }
            )
            ultimo = resultados[-1]
            print(
                f"{chave:<28}{ultimo['acuracia']:>8.3f}  f1m {ultimo['f1_macro']:.3f}  "
                f"grav {ultimo['acuracia_gravacao']:.3f}  "
                f"(abertura {ultimo['f1_abertura']:.2f} / giro linha "
                f"{ultimo['f1_giro_linha']:.2f})"
            )

    tabela = pd.DataFrame(resultados).sort_values("acuracia", ascending=False)
    print("\n" + "=" * 78)
    print("RESUMO ORDENADO POR ACURÁCIA FORA DA AMOSTRA")
    print("=" * 78)
    print(
        tabela.to_string(
            index=False,
            columns=[
                "configuracao", "features", "janelas", "majoritaria",
                "acuracia", "f1_macro", "acuracia_gravacao",
            ],
            float_format=lambda v: f"{v:.3f}",
        )
    )
    print(
        f"\nReferências: chute na classe majoritária = {tabela['majoritaria'].min():.3f}"
        f"  |  teste exploratório anterior a este módulo = {ACURACIA_EXPLORATORIA:.3f}"
    )
    return tabela, guardadas


def acuracia_por_participante(meta: pd.DataFrame, predito: np.ndarray) -> pd.DataFrame:
    """Acurácia de cada dobra do LOPO, separadamente.

    A média de 7 dobras esconde o caso em que o modelo vai bem em 6 pessoas e
    falha numa. Com 7 participantes, uma dobra ruim é 14% do resultado e muda a
    leitura: "funciona" e "funciona para quem dança parecido com o treino" são
    conclusões diferentes, e só a quebra por dobra distingue as duas.
    """
    tabela = meta.copy()
    tabela["predito"] = predito
    linhas = []
    for participante, g in tabela.groupby("participante"):
        linhas.append(
            {
                "participante": participante,
                "janelas": len(g),
                "acuracia": accuracy_score(g["passo"], g["predito"]),
                "f1_macro": f1_score(
                    g["passo"], g["predito"], average="macro", zero_division=0
                ),
            }
        )
    return pd.DataFrame(linhas)


def controle_de_calibracao(mov, duracao: float = 3.0) -> None:
    """Mede quanto do resultado depende da calibração do yaw por gravação.

    A calibração usa o percentil 95 de |dx| da gravação inteira, incluindo os
    quadros que estão do lado do teste na validação. Não é vazamento de rótulo,
    mas é informação da gravação de teste entrando na feature. Se o resultado
    cair muito sem ela, o número da varredura está otimista e o que vale é este.
    """
    print("\n" + "=" * 78)
    print("CONTROLE — o resultado depende da calibração do yaw por gravação?")
    print("=" * 78)

    for calibrar in (True, False):
        sinais = features.adicionar_yaw_e_velocidades(
            features.sinais_por_quadro(mov), calibrar=calibrar
        )
        X, meta = features.montar("B", mov, sinais, duracao)
        predito = prever_lopo(X, meta, construir_estimadores()["floresta"])
        real = meta["passo"].to_numpy()
        votos = voto_por_gravacao(meta, predito)
        etiqueta = "com calibração" if calibrar else "SEM calibração (sinal cru)"
        print(
            f"B@{duracao:.1f}s floresta, {etiqueta:<28}"
            f"janela {accuracy_score(real, predito):.3f}  "
            f"f1m {f1_score(real, predito, average='macro', zero_division=0):.3f}  "
            f"gravação {accuracy_score(votos['real'], votos['votado']):.3f}"
        )
    print(
        "\nSe as duas linhas ficarem próximas, a calibração é conveniência e não\n"
        "sustenta o resultado. Se a versão sem calibração cair muito, é ela que deve\n"
        "ser citada como número do módulo, porque é a que não olha a gravação de teste."
    )


def auditar_par_dificil(mov, sinais, guardadas: dict, melhor: str) -> None:
    """Mede abertura × giro em linha isoladamente e inspeciona as gravações.

    Dois diagnósticos diferentes se escondem num f1 baixo:

      · o modelo não separa os dois passos, mas as execuções SÃO distintas —
        então falta feature, modelo ou participante;
      · as execuções gravadas não são distintas — então o rótulo está otimista,
        e nenhum modelo vai acertar o que a gravação não registrou.

    O classificador binário responde o primeiro; a tabela por gravação dá ao
    autor o material para julgar o segundo, que é questão de domínio e de vídeo,
    não de estatística.
    """
    print("\n" + "=" * 78)
    print("AUDITORIA — abertura × giro em linha")
    print("=" * 78)

    X, meta, predito = guardadas[melhor]
    par = meta["passo"].isin(["mov_abertura", "mov_giro_linha"])
    real_par = meta.loc[par, "passo"].to_numpy()
    print(
        f"Na melhor configuração ({melhor}), das {int(par.sum())} janelas dos dois "
        f"passos, {accuracy_score(real_par, predito[par]):.3f} ficaram com o rótulo certo."
    )

    # Classificador binário: tira os outros 4 passos do caminho. Se mesmo a
    # dois acertar pouco, o problema não é confusão com um terceiro passo.
    Xb, yb = X[par.to_numpy()], real_par
    grupos = meta.loc[par, "participante"].to_numpy()
    acertos = []
    for treino, teste in LeaveOneGroupOut().split(Xb, yb, grupos):
        modelo = clonar(construir_estimadores()["floresta"])
        modelo.fit(Xb.iloc[treino], yb[treino])
        acertos.append(
            (grupos[teste][0], accuracy_score(yb[teste], modelo.predict(Xb.iloc[teste])))
        )
    binaria = np.mean([a for _, a in acertos])
    print(
        f"Classificador binário só dos dois passos, LOPO: {binaria:.3f} "
        f"(chute = 0,500). Por participante: "
        + ", ".join(f"{p} {a:.2f}" for p, a in acertos)
    )

    # Descritores de domínio por gravação: quanto roda, quantas vezes inverte, e
    # se o movimento desacelera no fim (a pausa da abertura).
    periodos = features.periodos_por_gravacao(sinais)
    linhas = []
    for gravacao, g in sinais.groupby("gravacao", sort=False):
        if g["passo"].iloc[0] not in ("mov_abertura", "mov_giro_linha"):
            continue
        g = g.sort_values("t_s")
        yaw = g["yaw"].to_numpy()
        tempos = g["t_s"].to_numpy()
        duracao = max(tempos[-1] - tempos[0], 1e-3)
        velocidade = np.abs(g["v_yaw"].to_numpy())
        terco = max(len(g) // 3, 1)
        linhas.append(
            {
                "gravacao": gravacao,
                "passo": g["passo"].iloc[0],
                "dur_s": duracao,
                "ciclo_s": periodos.get(gravacao, np.nan),
                "giro_total_rad": float(np.abs(np.diff(yaw)).sum()),
                "giro_por_s": float(np.abs(np.diff(yaw)).sum() / duracao),
                "inversoes_por_s": float(
                    np.sum(np.diff(np.sign(yaw)) != 0) / duracao
                ),
                "v_inicio": float(velocidade[:terco].mean()),
                "v_fim": float(velocidade[-terco:].mean()),
                "fracao_parado": float(np.mean(velocidade < 0.5)),
            }
        )
    tabela = pd.DataFrame(linhas).sort_values(["passo", "gravacao"])
    print("\nDescritores de domínio por gravação:")
    print(tabela.to_string(index=False, float_format=lambda v: f"{v:.2f}"))
    print("\nMédia por passo:")
    print(
        tabela.groupby("passo")
        .mean(numeric_only=True)
        .to_string(float_format=lambda v: f"{v:.2f}")
    )
    print(
        "\nLeitura: `giro_por_s` e `inversoes_por_s` mais altos no giro em linha é o\n"
        "esperado — 5 tempos de rotação contra 3 marcações e pausa. `v_fim` menor que\n"
        "`v_inicio` na abertura é a pausa aparecendo. Uma gravação de giro em linha com\n"
        "números de abertura é candidata a rótulo otimista e vale rever no vídeo."
    )


# ──────────────────────── modelo final e exportação ────────────────────────


def construir_keras(n_features: int, X_ajuste: np.ndarray, arquitetura: str) -> tf.keras.Model:
    """Modelo multiclasse com a normalização DENTRO do grafo.

    Embutir a normalização significa que o TFLite recebe as features cruas e o
    Kotlin não replica média e desvio à mão — uma fonte de erro silencioso que
    só aparece como "o modelo erra no celular e acerta no notebook".

    `linear` é regressão logística escrita como rede de uma camada. Com ~800
    janelas vindas de 7 pessoas, uma MLP larga tem mais parâmetros que exemplos
    independentes e decora os participantes do treino; a versão linear converte
    para TFLite igualmente bem e é o controle honesto.
    """
    normalizacao = tf.keras.layers.Normalization()
    normalizacao.adapt(X_ajuste)
    camadas = [tf.keras.layers.Input(shape=(n_features,)), normalizacao]
    if arquitetura == "mlp":
        camadas += [
            tf.keras.layers.Dense(
                24, activation="relu", kernel_regularizer=tf.keras.regularizers.l2(1e-3)
            ),
            tf.keras.layers.Dropout(0.3),
        ]
    elif arquitetura == "mlp_largo":
        # A floresta ganha do modelo linear por capturar interação entre
        # features ("yaw alto NO 4º sub-intervalo E pé fechado no 5º"). Uma
        # camada oculta larga é a forma de dar essa capacidade a um modelo que
        # converte para TFLite — árvore não converte sem op custom.
        camadas += [
            tf.keras.layers.Dense(
                64, activation="relu", kernel_regularizer=tf.keras.regularizers.l2(1e-4)
            ),
            tf.keras.layers.Dropout(0.2),
            tf.keras.layers.Dense(
                32, activation="relu", kernel_regularizer=tf.keras.regularizers.l2(1e-4)
            ),
            tf.keras.layers.Dropout(0.2),
        ]
    camadas.append(
        tf.keras.layers.Dense(
            len(PASSOS),
            activation="softmax",
            kernel_regularizer=tf.keras.regularizers.l2(1e-3),
        )
    )
    modelo = tf.keras.Sequential(camadas)
    modelo.compile(
        optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"]
    )
    return modelo


def treinar_keras(modelo: tf.keras.Model, X: np.ndarray, y: np.ndarray) -> None:
    """Treina embaralhando antes da validação interna, com pesos por classe.

    O `validation_split` do Keras separa os ÚLTIMOS exemplos na ordem recebida,
    sem embaralhar. As janelas chegam ordenadas por gravação, então a validação
    cairia inteira sobre um ou dois passos e o early stopping pararia num ponto
    arbitrário.

    Os pesos por classe não são ajuste fino: o número de janelas por passo
    depende da duração que cada participante levou gravando (balanço tem 98
    janelas, giro em linha 177, porque as gravações de giro são mais longas).
    Sem compensar, o modelo aprende essa diferença de duração junto com o passo.
    Os modelos do scikit-learn na varredura usam `class_weight="balanced"`, que
    é a mesma correção — aplicá-la aqui também é o que torna a comparação justa.
    """
    ordem = np.random.RandomState(RANDOM_STATE).permutation(len(X))
    contagem = np.bincount(y, minlength=len(PASSOS)).astype(float)
    pesos = {
        i: float(len(y) / (len(PASSOS) * c)) if c > 0 else 0.0
        for i, c in enumerate(contagem)
    }
    modelo.fit(
        X[ordem],
        y[ordem],
        epochs=300,
        batch_size=32,
        verbose=0,
        validation_split=0.2,
        class_weight=pesos,
        callbacks=[
            tf.keras.callbacks.EarlyStopping(
                patience=40, restore_best_weights=True, monitor="val_loss"
            )
        ],
    )


def avaliar_keras_lopo(X: pd.DataFrame, meta: pd.DataFrame, arquitetura: str) -> np.ndarray:
    """LOPO com o modelo Keras, para a escolha final não sair de outra régua."""
    Xv = X.to_numpy(dtype="float32")
    indice = {p: i for i, p in enumerate(PASSOS)}
    y = meta["passo"].map(indice).to_numpy()
    grupos = meta["participante"].to_numpy()
    predito = np.empty(len(y), dtype=object)

    for treino, teste in LeaveOneGroupOut().split(Xv, y, grupos):
        modelo = construir_keras(Xv.shape[1], Xv[treino], arquitetura)
        treinar_keras(modelo, Xv[treino], y[treino])
        indices = modelo.predict(Xv[teste], verbose=0).argmax(axis=1)
        predito[teste] = [PASSOS[i] for i in indices]
    return predito


def main(origem: str | None = None) -> None:
    mov, _ = carregar(origem)
    sinais = features.adicionar_yaw_e_velocidades(features.sinais_por_quadro(mov))

    periodos = features.periodos_por_gravacao(sinais)
    print("\nCiclo estimado por passo (s) — o BPM não está no dataset, ver features.py:")
    print(
        periodos.to_frame()
        .join(mov.groupby("gravacao")["passo"].first())
        .groupby("passo")["periodo_s"]
        .agg(["mean", "min", "max"])
        .to_string(float_format=lambda v: f"{v:.2f}")
    )

    tabela, guardadas = avaliar_configuracoes(mov, sinais)
    melhor = tabela.iloc[0]["configuracao"]

    X, meta, predito = guardadas[melhor]
    y = meta["passo"].to_numpy()
    print("\n" + "=" * 78)
    print(f"DETALHE DA MELHOR CONFIGURAÇÃO — {melhor}")
    print("=" * 78)
    imprimir_f1_por_passo(y, predito)
    imprimir_matriz(y, predito, "Matriz de confusão por janela")

    por_participante = acuracia_por_participante(meta, predito)
    print("\nAcurácia de cada dobra do LOPO (uma por participante):")
    print(por_participante.to_string(index=False, float_format=lambda v: f"{v:.3f}"))
    print(
        f"pior dobra {por_participante['acuracia'].min():.3f}  |  "
        f"melhor dobra {por_participante['acuracia'].max():.3f}  |  "
        f"desvio entre dobras {por_participante['acuracia'].std():.3f}"
    )

    votos = voto_por_gravacao(meta, predito)
    print(
        f"\nVoto majoritário por gravação: "
        f"{accuracy_score(votos['real'], votos['votado']):.3f} "
        f"({int((votos['real'] == votos['votado']).sum())} de {len(votos)} gravações)"
    )
    imprimir_matriz(votos["real"], votos["votado"], "Matriz de confusão por gravação")
    erradas = votos[votos["real"] != votos["votado"]]
    if not erradas.empty:
        print("\nGravações erradas no voto majoritário:")
        print(erradas.to_string(index=False, float_format=lambda v: f"{v:.2f}"))

    auditar_par_dificil(mov, sinais, guardadas, melhor)
    controle_de_calibracao(mov)

    # ─── modelo final: a configuração vencedora, agora em Keras ───
    estrategia, janela = melhor.split(" · ")[0].split("@")
    duracao = None if janela == "ciclo" else float(janela.rstrip("s"))
    X_final, meta_final = features.montar(estrategia, mov, sinais, duracao)

    print("\n" + "=" * 78)
    print(f"MODELO EXPORTÁVEL — features de {estrategia}@{janela}, validação LOPO")
    print("=" * 78)
    # Critério de escolha: f1 macro por janela, com a acurácia por gravação como
    # desempate.
    #
    # Não é acurácia por janela. O número de janelas por passo depende da duração
    # que cada participante levou gravando, então a acurácia simples premia
    # acertar o passo que foi gravado por mais tempo — f1 macro pesa os 6 passos
    # igual. E o desempate é por gravação porque é essa a decisão do app: duas
    # arquiteturas empataram em 0,886 por janela numa execução, com 0,837 e 0,907
    # por gravação, e escolher pela janela teria levado a pior das duas.
    placar, predicoes, acuracias = {}, {}, {}
    # Conjunto de arquiteturas FIXADO de antemão, não ampliado até alguma passar.
    # Escolher a melhor pelas mesmas dobras que a medem é viés de seleção; o
    # aviso no fim desta função registra isso.
    for arquitetura in ("linear", "mlp", "mlp_largo"):
        predito_keras = avaliar_keras_lopo(X_final, meta_final, arquitetura)
        real = meta_final["passo"].to_numpy()
        votos_keras = voto_por_gravacao(meta_final, predito_keras)
        f1_macro = f1_score(real, predito_keras, average="macro", zero_division=0)
        acuracia_gravacao = accuracy_score(votos_keras["real"], votos_keras["votado"])
        placar[arquitetura] = (f1_macro, acuracia_gravacao)
        predicoes[arquitetura] = predito_keras
        acuracias[arquitetura] = accuracy_score(real, predito_keras)
        print(
            f"keras/{arquitetura:<11}janela {accuracy_score(real, predito_keras):.3f}  "
            f"f1m {f1_macro:.3f}  gravação {acuracia_gravacao:.3f}"
        )

    arquitetura_final = max(placar, key=placar.get)
    melhor_predito = predicoes[arquitetura_final]
    print(
        f"\nArquitetura escolhida para exportar: keras/{arquitetura_final}"
        f"\n(escolha feita entre {len(placar)} arquiteturas fixadas de antemão, pelas "
        "mesmas dobras\nque a medem — há viés de seleção, da ordem da diferença entre "
        "elas.)"
    )
    imprimir_f1_por_passo(meta_final["passo"].to_numpy(), melhor_predito)
    imprimir_matriz(
        meta_final["passo"].to_numpy(),
        melhor_predito,
        f"Matriz de confusão — keras/{arquitetura_final}",
    )
    print("\nAcurácia de cada dobra do LOPO no modelo exportável:")
    print(
        acuracia_por_participante(meta_final, melhor_predito).to_string(
            index=False, float_format=lambda v: f"{v:.3f}"
        )
    )

    if acuracias[arquitetura_final] < tabela.iloc[0]["acuracia"] - 0.02:
        print(
            f"\nAVISO: A floresta/linear do scikit-learn ({tabela.iloc[0]['acuracia']:.3f}) "
            f"supera o modelo Keras exportável ({acuracias[arquitetura_final]:.3f}).\n"
            "   O TFLite é o que embarca, então é o número do TFLite que vale para o\n"
            "   app; o do scikit-learn fica como teto do que estas features permitem."
        )

    Xv = X_final.to_numpy(dtype="float32")
    indice = {p: i for i, p in enumerate(PASSOS)}
    modelo = construir_keras(Xv.shape[1], Xv, arquitetura_final)
    treinar_keras(modelo, Xv, meta_final["passo"].map(indice).to_numpy())

    MODELOS_DIR.mkdir(exist_ok=True)
    caminho_modelo = MODELOS_DIR / "movement_classifier.keras"
    modelo.save(caminho_modelo)
    (MODELOS_DIR / "movement_classifier_meta.json").write_text(
        json.dumps(
            {
                "estrategia": estrategia,
                "janela": janela,
                "arquitetura": arquitetura_final,
                "features": list(X_final.columns),
                "classes": PASSOS,
                "acuracia_lopo_janela": float(acuracias[arquitetura_final]),
                "f1_macro_lopo_janela": float(placar[arquitetura_final][0]),
                "acuracia_lopo_gravacao": float(
                    accuracy_score(
                        *voto_por_gravacao(meta_final, melhor_predito)[
                            ["real", "votado"]
                        ].T.to_numpy()
                    )
                ),
                "participantes": sorted(meta_final["participante"].unique().tolist()),
                "gravacoes": int(meta_final["gravacao"].nunique()),
            },
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    print(f"\nModelo salvo em {caminho_modelo}")
    print("Metadados (ordem das features e classes) em movement_classifier_meta.json")
    print("Próximo passo: python export_tflite.py")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else None)
