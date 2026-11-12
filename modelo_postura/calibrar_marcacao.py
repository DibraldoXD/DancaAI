"""
Calibra os limiares do módulo de marcação (transferência de peso) sobre as gravações de
movimento da planilha.

Premissa: balanço, base 1 e base 2 foram executados corretamente e os passos sempre
alternam. Logo, TODO erro de marcação que o algoritmo acusar nessas gravações é falso
positivo. O script mede, para cada configuração, quantos erros falsos ela produz, quanto
do tempo ela passa em "neutro" e se ela ainda enxerga as trocas de perna evidentes (para
que zerar os erros não seja só deixar de detectar passos).

A lógica de `Maquina` e de `suavizar` espelha, linha a linha, `WeightStateMachine.kt` e
`StepCounter.kt` (app/src/main/java/com/dancaai/...). Qualquer mudança lá precisa ser
refeita aqui; `--autoteste` confere o espelhamento nos cenários dos testes unitários.

Só biblioteca padrão: roda em qualquer Python 3.9+, inclusive no Colab.

Uso:
    python calibrar_marcacao.py                      # baixa a planilha (mesmo link dos outros scripts)
    python calibrar_marcacao.py --csv planilha.csv   # usa um CSV local
    python calibrar_marcacao.py --varredura          # varre faixa x histerese x permanência
    python calibrar_marcacao.py --autoteste

Limitações que valem para o resultado:
  • as gravações são a ~10 Hz; o app roda a ~30 Hz. Permanências em ms valem para os dois,
    mas abaixo de 100 ms o offline não consegue distinguir (1 frame = 100 ms);
  • a planilha não guarda a batida do metrônomo, então a regra da pausa não é simulada
    aqui. Ela só reduz erros, portanto o número offline é um teto.
"""
from __future__ import annotations

import argparse
import csv
import itertools
import math
import re
import sys
import urllib.request
from collections import defaultdict
from dataclasses import dataclass, replace
from datetime import datetime
from pathlib import Path

# ── fonte dos dados ──────────────────────────────────────────────────────────

SHEET_ID = "1hNIBCHZfQR7AEAeGuJ4ga6K_XTPrlMExZEBIXffpqUc"  # o mesmo de treinar_multilabel.py
CSV_URL = f"https://docs.google.com/spreadsheets/d/{SHEET_ID}/export?format=csv&gid=0"

# Movimentos de execução correta, usados como referência de "zero erro".
MOVIMENTOS_CORRETOS = ("mov_balanco", "mov_base1", "mov_base2")

# Rótulo: assunto@angulo|r<gravacao>s<indice>t<ms>[|p<NN>]  (ver CaptureLabel.kt)
ROTULO_GRAVACAO = re.compile(r"^(?P<assunto>[^@|]+)(?:@(?P<angulo>[^|]*))?\|r(?P<rec>\d+)s(?P<idx>\d+)t(?P<t>\d+)")
PARTICIPANTE = re.compile(r"\|p(?P<p>\d+)")

# Duas linhas da mesma gravação nunca ficam mais de alguns segundos afastadas; o número r<n>
# volta a 1 a cada sessão, então um intervalo grande separa gravações distintas.
LACUNA_ENTRE_GRAVACOES_S = 10.0


@dataclass
class Gravacao:
    assunto: str
    angulo: str
    participante: str
    rec: int
    # (t_ms, quadril_x, tornozelo_E_x, tornozelo_D_x, tornozelo_E_z, tornozelo_D_z)
    quadros: list[tuple[float, float, float, float, float, float]]

    @property
    def nome(self) -> str:
        p = f"|p{self.participante}" if self.participante else ""
        return f"{self.assunto}@{self.angulo}{p}|r{self.rec}"

    @property
    def duracao_s(self) -> float:
        return (self.quadros[-1][0] - self.quadros[0][0]) / 1000.0 if self.quadros else 0.0


def _num(s: str) -> float | None:
    s = (s or "").strip().replace(",", ".")  # a planilha pode estar em pt-BR
    try:
        return float(s)
    except ValueError:
        return None


