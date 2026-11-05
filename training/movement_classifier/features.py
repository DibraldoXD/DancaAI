"""
Sinais por quadro e janelamento — Dança AI (TCC02, atividade A.2.2).

Este arquivo concentra a decisão que define o problema: **o que é uma amostra
para o classificador de passos**. Um passo de forró não se distingue de outro
por uma pose; se distingue pela trajetória e pela posição dela no compasso. Logo
a amostra é uma janela de tempo, e a escolha da janela e das features é o
experimento, não um detalhe.

O ponto difícil, informado pelo autor do TCC: **as 3 primeiras pisadas do giro
em linha são praticamente idênticas às 3 pisadas da abertura.** O que separa os
dois é que o giro em linha tem 5 pisadas e a rotação CONTINUA depois da 3ª, até
fechar as 5, enquanto a abertura tem 3 marcações e logo depois vem a PAUSA.
Então o discriminador é temporal e relativo ao compasso — e features de média e
desvio por janela de duração fixa descartam exatamente essa informação.

Daí as quatro estratégias aqui, para serem medidas e não escolhidas por gosto:

    A  reprodução do baseline: janela fixa, estatísticas sobre coordenadas
       normalizadas pelo SPAN DE OMBROS (o que o teste exploratório fez).
    B  mesma janela e mesmas estatísticas, sobre os sinais normalizados pelo
       TRONCO (ver `sinais_por_quadro`). Isola o efeito da normalização.
    C  ordem temporal preservada: a janela é fatiada em sub-intervalos e cada um
       entra com as próprias estatísticas, na ordem.
    D  janela de um ciclo do movimento, estimado do próprio sinal, reamostrada
       para comprimento fixo. Absorve a diferença de andamento entre pessoas.

Por que o span de ombros é a normalização errada para estes passos
──────────────────────────────────────────────────────────────────
O span de ombros projetado na imagem tende a ZERO quando o dançarino fica de
perfil. Dividir coordenadas por ele explode as features exatamente nos quadros
de rotação — que são os quadros que distinguem abertura de giro em linha. O
comprimento do tronco (quadril → ombro) não tem esse problema: ele é
aproximadamente constante sob rotação em torno do eixo vertical. Essa é a
hipótese mecânica que a comparação A × B mede.
"""

from __future__ import annotations

import numpy as np
import pandas as pd

from load_data import LANDMARKS

# Comprimentos de janela avaliados, em segundos. 3,0 s é o valor do teste
# exploratório (0,776 de acurácia), mantido para a comparação ser direta.
DURACOES_JANELA_S = (3.0, 4.5)

# Passo entre janelas. Janelas sobrepostas da mesma gravação NÃO são exemplos
# independentes; o agrupamento por participante na validação é o que impede que
# isso vire vazamento. O passo curto existe para dar volume de treino, não para
# inflar o número de exemplos "distintos".
HOP_S = 0.5

# Fração mínima de amostras esperadas numa janela. Com buracos de amostragem
# (pessoa saindo do enquadramento) uma janela pode ficar quase vazia, e as
# estatísticas dela seriam ruído com cara de feature.
OCUPACAO_MINIMA = 0.6

TAXA_NOMINAL_HZ = 10.0

# Faixa de busca do ciclo do movimento (estratégia D), em segundos.
#
# O metrônomo do app vai de 60 a 200 BPM. Um ciclo de 4 a 5 tempos leva de
# 4×60/200 = 1,2 s a 5×60/60 = 5,0 s. Buscar fora dessa faixa é procurar um
# ciclo que o app não consegue pedir.
CICLO_MIN_S = 1.2
CICLO_MAX_S = 5.0

# Pontos por ciclo depois da reamostragem (estratégia D). 12 pontos dão ~2 por
# tempo num ciclo de 5 tempos — o bastante para registrar onde a rotação para,
# sem virar uma feature por quadro.
PONTOS_POR_CICLO = 12

# Sub-intervalos da estratégia C. 5 espelha os 5 tempos do giro em linha: cada
# sub-intervalo cobre aproximadamente um tempo quando a janela cobre um ciclo.
SUBINTERVALOS = 5


def _col(mov: pd.DataFrame, marco: str) -> np.ndarray:
    return mov[[f"{marco}_x", f"{marco}_y", f"{marco}_z"]].to_numpy(dtype=float)


