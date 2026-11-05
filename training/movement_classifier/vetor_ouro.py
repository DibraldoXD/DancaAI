"""
Gera o vetor-ouro que amarra o Kotlin ao Python — Dança AI (TCC02, A.2.2).

O modelo recebe 182 floats sem nome. Se o lado Android montar as features em
outra ordem, ou usar desvio amostral onde o treino usou populacional, **o modelo
responde normalmente, com valores errados, e nada acusa**: não há exceção, não há
log, só um passo errado na tela. Este arquivo existe para que esse erro apareça
como teste vermelho.

Como funciona
─────────────
Uma trajetória SINTÉTICA, definida pela fórmula em [amostras_sinteticas], passa
pelo mesmo `features.py` que treinou o modelo. As 182 features resultantes vão
para um JSON lido por `MovementFeaturesTest.kt`, que constrói a mesma trajetória
pela mesma fórmula em Kotlin e compara número por número.

Por que sintética, e não uma janela real
────────────────────────────────────────
Uma janela real são ~30 quadros de pontos corporais de um participante, e este
repositório é público. Dado de pessoa não entra aqui nem em pedaço pequeno. A
trajetória sintética testa exatamente o que pode estar errado — ordem das
features, fórmula de cada estatística, fronteira dos sub-intervalos, alinhamento
das velocidades — sem publicar nada de ninguém.

A tolerância do teste não é frouxidão: o Python calcula em float64 e o Kotlin em
float32, então divergência na última casa é esperada. Um erro de ORDEM, que é o
que este teste caça, produz diferença de ordem de grandeza, não de casa decimal.

Uso:
    python vetor_ouro.py            # grava o JSON em app/src/test/resources/
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import pandas as pd

import features

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(errors="replace")

# Texto simples, não JSON, de propósito: num teste unitário JVM o `org.json` que
# acompanha o `android.jar` é apenas um stub que lança exceção, então ler JSON no
# teste exigiria uma dependência nova só para isso. `nome=valor` por linha o
# Kotlin lê com `split`.
DESTINO = (
    Path(__file__).resolve().parents[2]
    / "app" / "src" / "test" / "resources" / "golden_vector_movimento.txt"
)

# A janela NÃO é fixa aqui: vem dos metadados do modelo exportado.
#
# Foi exatamente esta a deriva que custou tempo: a configuração vencedora mudou
# de C@3,0s para C@4,5s quando a calibração do yaw foi desligada, e um vetor-ouro
# com janela fixa continuaria passando — validando o Kotlin contra um tamanho de
# janela que o modelo embarcado não usa mais. Ler do `meta.json` faz o vetor-ouro
# acompanhar o modelo por construção.
META = Path(__file__).parent / "models" / "movement_classifier_meta.json"
INTERVALO_S = 0.1


def duracao_janela_s() -> float:
    if not META.exists():
        raise SystemExit(
            f"{META.name} não encontrado — rode train.py antes, para o vetor-ouro "
            "poder usar a mesma janela do modelo exportado."
        )
    janela = json.loads(META.read_text(encoding="utf-8"))["janela"]
    if janela == "ciclo":
        raise SystemExit(
            "O modelo exportado usa janela por ciclo do movimento, cuja duração "
            "varia por gravação. O vetor-ouro (e o MovementWindow do app) "
            "pressupõem janela de duração fixa; os dois precisam mudar juntos."
        )
    return float(janela.rstrip("s"))


def amostras_sinteticas(n_amostras: int) -> pd.DataFrame:
    """Trajetória determinística, com a mesma fórmula que o teste Kotlin usa.

    Não imita um passo de forró, e não precisa: o que ela precisa exercitar é
    cada ramo do cálculo. Daí as escolhas abaixo.
    """
    linhas = []
    for i in range(n_amostras):
        t = i * INTERVALO_S
        # Rotação que CRUZA O ZERO e inverte de sinal, para o teste pegar quem
        # tomar o módulo em vez do valor assinado.
        #
        # A amplitude é escolhida para o `yaw` resultante cair na faixa real:
        # meio-span de 0,09 dá span de ombros de 0,18 sobre tronco de 0,20, ou
        # seja |yaw| até ~0,9 — a mesma ordem medida nas 7 pessoas da coleta. Um
        # vetor-ouro numa faixa numérica que a produção nunca vê testaria o
        # código fora do regime em que ele roda.
        giro = 0.09 * math.sin(2.0 * math.pi * t / 2.4)
        # Fases diferentes em cada par de membros, para que uma troca de colunas
        # (pes_dx por pes_dy, por exemplo) mude o resultado.
        passo = 0.18 * math.sin(2.0 * math.pi * t / 1.2 + 0.7)
        sobe = 0.04 * math.cos(2.0 * math.pi * t / 1.2)

        ombro_meio_x, ombro_meio_y = 0.50 + 0.02 * giro, 0.40 - sobe
        quadril_meio_x, quadril_meio_y = 0.50 + 0.01 * passo, 0.60
        linhas.append(
            {
                "OMB-E_x": ombro_meio_x - giro, "OMB-E_y": ombro_meio_y + 0.01 * passo, "OMB-E_z": -0.30,
                "OMB-D_x": ombro_meio_x + giro, "OMB-D_y": ombro_meio_y - 0.01 * passo, "OMB-D_z": -0.28,
                "QDR-E_x": quadril_meio_x - 0.08, "QDR-E_y": quadril_meio_y, "QDR-E_z": -0.01,
                "QDR-D_x": quadril_meio_x + 0.08, "QDR-D_y": quadril_meio_y, "QDR-D_z": 0.01,
                "JOE-E_x": quadril_meio_x - 0.09, "JOE-E_y": 0.74 + 0.5 * sobe, "JOE-E_z": -0.10,
                "JOE-D_x": quadril_meio_x + 0.09, "JOE-D_y": 0.74 - 0.5 * sobe, "JOE-D_z": 0.02,
                "TRN-E_x": quadril_meio_x - 0.07 - passo, "TRN-E_y": 0.86 + sobe, "TRN-E_z": 0.18,
                "TRN-D_x": quadril_meio_x + 0.07 + passo, "TRN-D_y": 0.86 - sobe, "TRN-D_z": 0.31,
                "t_s": t,
                # Uma "gravação" só: as velocidades têm de ser derivadas em série
                # contínua, como no treino.
                "participante": "sintetico",
                "passo": "mov_balanco",
                "gravacao": "sintetico/mov_balanco/r1",
            }
        )
    return pd.DataFrame(linhas)


def main(calibrar: bool) -> None:
    duracao = duracao_janela_s()
    # Uma amostra ALÉM da janela. No treino as velocidades foram derivadas sobre a
    # gravação inteira e só depois fatiadas, então a primeira amostra de uma janela
    # tem velocidade calculada contra a amostra anterior a ela. O MovementWindow do
    # app guarda essa amostra extra para reproduzir isso, e o vetor-ouro tem de
    # exercitar esse alinhamento.
    n_amostras = round(duracao / INTERVALO_S) + 1
    mov = amostras_sinteticas(n_amostras)
    sinais = features.adicionar_yaw_e_velocidades(
        features.sinais_por_quadro(mov), calibrar=calibrar
    )

    # A janela é as 30 últimas amostras; a primeira das 31 existe só para dar
    # velocidade à primeira da janela, igual ao buffer do app.
    janela = sinais.sort_values("t_s").iloc[1:]
    vetor = features._ordenado(janela, features.SINAIS_CORPO, features.SUBINTERVALOS)
    nomes = list(vetor.keys())

    linhas = [
        "# Gerado por training/movement_classifier/vetor_ouro.py.",
        "# NÃO edite à mão: rode o script.",
        "# A trajetória é sintética — nenhum dado de participante aqui.",
        f"#meta calibracao_yaw={str(calibrar).lower()}",
        f"#meta n_amostras_janela={len(janela)}",
        f"#meta intervalo_s={INTERVALO_S}",
        f"#meta duracao_janela_s={duracao}",
    ]
    # Repr completo do float, sem arredondar: arredondar aqui criaria uma
    # divergência artificial entre os dois lados e comeria a margem da
    # tolerância, que existe para a diferença float64/float32 e nada mais.
    linhas += [f"{nome}={float(vetor[nome])!r}" for nome in nomes]

    DESTINO.parent.mkdir(parents=True, exist_ok=True)
    DESTINO.write_text("\n".join(linhas) + "\n", encoding="utf-8")

    print(f"Vetor-ouro gravado em {DESTINO}")
    print(f"  features: {len(nomes)} (esperado 182)")
    print(f"  calibração do yaw: {calibrar}")
    print(f"  janela: {duracao} s ({len(janela)} amostras + 1 anterior)")
    print(f"  primeiras 4: {[round(float(vetor[n]), 6) for n in nomes[:4]]}")
    print(f"  últimas 4:   {[round(float(vetor[n]), 6) for n in nomes[-4:]]}")
    if len(nomes) != 182:
        raise SystemExit(
            f"Esperava 182 features e saíram {len(nomes)} — a estratégia de "
            "janelamento mudou, e o lado Kotlin precisa mudar junto."
        )


if __name__ == "__main__":
    # A calibração do yaw depende de estatística da gravação inteira, que o app
    # não tem em tempo de inferência. O padrão aqui é o modelo SEM calibração,
    # que é o que se embarca; ver o README do módulo.
    main(calibrar="--com-calibracao" in sys.argv)
