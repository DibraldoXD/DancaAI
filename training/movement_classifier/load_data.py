"""
Carregamento das gravações de movimento — Dança AI (TCC02, atividade A.2.2).

Lê a planilha de coleta e devolve as gravações de passo prontas para o treino do
classificador dos 6 passos de forró. Três decisões aqui não são detalhe de
implementação; são o que separa um número honesto de um número memorizado.

  1. O eixo de tempo é o `t<ms>` do rótulo, não a coluna `Timestamp`. O
     Timestamp tem resolução de 1 segundo, e a gravação amostra a 10 Hz — dez
     amostras por segundo compartilham o mesmo Timestamp. Reconstruir a
     trajetória por ele embaralharia a ordem dentro de cada segundo.

  2. Participante não é o mesmo que sessão por intervalo de tempo. O
     agrupamento por 5 minutos funde dois participantes num só quando a troca
     de pessoa foi rápida — ver [CORTE_MANUAL]. Sem o corte, duas pessoas
     atravessam treino e teste e o resultado mede memorização.

  3. O contador `r` do MovementRecorder reinicia a cada participante, então
     `mov_balanco|r1` existe sete vezes na planilha. A identidade de uma
     gravação é a tripla (participante, passo, r) — nunca o `r` sozinho.

Uso:
    python load_data.py                      # baixa da planilha
    python load_data.py data/capturas.csv    # usa um CSV já baixado
"""

from __future__ import annotations

import hashlib
import re
import sys
from datetime import datetime
from pathlib import Path

# O console padrão do Windows usa cp1252 e derruba o processo com
# UnicodeEncodeError ao imprimir um caractere fora dessa tabela. Trocar o
# caractere por "?" é sempre melhor que perder o relatório inteiro.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(errors="replace")

import numpy as np
import pandas as pd

MODULO_DIR = Path(__file__).parent
DADOS_DIR = MODULO_DIR / "data"

SHEET_ID = "1hNIBCHZfQR7AEAeGuJ4ga6K_XTPrlMExZEBIXffpqUc"
CSV_URL = f"https://docs.google.com/spreadsheets/d/{SHEET_ID}/export?format=csv&gid=0"

# Ordem dos landmarks enviada pelo app em `toSheetRow()` (ver TrainingScreen.kt e
# a tabela de 38 colunas no README). Mudar esta lista desalinha o dataset.
LANDMARKS = [
    "NAR", "ORE-E", "ORE-D", "OMB-E", "OMB-D",
    "QDR-E", "QDR-D", "JOE-E", "JOE-D", "TRN-E", "TRN-D",
]
COLUNAS_COORD = [f"{m}_{eixo}" for m in LANDMARKS for eixo in ("x", "y", "z")]

# Os 6 passos de forró do escopo do TCC, na ordem em que aparecem nos relatórios.
# Os tokens vêm de CaptureSubject em CaptureLabel.kt; renomear um aqui desalinha
# o dataset já coletado.
PASSOS = [
    "mov_balanco",
    "mov_base1",
    "mov_base2",
    "mov_abertura",
    "mov_giro_invertido",
    "mov_giro_linha",
]
NOMES_PASSOS = {
    "mov_balanco": "Balanço",
    "mov_base1": "Base 1",
    "mov_base2": "Base 2",
    "mov_abertura": "Abertura",
    "mov_giro_invertido": "Giro invertido (Base 3)",
    "mov_giro_linha": "Giro em linha (Base 4)",
}

# Formato do rótulo de uma amostra de gravação: `assunto@angulo|r<n>s<i>t<ms>`
# (ver CaptureLabel.kt). O `s` é o índice da amostra e o `t` o tempo decorrido em
# milissegundos desde o início da gravação — os dois são necessários porque
# frames sem pessoa no enquadramento não viram amostra, e é o salto no `t` que
# revela o buraco.
PADRAO_ROTULO = re.compile(
    r"^(?P<passo>mov_[a-z0-9_]+)@(?P<angulo>[a-zA-Z_]+)"
    r"\|r(?P<r>\d+)s(?P<s>\d+)t(?P<t>\d+)$"
)

GAP_SESSAO_MINUTOS = 5

# Corte manual dentro da sessão fundida da coleta de 04/11/2026.
#
# Dois participantes se seguiram com menos de 5 minutos de intervalo, então o
# agrupamento por tempo os juntou. O sintoma é a taxa de amostragem aparente:
# uma sessão de uma pessoa mede ~10 Hz (o intervalo do MovementRecorder); a
# sessão fundida mede 18-20 Hz, porque duas gravações distintas com o mesmo
# `r` entram como se fossem uma, com os tempos interleavados.
#
# O instante abaixo foi lido da própria planilha: é o intervalo sem captura
# entre a última gravação de uma pessoa e a primeira da seguinte. Uma coleta
# nova não precisa dele — precisa registrar o participante no rótulo.
CORTE_MANUAL = pd.Timestamp("2026-11-04 21:13:30")