def carregar(origem: str | None) -> list[Gravacao]:
    """Lê o CSV (arquivo ou link) e devolve as gravações de movimento, quadros em ordem de tempo."""
    if origem is None:
        print("Baixando a planilha...", file=sys.stderr)
        with urllib.request.urlopen(CSV_URL) as r:
            texto = r.read().decode("utf-8-sig")
        linhas = list(csv.reader(texto.splitlines()))
    else:
        with open(origem, encoding="utf-8-sig", newline="") as f:
            linhas = list(csv.reader(f))

    cab = {nome: i for i, nome in enumerate(linhas[0])}
    precisa = ("Timestamp", "Rotulo", "QDR-E_x", "QDR-D_x", "TRN-E_x", "TRN-D_x", "TRN-E_z", "TRN-D_z")
    faltando = [c for c in precisa if c not in cab]
    if faltando:
        raise SystemExit(f"Colunas ausentes no CSV: {faltando}")

    brutos: dict[tuple, list] = defaultdict(list)
    for r in linhas[1:]:
        if len(r) <= max(cab[c] for c in precisa):
            continue
        m = ROTULO_GRAVACAO.match(r[cab["Rotulo"]])
        if not m:
            continue  # captura avulsa, postura ou linha de métricas
        try:
            ts = datetime.strptime(r[cab["Timestamp"]], "%Y-%m-%d %H:%M:%S")
        except ValueError:
            continue
        vals = [_num(r[cab[c]]) for c in ("QDR-E_x", "QDR-D_x", "TRN-E_x", "TRN-D_x", "TRN-E_z", "TRN-D_z")]
        if any(v is None for v in vals):
            continue
        qe, qd, te, td, tez, tdz = vals
        part = PARTICIPANTE.search(r[cab["Rotulo"]])
        chave = (m["assunto"], m["angulo"] or "", part["p"] if part else "", int(m["rec"]))
        brutos[chave].append((ts, float(m["t"]), (qe + qd) / 2.0, te, td, tez, tdz))

    gravacoes: list[Gravacao] = []
    for (assunto, angulo, part, rec), pts in brutos.items():
        pts.sort(key=lambda p: p[0])
        grupo = [pts[0]]
        for p in pts[1:]:
            if (p[0] - grupo[-1][0]).total_seconds() > LACUNA_ENTRE_GRAVACOES_S:
                gravacoes.append(_montar(assunto, angulo, part, rec, grupo))
                grupo = []
            grupo.append(p)
        gravacoes.append(_montar(assunto, angulo, part, rec, grupo))
    return [g for g in gravacoes if len(g.quadros) >= 5]


def _montar(assunto, angulo, part, rec, pts) -> Gravacao:
    quadros = sorted(((p[1], p[2], p[3], p[4], p[5], p[6]) for p in pts), key=lambda q: q[0])
    return Gravacao(assunto, angulo, part, rec, quadros)