def sinais_por_quadro(mov: pd.DataFrame) -> pd.DataFrame:
    """Converte coordenadas cruas em grandezas interpretáveis, por quadro.

    Todas as razões são divididas pelo comprimento do tronco, que é invariante à
    distância da câmera, à estatura e — ao contrário do span de ombros — à
    rotação do corpo em torno do eixo vertical.

    O sinal central é `yaw`: o ângulo de rotação do tronco, COM SINAL. Tomar o
    módulo (como faz uma razão span/tronco) dobra a curva no zero e transforma
    uma rotação suave numa oscilação de aparência aleatória. Medido na coleta de
    04/11, a curva assinada de uma abertura a 10 Hz é
    `+0,12 +0,52 +0,85 +0,89 +0,73 +0,07 -0,32 -0,80 -0,90 ...` — contínua e
    física; a mesma curva em módulo salta meio intervalo entre quadros vizinhos.
    """
    ombro_e, ombro_d = _col(mov, "OMB-E"), _col(mov, "OMB-D")
    quadril_e, quadril_d = _col(mov, "QDR-E"), _col(mov, "QDR-D")
    tornozelo_e, tornozelo_d = _col(mov, "TRN-E"), _col(mov, "TRN-D")
    joelho_e, joelho_d = _col(mov, "JOE-E"), _col(mov, "JOE-D")

    ombro_meio = (ombro_e + ombro_d) / 2.0
    quadril_meio = (quadril_e + quadril_d) / 2.0
    pes_meio = (tornozelo_e + tornozelo_d) / 2.0

    tronco = np.linalg.norm(ombro_meio[:, :2] - quadril_meio[:, :2], axis=1)
    tronco = np.where(tronco < 1e-6, np.nan, tronco)

    # Deslocamento horizontal assinado entre ombros: positivo e negativo são
    # lados opostos, e a passagem pelo zero é o perfil.
    dx_ombros = (ombro_d[:, 0] - ombro_e[:, 0]) / tronco
    dx_quadril = (quadril_d[:, 0] - quadril_e[:, 0]) / tronco

    sinais = pd.DataFrame(
        {
            "dx_ombros": dx_ombros,
            "dx_quadril": dx_quadril,
            "abertura_pes": np.linalg.norm(
                tornozelo_e[:, :2] - tornozelo_d[:, :2], axis=1
            )
            / tronco,
            "pes_dx": (tornozelo_d[:, 0] - tornozelo_e[:, 0]) / tronco,
            "pes_dy": (tornozelo_e[:, 1] - tornozelo_d[:, 1]) / tronco,
            # Transferência de peso: deslocamento lateral do quadril em relação
            # ao ponto médio dos pés. É a mesma grandeza que o módulo de
            # transferência de peso do app observa.
            "peso_lateral": (quadril_meio[:, 0] - pes_meio[:, 0]) / tronco,
            # y cresce para baixo nas coordenadas normalizadas do MediaPipe,
            # então isto é positivo e mede a altura do quadril sobre os pés.
            "altura_quadril": (pes_meio[:, 1] - quadril_meio[:, 1]) / tronco,
            "joelho_dy": (joelho_e[:, 1] - joelho_d[:, 1]) / tronco,
            "ombro_dy": (ombro_e[:, 1] - ombro_d[:, 1]) / tronco,
        },
        index=mov.index,
    )
    sinais["participante"] = mov["participante"].to_numpy()
    sinais["passo"] = mov["passo"].to_numpy()
    sinais["gravacao"] = mov["gravacao"].to_numpy()
    sinais["t_s"] = mov["t_s"].to_numpy()
    return sinais