# Duração mínima de uma gravação aproveitável.
#
# O passo mais longo do escopo tem 5 tempos (giro em linha). A 60 BPM, o piso do
# metrônomo do app, 5 tempos levam 5 s. Uma gravação mais curta que isso não
# contém um ciclo completo nem no andamento mais lento possível, e entraria como
# exemplo de um passo cujo traço distintivo está cortado.
DURACAO_MINIMA_S = 5.0


def baixar_ou_ler(origem: str | Path | None = None) -> pd.DataFrame:
    """Lê o CSV da planilha (ou um arquivo local) convertendo o locale pt-BR.

    A planilha está em pt-BR: vírgula decimal e campos entre aspas. Ler tudo
    como texto e converter explicitamente funciona nos dois locales, enquanto
    deixar o pandas inferir produz colunas `object` silenciosamente — e aí
    `mean()` devolve concatenação de strings em vez de erro.
    """
    caminho = CSV_URL if origem is None else str(origem)
    df = pd.read_csv(caminho, dtype=str)

    faltando = [c for c in COLUNAS_COORD if c not in df.columns]
    if faltando:
        raise ValueError(
            f"Colunas de landmark ausentes no CSV: {faltando}. "
            "Confira se o cabeçalho da planilha ainda é o contrato de 38 colunas "
            "descrito no README."
        )

    for coluna in [c for c in df.columns if c not in ("Timestamp", "Rotulo")]:
        df[coluna] = pd.to_numeric(
            df[coluna].str.replace(",", ".", regex=False), errors="coerce"
        )
    return df


def extrair_gravacoes(df: pd.DataFrame) -> pd.DataFrame:
    """Filtra as amostras de gravação de movimento e anexa as chaves de grupo.

    Devolve um DataFrame com as colunas de coordenada mais `passo`, `angulo`,
    `participante`, `gravacao` e `t_s`.
    """
    campos = df["Rotulo"].str.extract(PADRAO_ROTULO)
    é_gravacao = campos["passo"].notna()

    descartadas = len(df) - int(é_gravacao.sum())
    print(f"Linhas na planilha: {len(df)}")
    print(f"Amostras de gravação de movimento: {int(é_gravacao.sum())}")
    print(f"Descartadas (postura, verificação, captura instantânea): {descartadas}")

    mov = df[é_gravacao].copy()
    mov["passo"] = campos.loc[é_gravacao, "passo"]
    mov["angulo"] = campos.loc[é_gravacao, "angulo"]
    mov["r"] = campos.loc[é_gravacao, "r"].astype(int)
    mov["indice_amostra"] = campos.loc[é_gravacao, "s"].astype(int)
    mov["t_s"] = campos.loc[é_gravacao, "t"].astype(int) / 1000.0
    mov["Timestamp"] = pd.to_datetime(mov["Timestamp"])

    fora_do_escopo = mov[~mov["passo"].isin(PASSOS)]
    if not fora_do_escopo.empty:
        print(f"\nDescartadas {len(fora_do_escopo)} amostra(s) de passo fora do escopo:")
        print(fora_do_escopo["passo"].value_counts().to_string())
        mov = mov[mov["passo"].isin(PASSOS)]

    print("\nÂngulos de câmera presentes:")
    print(mov["angulo"].value_counts().to_string())

    mov = _segmentar_participantes(mov)
    mov["gravacao"] = (
        mov["participante"] + "/" + mov["passo"] + "/r" + mov["r"].astype(str)
    )
    return mov.sort_values(["participante", "passo", "r", "indice_amostra"]).reset_index(
        drop=True
    )


def _segmentar_participantes(mov: pd.DataFrame) -> pd.DataFrame:
    """Deriva o rótulo de participante: intervalo de 5 min + o corte manual.

    O rótulo é posicional (`p0`, `p1`, ...), derivado só do Timestamp — não
    identifica ninguém, o que importa porque este repositório é público.
    """
    mov = mov.sort_values(["Timestamp", "indice_amostra"]).reset_index(drop=True)
    gap = mov["Timestamp"].diff() > pd.Timedelta(minutes=GAP_SESSAO_MINUTOS)
    sessao = gap.cumsum()

    # Só a sessão atravessada pelo corte é dividida. Aplicar o corte a todas as
    # sessões criaria um participante vazio em cada uma que começa depois dele.
    antes = set(sessao[mov["Timestamp"] < CORTE_MANUAL])
    depois = set(sessao[mov["Timestamp"] >= CORTE_MANUAL])
    fundidas = antes & depois

    sufixo = np.where(
        sessao.isin(fundidas) & (mov["Timestamp"] >= CORTE_MANUAL), "b", ""
    )
    mov["participante"] = "p" + sessao.astype(str) + sufixo

    print(f"\nSessões por intervalo de {GAP_SESSAO_MINUTOS} min: {sessao.nunique()}")
    if fundidas:
        print(
            f"Corte manual em {CORTE_MANUAL} dividiu a(s) sessão(ões) {sorted(fundidas)} "
            f"— duas pessoas se seguiram em menos de {GAP_SESSAO_MINUTOS} min."
        )
    else:
        print(
            "AVISO: O corte manual não encontrou sessão fundida. Se a planilha cresceu "
            "com uma coleta nova, confira se a fusão continua no mesmo instante "
            "(sintoma: taxa aparente acima de 12 Hz no relatório abaixo)."
        )
    print(f"Participantes: {mov['participante'].nunique()}")
    return mov