def reamostrar(g: Gravacao, hz: float) -> Gravacao:
    """
    Interpola linearmente as posições para `hz` quadros por segundo.

    As gravações são a ~10 Hz e o app roda a ~30 Hz. Com 10 Hz, "80 ms de permanência"
    exige 2 quadros seguidos (>=100 ms) e perde oscilações que o app, a 30 Hz, enxergaria.
    A interpolação é só uma aproximação (não recupera ruído nem picos entre amostras), mas
    deixa permanências em ms comparáveis às do aparelho. Não interpola lacunas > 250 ms.
    """
    passo = 1000.0 / hz
    q = g.quadros
    novos = []
    for a, b in zip(q, q[1:]):
        dt = b[0] - a[0]
        if dt <= 0:
            continue
        n = max(int(dt // passo), 1) if dt <= 250 else 1
        for k in range(n):
            f = (k * passo) / dt if dt <= 250 else 0.0
            novos.append(tuple(x + (y - x) * f for x, y in zip(a, b)))
    novos.append(q[-1])
    return Gravacao(g.assunto, g.angulo, g.participante, g.rec, novos)


# ── espelho de StepCounter.kt (suavização) e WeightStateMachine.kt ──────────

SMOOTHING_TAU_MS = 77.0     # StepCounter.SMOOTHING_TAU_MS
MAX_SMOOTHING_DT_MS = 1000  # StepCounter.MAX_SMOOTHING_DT_MS
MIN_TOTAL_DIST = 0.008      # StepCounter.MIN_TOTAL_DIST


@dataclass(frozen=True)
class Config:
    """Espelha WeightStateMachine.Config."""
    upper: float = 0.55
    lower: float = 0.45
    hysteresis: float = 0.03
    dwell_ms: int = 30
    neutral_dwell_ms: int = 300
    max_gap_ms: int = 500
    z_tiebreak: bool = False
    z_threshold: float = 0.10


# O algoritmo anterior ao ajuste: zona 0,40-0,60, desempate por z ligado, sem histerese
# nem permanência; erro = mesma perna depois de qualquer frame neutro.
CONFIG_ANTIGA = Config(upper=0.60, lower=0.40, hysteresis=0.0, dwell_ms=0, neutral_dwell_ms=0, z_tiebreak=True)


def suavizar(quadros):
    """EMA por constante de tempo das posições; devolve (t, razao|None, zdiff) por quadro."""
    sh = sl = sr = szl = szr = None
    anterior = None
    saida = []
    for t, hip, tl, tr, zl, zr in quadros:
        if anterior is None:
            alpha = 1.0
        else:
            dt = min(max(t - anterior, 1), MAX_SMOOTHING_DT_MS)
            alpha = 1.0 - math.exp(-dt / SMOOTHING_TAU_MS)
        anterior = t

        def ema(prev, cur):
            return cur if prev is None else prev + (cur - prev) * alpha

        sh, sl, sr = ema(sh, hip), ema(sl, tl), ema(sr, tr)
        szl, szr = ema(szl, zl), ema(szr, zr)
        dist_l, dist_r = abs(sh - sl), abs(sh - sr)
        total = dist_l + dist_r
        razao = dist_r / total if total > MIN_TOTAL_DIST else None
        saida.append((t, razao, szr - szl))
    return saida


class Maquina:
    """Espelho de WeightStateMachine. Estados: "L", "R", "N"."""

    def __init__(self, cfg: Config):
        self.cfg = cfg
        self.estado = "N"
        self.ultimo = None
        self.pendente = None
        self.pendente_desde = 0
        self.ultimo_update = None

    def _zona(self, razao, zdiff):
        c = self.cfg
        if razao is None:
            return "N"
        acima = c.upper - c.hysteresis if self.estado == "L" else c.upper
        abaixo = c.lower + c.hysteresis if self.estado == "R" else c.lower
        if razao > acima:
            return "L"
        if razao < abaixo:
            return "R"
        if c.z_tiebreak:
            if zdiff > c.z_threshold:
                return "L"
            if zdiff < -c.z_threshold:
                return "R"
        return "N"

    def update(self, razao, zdiff, t, em_pausa=False):
        """Devolve (perna, inicio_ms, e_erro) se este quadro confirmou uma troca."""
        if self.ultimo_update is not None and t - self.ultimo_update > self.cfg.max_gap_ms:
            self.pendente = None
        self.ultimo_update = t
        zona = self._zona(razao, zdiff)
        if zona == self.estado:
            self.pendente = None
            return None
        if zona != self.pendente:
            self.pendente, self.pendente_desde = zona, t
        precisa = self.cfg.neutral_dwell_ms if zona == "N" else self.cfg.dwell_ms
        if t - self.pendente_desde < precisa or em_pausa:
            return None
        e_erro = zona != "N" and self.ultimo == zona
        evento = (zona, self.pendente_desde, e_erro)
        self.estado = zona
        if zona != "N":
            self.ultimo = zona
        self.pendente = None
        return evento


# ── simulação e métricas ─────────────────────────────────────────────────────

@dataclass
class Resultado:
    quadros: int = 0
    quadros_neutro: int = 0
    duracao_s: float = 0.0
    eventos: int = 0          # trocas confirmadas para L ou R
    erros: int = 0
    recall_num: int = 0       # trocas evidentes (referência) reconhecidas
    recall_den: int = 0
    extras: int = 0           # eventos sem troca evidente por perto

    def somar(self, o: "Resultado") -> None:
        for campo in self.__dataclass_fields__:
            setattr(self, campo, getattr(self, campo) + getattr(o, campo))

    @property
    def pct_neutro(self) -> float:
        return 100.0 * self.quadros_neutro / self.quadros if self.quadros else 0.0

    @property
    def recall(self) -> float:
        return self.recall_num / self.recall_den if self.recall_den else 1.0

    @property
    def trocas_por_s(self) -> float:
        return self.eventos / self.duracao_s if self.duracao_s else 0.0


# Trocas "evidentes": comparador binário em torno de 0,5 (vira para ESQUERDA acima de 0,57 e
# para DIREITA abaixo de 0,43, e só volta ao cruzar o outro limiar). Alterna por construção
# e ignora oscilação de amplitude pequena. Serve para garantir que uma configuração não
# zera os erros apenas deixando de enxergar passos de verdade. (Uma primeira versão usava
# a própria máquina com faixa 0,35-0,65 e sem permanência: ela repetia "L, N, L" para um
# único apoio sustentado, e medir contra ela só media discordância com ruído.)
REF_BAIXO, REF_ALTO = 0.43, 0.57
# A faixa estreita cruza o limiar ANTES da referência larga (a razão sobe de 0,55 a 0,65
# no meio do caminho), então o evento candidato costuma vir antes do de referência.
# Janela assimétrica: aceita candidato até 600 ms antes e 350 ms depois.
ANTES_MS = 600
DEPOIS_MS = 350


def eventos_de(suave, cfg: Config):
    m = Maquina(cfg)
    eventos, neutros = [], 0
    for t, razao, zdiff in suave:
        ev = m.update(razao, zdiff, t)
        if ev:
            eventos.append(ev)
        neutros += m.estado == "N"
    return eventos, neutros


def referencia(suave):
    """Trocas evidentes (ver REF_BAIXO/REF_ALTO): lista de (perna, t_ms) que sempre alterna."""
    estado, eventos = None, []
    for t, razao, _ in suave:
        if razao is None:
            continue
        if razao > REF_ALTO and estado != "L":
            estado = "L"
            eventos.append(("L", t))
        elif razao < REF_BAIXO and estado != "R":
            estado = "R"
            eventos.append(("R", t))
    return eventos


def avaliar(g: Gravacao, cfg: Config) -> Resultado:
    suave = suavizar(g.quadros)
    eventos, neutros = eventos_de(suave, cfg)
    ref = referencia(suave)
    cand = [(p, t, err) for p, t, err in eventos if p != "N"]

    # Casamento um-para-um em ordem de tempo: cada troca evidente consome no máximo um
    # evento candidato da mesma perna, para que um ciclo não "reconheça" o ciclo vizinho.
    usados: set[int] = set()
    reconhecidas = 0
    for p, t in ref:
        for i, (cp, ct, _) in enumerate(cand):
            if i not in usados and cp == p and t - ANTES_MS <= ct <= t + DEPOIS_MS:
                usados.add(i)
                reconhecidas += 1
                break

    r = Resultado(quadros=len(suave), quadros_neutro=neutros, duracao_s=g.duracao_s)
    r.eventos = len(cand)
    r.erros = sum(1 for _, _, err in cand if err)
    r.recall_den = len(ref)
    r.recall_num = reconhecidas
    r.extras = len(cand) - len(usados)
    return r


def avaliar_todas(gravacoes, cfg: Config) -> Resultado:
    total = Resultado()
    for g in gravacoes:
        total.somar(avaliar(g, cfg))
    return total


# ── relatórios ───────────────────────────────────────────────────────────────

def descreve(cfg: Config) -> str:
    z = "z" if cfg.z_tiebreak else "-"
    return (f"zona {cfg.lower:.2f}-{cfg.upper:.2f} hist {cfg.hysteresis:.2f} "
            f"perm {cfg.dwell_ms}/{cfg.neutral_dwell_ms}ms {z}")


def linha(nome: str, r: Resultado) -> str:
    return (f"{nome:<44} {r.pct_neutro:6.1f}% neutro  {r.erros:4d} erros  "
            f"{r.eventos:5d} trocas ({r.trocas_por_s:4.2f}/s)  recall {100 * r.recall:5.1f}%  extras {r.extras:4d}")


def relatorio_comparativo(gravacoes, cfg_nova: Config) -> None:
    print(f"\n== {len(gravacoes)} gravações, {sum(g.duracao_s for g in gravacoes):.0f} s, "
          f"{sum(len(g.quadros) for g in gravacoes)} quadros ==")
    print("(todo erro aqui é falso positivo: execuções corretas)\n")
    print(linha("ANTIGO  " + descreve(CONFIG_ANTIGA), avaliar_todas(gravacoes, CONFIG_ANTIGA)))
    print(linha("NOVO    " + descreve(cfg_nova), avaliar_todas(gravacoes, cfg_nova)))

    print("\nPor movimento e ângulo:")
    grupos = defaultdict(list)
    for g in gravacoes:
        grupos[(g.assunto, g.angulo)].append(g)
    for (assunto, angulo), gs in sorted(grupos.items()):
        a, n = avaliar_todas(gs, CONFIG_ANTIGA), avaliar_todas(gs, cfg_nova)
        print(f"  {assunto}@{angulo:<10} {len(gs):3d} grav.  neutro {a.pct_neutro:5.1f}% -> {n.pct_neutro:5.1f}%   "
              f"erros {a.erros:3d} -> {n.erros:3d}   recall {100 * n.recall:5.1f}%")

    piores = sorted(gravacoes, key=lambda g: -avaliar(g, cfg_nova).erros)[:5]
    if avaliar(piores[0], cfg_nova).erros:
        print("\nGravações que ainda acusam erro com a configuração nova:")
        for g in piores:
            r = avaliar(g, cfg_nova)
            if r.erros:
                print("  " + linha(g.nome, r))


def contexto_dos_erros(gravacoes, cfg: Config, antes_ms: int = 1500) -> None:
    """Para cada erro acusado, a razão suavizada dos instantes que o antecederam."""
    n = 0
    for g in gravacoes:
        m, hist = Maquina(cfg), []
        for t, razao, zdiff in suavizar(g.quadros):
            ev = m.update(razao, zdiff, t)
            hist.append((t, razao))
            if ev and ev[2]:
                n += 1
                print(f"\n#{n} {g.nome}: erro em {ev[0]}, candidato desde {ev[1]:.0f} ms, confirmado em {t:.0f} ms")
                ctx = [(tt, rr) for tt, rr in hist if ev[1] - antes_ms <= tt <= t]
                print("   razão (quadros de ~100 ms; -- = pés juntos): "
                      + " ".join(f"{rr:.2f}" if rr is not None else "--" for _, rr in ctx))
    if n == 0:
        print("\nNenhum erro acusado com esta configuração.")


def varredura(gravacoes, topo: int = 20) -> None:
    resultados = []
    grade = itertools.product(
        [(0.40, 0.60), (0.45, 0.55), (0.47, 0.53), (0.48, 0.52), (0.49, 0.51)],  # (lower, upper)
        [0.0, 0.02, 0.03],
        # a 10 Hz, 0 ms = "1 quadro" (~100 ms); 80 ms = "2 quadros seguidos"
        [0, 80, 150],
        [0, 150, 300, 500],
        [False, True],
    )
    for (lo, up), hist, dwell, ndwell, z in grade:
        cfg = Config(upper=up, lower=lo, hysteresis=hist, dwell_ms=dwell, neutral_dwell_ms=ndwell, z_tiebreak=z)
        resultados.append((cfg, avaliar_todas(gravacoes, cfg)))

    # Só vale quem ainda enxerga as trocas evidentes; entre elas, menos erro e menos neutro.
    validos = [(c, r) for c, r in resultados if r.recall >= 0.95]
    validos.sort(key=lambda cr: (cr[1].erros, cr[1].pct_neutro))
    print(f"\n== Varredura: {len(resultados)} configurações, {len(validos)} com recall >= 95% ==")
    print(f"Melhores {topo} (menos erros falsos, depois menos neutro):\n")
    for cfg, r in validos[:topo]:
        print(linha(descreve(cfg), r))
    if not validos:
        print("Nenhuma configuração manteve 95% de recall; o critério de recall precisa ser revisto.")


# ── autoteste: o espelho Python reproduz os cenários dos testes Kotlin ──────

def autoteste() -> None:
    def rodar(seq, passo=33, cfg=Config()):
        m, evs, t = Maquina(cfg), [], 0
        for razao in seq:
            ev = m.update(razao, 0.0, t)
            if ev:
                evs.append(ev)
            t += passo
        return evs

    def gera(f, fim, passo):
        return [f(t) for t in range(0, fim, passo)]

    esq, dir_, neu = 0.80, 0.20, 0.50

    # alternância limpa: sem erro
    ev = rodar(gera(lambda t: esq if t < 300 else dir_ if t < 600 else esq, 1000, 33))
    assert [e[0] for e in ev] == ["L", "R", "L"] and not any(e[2] for e in ev), ev

    # oscilação na borda: nenhuma transição nova depois do primeiro apoio
    ev = rodar([esq] * 10 + [0.56 if i % 2 == 0 else 0.54 for i in range(60)])
    assert [e[0] for e in ev] == ["L"], ev

    # neutro curto entre a mesma perna: não é erro
    ev = rodar(gera(lambda t: neu if 300 <= t <= 400 else esq, 1000, 33))
    assert [e[0] for e in ev] == ["L"], ev

    # neutro estável e a mesma perna de novo: erro
    ev = rodar(gera(lambda t: esq if t < 300 else neu if t < 800 else esq, 1500, 33))
    assert [(e[0], e[2]) for e in ev] == [("L", False), ("N", False), ("L", True)], ev

    # mesma taxa de acerto a 10 Hz e a 30 Hz
    f = lambda t: esq if t < 500 else dir_ if t < 1000 else esq if t < 1500 else dir_
    a10 = [(e[0], e[2]) for e in rodar(gera(f, 2000, 100), 100)]
    a30 = [(e[0], e[2]) for e in rodar(gera(f, 2000, 33), 33)]
    assert a10 == a30 and len(a10) == 4, (a10, a30)

    # lacuna de quadros descarta o candidato em andamento
    m = Maquina(Config())
    m.update(esq, 0.0, 0)
    assert m.update(esq, 0.0, 1000) is None
    ev = m.update(esq, 0.0, 1100)
    assert ev and ev[0] == "L" and ev[1] == 1000, ev

    # documenta o defeito corrigido: oscilando em torno de 0,60 o algoritmo antigo vai a
    # neutro e volta à mesma perna, e isso contava como erro de marcação
    ev = rodar([esq] * 10 + [0.62, 0.58, 0.62, 0.58, 0.62] * 4, passo=33, cfg=CONFIG_ANTIGA)
    assert any(e[2] for e in ev), ev
    ev = rodar([esq] * 10 + [0.62, 0.58, 0.62, 0.58, 0.62] * 4, passo=33)
    assert not any(e[2] for e in ev), ev

    print("autoteste ok: espelho Python confere com os cenários de WeightStateMachineTest")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--csv", help="CSV local da planilha (padrão: baixa pelo link)")
    ap.add_argument("--movimentos", default=",".join(MOVIMENTOS_CORRETOS),
                    help="assuntos tratados como execução correta, separados por vírgula")
    ap.add_argument("--varredura", action="store_true", help="varre as configurações e lista as melhores")
    ap.add_argument("--autoteste", action="store_true", help="confere o espelho Python e sai")
    ap.add_argument("--faixa", nargs=2, type=float, metavar=("LOWER", "UPPER"), help="faixa neutra a avaliar")
    ap.add_argument("--histerese", type=float, default=Config().hysteresis)
    ap.add_argument("--permanencia", nargs=2, type=int, metavar=("PERNA_MS", "NEUTRO_MS"),
                    default=(Config().dwell_ms, Config().neutral_dwell_ms))
    ap.add_argument("--z", action="store_true", help="liga o desempate por z")
    ap.add_argument("--erros", action="store_true", help="mostra o contexto de cada erro acusado")
    ap.add_argument("--hz", type=float, help="reamostra as gravações (10 Hz) para esta taxa, ex.: 30")
    args = ap.parse_args()

    if args.autoteste:
        autoteste()
        return

    alvo = set(args.movimentos.split(","))
    gravacoes = [g for g in carregar(args.csv) if g.assunto in alvo]
    if not gravacoes:
        raise SystemExit(f"Nenhuma gravação encontrada para {sorted(alvo)}")
    if args.hz:
        gravacoes = [reamostrar(g, args.hz) for g in gravacoes]
        print(f"(gravações reamostradas para {args.hz:g} Hz por interpolação linear)")

    lo, up = args.faixa if args.faixa else (Config().lower, Config().upper)
    cfg = Config(upper=up, lower=lo, hysteresis=args.histerese,
                 dwell_ms=args.permanencia[0], neutral_dwell_ms=args.permanencia[1], z_tiebreak=args.z)
    relatorio_comparativo(gravacoes, cfg)
    if args.erros:
        contexto_dos_erros(gravacoes, cfg)
    if args.varredura:
        varredura(gravacoes)


if __name__ == "__main__":
    main()