def adicionar_yaw_e_velocidades(
    sinais: pd.DataFrame, calibrar: bool = True
) -> pd.DataFrame:
    """Calibra o yaw em radianos e acrescenta as velocidades, por gravação.

    `dx_ombros / tronco` é proporcional ao cosseno do ângulo de rotação, não ao
    ângulo. Dividir pela amplitude frontal da própria pessoa e aplicar `arcsin`
    devolve um ângulo em radianos, comparável entre participantes de biotipo
    diferente — a razão ombro/tronco varia de 0,80 a 0,95 entre as 7 pessoas da
    coleta, variação que entraria como "sinal" se ficasse crua.

    A referência é o percentil 95 de |dx| DENTRO da gravação. Isso é
    calibração não supervisionada (não olha o rótulo), mas usa a gravação
    inteira para normalizar cada quadro dela — inclusive quadros que, na
    validação, estão do lado do teste. Não é vazamento de rótulo, e no app o
    equivalente existe (a referência frontal sai do enquadramento inicial), mas
    é dependência que precisa ser medida em vez de assumida: `calibrar=False`
    devolve os sinais crus, e train.py reporta as duas versões lado a lado.

    As velocidades usam o tempo real entre amostras, não o índice: com buracos
    de amostragem, derivar pelo índice subestima a velocidade exatamente nos
    quadros em que a pessoa se move rápido o bastante para sair de quadro.
    """
    partes = []
    for _, g in sinais.groupby("gravacao", sort=False):
        g = g.sort_values("t_s").copy()

        if calibrar:
            referencia = np.nanpercentile(np.abs(g["dx_ombros"].to_numpy()), 95)
            referencia = max(float(referencia), 1e-6)
            g["yaw"] = np.arcsin(np.clip(g["dx_ombros"] / referencia, -1.0, 1.0))

            ref_quadril = max(float(np.nanpercentile(np.abs(g["dx_quadril"]), 95)), 1e-6)
            g["yaw_quadril"] = np.arcsin(
                np.clip(g["dx_quadril"] / ref_quadril, -1.0, 1.0)
            )
        else:
            # Sem calibração: a razão crua, que nenhuma estatística da gravação
            # tocou. Mantém o sinal e a suavidade, perde a comparabilidade de
            # amplitude entre biotipos.
            g["yaw"] = g["dx_ombros"]
            g["yaw_quadril"] = g["dx_quadril"]

        dt = g["t_s"].diff()
        dt = dt.where(dt > 1e-3)
        for coluna in ("yaw", "abertura_pes", "peso_lateral", "altura_quadril"):
            g[f"v_{coluna}"] = (g[coluna].diff() / dt).bfill().fillna(0.0)
        partes.append(g)

    return pd.concat(partes).sort_index()


# Sinais usados pelas estratégias B, C e D. A ordem é fixa porque ela define a
# ordem das features exportadas para o TFLite.
SINAIS_CORPO = [
    "yaw",
    "yaw_quadril",
    "abertura_pes",
    "pes_dx",
    "pes_dy",
    "peso_lateral",
    "altura_quadril",
    "joelho_dy",
    "ombro_dy",
    "v_yaw",
    "v_abertura_pes",
    "v_peso_lateral",
    "v_altura_quadril",
]


def coordenadas_normalizadas_por_span(mov: pd.DataFrame) -> pd.DataFrame:
    """Coordenadas no referencial do corpo, escaladas pelo SPAN DE OMBROS.

    É a normalização do teste exploratório, reproduzida aqui para a estratégia A.
    Ela é a comparação, não a recomendação: o span colapsa sob rotação, e é isso
    que a comparação A × B quantifica.
    """
    ombro_e, ombro_d = _col(mov, "OMB-E"), _col(mov, "OMB-D")
    quadril_centro = (_col(mov, "QDR-E") + _col(mov, "QDR-D")) / 2.0
    span = np.linalg.norm(ombro_e[:, :2] - ombro_d[:, :2], axis=1)
    span = np.where(span < 1e-6, np.nan, span)

    dados = {}
    for marco in LANDMARKS:
        v = (_col(mov, marco) - quadril_centro) / span[:, None]
        for eixo, i in (("x", 0), ("y", 1), ("z", 2)):
            dados[f"{marco}_{eixo}n"] = v[:, i]
    saida = pd.DataFrame(dados, index=mov.index)
    for coluna in ("participante", "passo", "gravacao", "t_s"):
        saida[coluna] = mov[coluna].to_numpy()
    return saida


# ──────────────────────────── estimativa do ciclo ────────────────────────────