def relatorio_integridade(mov: pd.DataFrame) -> pd.DataFrame:
    """Imprime uma linha por gravação e devolve a tabela de resumo.

    A taxa aparente é o teste de sanidade da segmentação: o MovementRecorder
    amostra a 10 Hz, então qualquer gravação muito acima disso é sinal de que
    duas gravações distintas foram fundidas numa só chave.
    """
    linhas = []
    for (part, passo, grav), g in mov.groupby(
        ["participante", "passo", "gravacao"], sort=False
    ):
        g = g.sort_values("indice_amostra")
        duracao = float(g["t_s"].iloc[-1] - g["t_s"].iloc[0])
        intervalos = g["t_s"].diff().dropna()
        linhas.append(
            {
                "participante": part,
                "passo": passo,
                "gravacao": grav,
                "amostras": len(g),
                "duracao_s": duracao,
                "taxa_hz": (len(g) - 1) / duracao if duracao > 0 else np.nan,
                "maior_buraco_s": float(intervalos.max()) if len(intervalos) else np.nan,
            }
        )
    resumo = pd.DataFrame(linhas)

    print("\n" + "=" * 78)
    print("INTEGRIDADE DAS GRAVAÇÕES")
    print("=" * 78)
    print(
        resumo.to_string(
            index=False,
            columns=["participante", "passo", "amostras", "duracao_s", "taxa_hz", "maior_buraco_s"],
            float_format=lambda v: f"{v:.2f}",
        )
    )

    suspeitas = resumo[(resumo["taxa_hz"] > 12) | (resumo["taxa_hz"] < 7)]
    if not suspeitas.empty:
        print(
            f"\nAVISO: {len(suspeitas)} gravação(ões) com taxa fora de 7-12 Hz. Acima de 12 Hz "
            "geralmente é fusão de participantes; abaixo de 7 Hz é perda de frames "
            "(pessoa saindo do enquadramento)."
        )
        print(suspeitas.to_string(index=False, float_format=lambda v: f"{v:.2f}"))
    return resumo


def carregar(origem: str | Path | None = None, salvar_snapshot: bool = True):
    """Fluxo completo: lê, segmenta, audita e descarta o que não serve.

    Devolve `(mov, resumo)`, com `mov` já sem as gravações curtas demais.
    """
    df = baixar_ou_ler(origem)
    mov = extrair_gravacoes(df)
    resumo = relatorio_integridade(mov)

    curtas = resumo[resumo["duracao_s"] < DURACAO_MINIMA_S]
    if not curtas.empty:
        print(
            f"\nDescartadas {len(curtas)} gravação(ões) com menos de {DURACAO_MINIMA_S:.0f} s "
            f"— não contêm um ciclo completo nem a 60 BPM:"
        )
        print(curtas.to_string(index=False, float_format=lambda v: f"{v:.2f}"))
        mov = mov[~mov["gravacao"].isin(curtas["gravacao"])].reset_index(drop=True)
        resumo = resumo[resumo["duracao_s"] >= DURACAO_MINIMA_S].reset_index(drop=True)

    print("\n" + "=" * 78)
    print("DATASET APROVEITÁVEL")
    print("=" * 78)
    print(f"Amostras: {len(mov)}")
    print(f"Gravações: {mov['gravacao'].nunique()}")
    print(f"Participantes: {mov['participante'].nunique()}")
    print("\nGravações por passo e participante:")
    print(
        mov.groupby(["passo", "participante"])["gravacao"]
        .nunique()
        .unstack(fill_value=0)
        .to_string()
    )

    cobertura = mov.groupby("participante")["passo"].nunique()
    incompletos = cobertura[cobertura < len(PASSOS)]
    if not incompletos.empty:
        print(
            "\nAVISO: Participante(s) sem os 6 passos — a dobra de teste correspondente "
            "não mede as classes ausentes:"
        )
        print(incompletos.to_string())

    if salvar_snapshot:
        _salvar_snapshot(mov)
    return mov, resumo


def _salvar_snapshot(mov: pd.DataFrame) -> None:
    """Congela o dataset usado numa execução, para o número ser reproduzível.

    A planilha continua recebendo capturas; sem o snapshot, o número no README
    não corresponde a nenhum dataset específico. O arquivo fica em `data/`, que
    o .gitignore exclui: são dados corporais de participantes e este
    repositório é público.
    """
    DADOS_DIR.mkdir(exist_ok=True)
    caminho = DADOS_DIR / f"dataset_movimento_{datetime.now():%Y%m%d_%H%M}.csv"
    conteudo = mov.to_csv(index=False)
    caminho.write_text(conteudo, encoding="utf-8")
    sha = hashlib.sha256(conteudo.encode()).hexdigest()[:12]
    print(f"\nSnapshot: {caminho.name}")
    print(f"SHA-256 (12): {sha}")


if __name__ == "__main__":
    carregar(sys.argv[1] if len(sys.argv) > 1 else None)