def estimar_periodo(tempos: np.ndarray, matriz: np.ndarray) -> float:
    """Estima a duração de um ciclo do movimento por autocorrelação.

    O BPM da sessão NÃO está no dataset: o contrato de 38 colunas da planilha
    não tem coluna de BPM, e o sufixo `|r<n>s<i>t<ms>` do rótulo não carrega
    tempo de compasso (só as capturas contínuas `|c<ciclo>t<tempo>` carregam, e
    nenhuma gravação de movimento é dessas). Então o ciclo tem de vir do próprio
    sinal. Para coletas futuras, gravar o BPM no rótulo troca esta estimativa
    por um valor conhecido.

    A autocorrelação é normalizada pelo número de pares de cada defasagem. Sem
    essa divisão a soma encurta junto com a sobreposição e o máximo cai sempre
    na menor defasagem possível — o resultado seria a suavidade do sinal, não a
    periodicidade dele.
    """
    grade = np.arange(tempos[0], tempos[-1], 1.0 / TAXA_NOMINAL_HZ)
    if len(grade) < 2 * int(CICLO_MIN_S * TAXA_NOMINAL_HZ):
        return float("nan")

    pontuacao = np.zeros(len(grade))
    usados = 0
    for coluna in range(matriz.shape[1]):
        serie = np.interp(grade, tempos, matriz[:, coluna])
        serie = serie - serie.mean()
        desvio = serie.std()
        if desvio < 1e-9:
            continue
        serie /= desvio
        n = len(serie)
        correlacao = np.array(
            [np.dot(serie[: n - k], serie[k:]) / (n - k) for k in range(n)]
        )
        pontuacao += correlacao
        usados += 1
    if usados == 0:
        return float("nan")

    lag_min = int(CICLO_MIN_S * TAXA_NOMINAL_HZ)
    lag_max = min(int(CICLO_MAX_S * TAXA_NOMINAL_HZ), len(grade) - lag_min)
    if lag_max <= lag_min:
        return float("nan")
    melhor = lag_min + int(np.argmax(pontuacao[lag_min:lag_max]))
    return melhor / TAXA_NOMINAL_HZ


def periodos_por_gravacao(sinais: pd.DataFrame) -> pd.Series:
    """Duração do ciclo de cada gravação, estimada dos sinais de pisada e giro."""
    base = ["abertura_pes", "peso_lateral", "yaw", "altura_quadril"]
    periodos = {}
    for gravacao, g in sinais.groupby("gravacao", sort=False):
        g = g.sort_values("t_s")
        periodos[gravacao] = estimar_periodo(
            g["t_s"].to_numpy(), g[base].to_numpy(dtype=float)
        )
    return pd.Series(periodos, name="periodo_s")


# ──────────────────────────────── janelamento ────────────────────────────────


def _blocos(tabela: pd.DataFrame, duracoes: dict[str, float] | float, hop_s: float):
    """Gera `(metadados, bloco)` para cada janela válida de cada gravação.

    `duracoes` aceita um valor fixo ou um dicionário gravação → duração, que é
    como a estratégia D passa a duração do ciclo estimado.
    """
    for gravacao, g in tabela.groupby("gravacao", sort=False):
        g = g.sort_values("t_s")
        tempos = g["t_s"].to_numpy()
        duracao = duracoes if isinstance(duracoes, float) else duracoes.get(gravacao)
        if duracao is None or not np.isfinite(duracao):
            continue

        minimo = OCUPACAO_MINIMA * duracao * TAXA_NOMINAL_HZ
        inicio = tempos[0]
        while inicio + duracao <= tempos[-1] + 1e-9:
            dentro = (tempos >= inicio) & (tempos < inicio + duracao)
            if dentro.sum() >= max(minimo, 4):
                bloco = g[dentro]
                yield (
                    {
                        "gravacao": gravacao,
                        "participante": bloco["participante"].iloc[0],
                        "passo": bloco["passo"].iloc[0],
                        "inicio_s": inicio,
                        "duracao_s": duracao,
                    },
                    bloco,
                )
            inicio += hop_s


def _estatisticas(bloco: pd.DataFrame, colunas: list[str]) -> dict[str, float]:
    """Média, desvio, amplitude e velocidade média — o agregado do baseline."""
    saida = {}
    tempos = bloco["t_s"].to_numpy()
    for coluna in colunas:
        v = bloco[coluna].to_numpy(dtype=float)
        v = v[np.isfinite(v)]
        if len(v) == 0:
            saida[f"{coluna}_media"] = saida[f"{coluna}_desvio"] = 0.0
            saida[f"{coluna}_amplitude"] = saida[f"{coluna}_velocidade"] = 0.0
            continue
        saida[f"{coluna}_media"] = float(v.mean())
        saida[f"{coluna}_desvio"] = float(v.std())
        saida[f"{coluna}_amplitude"] = float(v.max() - v.min())
        duracao = max(tempos[-1] - tempos[0], 1e-3)
        saida[f"{coluna}_velocidade"] = float(np.abs(np.diff(v)).sum() / duracao)
    return saida


def _ordenado(bloco: pd.DataFrame, colunas: list[str], k: int) -> dict[str, float]:
    """Estatísticas por sub-intervalo, concatenadas NA ORDEM do tempo.

    É aqui que "a rotação continua depois da 3ª pisada" pode virar feature: o
    yaw do 4º e do 5º sub-intervalo é alto no giro em linha e baixo na abertura,
    ainda que a média da janela inteira seja parecida nos dois.
    """
    tempos = bloco["t_s"].to_numpy()
    inicio, fim = tempos[0], tempos[-1]
    bordas = np.linspace(inicio, fim + 1e-9, k + 1)
    saida = {}
    for i in range(k):
        dentro = (tempos >= bordas[i]) & (tempos <= bordas[i + 1])
        fatia = bloco[dentro]
        for coluna in colunas:
            v = fatia[coluna].to_numpy(dtype=float)
            v = v[np.isfinite(v)]
            if len(v) == 0:
                saida[f"{coluna}_s{i}_media"] = 0.0
                saida[f"{coluna}_s{i}_amplitude"] = 0.0
            else:
                saida[f"{coluna}_s{i}_media"] = float(v.mean())
                saida[f"{coluna}_s{i}_amplitude"] = float(v.max() - v.min())
    saida.update(_estatisticas(bloco, colunas))
    return saida


def _reamostrado(bloco: pd.DataFrame, colunas: list[str], pontos: int) -> dict[str, float]:
    """Cada sinal reamostrado para `pontos` valores igualmente espaçados.

    Preserva a forma completa da trajetória dentro do ciclo, e não só resumos
    dela. Reamostrar para comprimento fixo é o que torna comparáveis duas
    pessoas dançando em andamentos diferentes.
    """
    tempos = bloco["t_s"].to_numpy()
    grade = np.linspace(tempos[0], tempos[-1], pontos)
    saida = {}
    for coluna in colunas:
        v = bloco[coluna].to_numpy(dtype=float)
        ok = np.isfinite(v)
        if ok.sum() < 2:
            for i in range(pontos):
                saida[f"{coluna}_p{i:02d}"] = 0.0
            continue
        interpolado = np.interp(grade, tempos[ok], v[ok])
        for i, valor in enumerate(interpolado):
            saida[f"{coluna}_p{i:02d}"] = float(valor)
    return saida


def montar(
    estrategia: str,
    mov: pd.DataFrame,
    sinais: pd.DataFrame,
    duracao_s: float | None = None,
) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Monta a matriz de features de uma estratégia.

    Devolve `(X, meta)`, com `meta` carregando `passo`, `participante` e
    `gravacao` de cada janela — as três colunas que a validação precisa para
    agrupar corretamente.
    """
    if estrategia == "A":
        tabela = coordenadas_normalizadas_por_span(mov)
        colunas = [c for c in tabela.columns if c.endswith(("xn", "yn", "zn"))]
        gerador = _blocos(tabela, float(duracao_s), HOP_S)
        extrator = lambda b: _estatisticas(b, colunas)  # noqa: E731
    elif estrategia == "B":
        gerador = _blocos(sinais, float(duracao_s), HOP_S)
        extrator = lambda b: _estatisticas(b, SINAIS_CORPO)  # noqa: E731
    elif estrategia == "C":
        gerador = _blocos(sinais, float(duracao_s), HOP_S)
        extrator = lambda b: _ordenado(b, SINAIS_CORPO, SUBINTERVALOS)  # noqa: E731
    elif estrategia == "D":
        periodos = periodos_por_gravacao(sinais)
        gerador = _blocos(sinais, periodos.to_dict(), HOP_S)

        def extrator(bloco):
            saida = _reamostrado(bloco, SINAIS_CORPO, PONTOS_POR_CICLO)
            # A duração do ciclo entra como feature: ela é informação de
            # compasso, e é o que o app conhece de graça pelo BPM configurado.
            saida["ciclo_s"] = float(
                bloco["t_s"].to_numpy()[-1] - bloco["t_s"].to_numpy()[0]
            )
            return saida
    else:
        raise ValueError(f"Estratégia desconhecida: {estrategia}")

    linhas, metadados = [], []
    for meta, bloco in gerador:
        linhas.append(extrator(bloco))
        metadados.append(meta)
    if not linhas:
        raise RuntimeError(
            f"Estratégia {estrategia} não produziu nenhuma janela — confira "
            f"duração ({duracao_s}) contra a duração das gravações."
        )

    X = pd.DataFrame(linhas).fillna(0.0)
    return X, pd.DataFrame(metadados)
