# Classificador de movimentos (TFLite) — A.2.2

Treino do classificador dos 6 passos de forró do escopo do TCC — balanço, base 1,
base 2, abertura, giro invertido (base 3) e giro em linha (base 4) — a partir das
gravações de pose coletadas pelo app. O cronograma (atividade A.2.2) pede acurácia
documentada; a seção [Resultado](#resultado) é essa documentação.

Mesma estrutura do módulo de postura
([`training/posture_classifier/`](../posture_classifier/README.md)): `data/` e
`models/` são ignorados pelo git, porque são dados corporais de participantes e
este repositório é público.

## Resultado

Validação **leave-one-participant-out** nos 7 participantes da coleta de
04/11/2026: cada janela é classificada por um modelo que nunca viu nenhuma
gravação daquela pessoa.

| | por janela | por gravação (voto majoritário) |
|---|---|---|
| Chute na classe majoritária | 0,208 | — |
| Teste exploratório anterior a este módulo (janela de 3 s) | 0,776 | — |
| **Modelo embarcável (TFLite, `keras/mlp_largo`)** | **0,939** | **0,953** (41/43) |
| Teto medido (random forest, não embarcável) | 0,946 | 0,953 (41/43) |

f1 por passo do modelo embarcável: giro invertido 1,00 · balanço 0,99 ·
base 1 0,98 · base 2 0,97 · giro em linha 0,88 · abertura 0,83.

Configuração exportada: janela de 4,5 s com 5 sub-intervalos ordenados (`C@4.5s`),
**sem calibração do yaw** — ver [A calibração do yaw](#a-calibração-do-yaw-medida-e-descartada).

### O que nestes números é robusto e o que não é

`train.py` ativa `enable_op_determinism()` do TensorFlow, então **uma execução é
reproduzível**: mesma entrada, mesma saída. Isso não é o mesmo que estabilidade.

As três arquiteturas Keras ficam dentro de 4 pontos uma da outra (0,898 / 0,913
/ 0,939), e já se viu esse ranking mudar por motivo cosmético: renomear as chaves
de gravação — que não altera a partição dos participantes — trocou a arquitetura
vencedora numa execução anterior. Determinismo garante reprodutibilidade dada a
mesma entrada, não invariância à **ordem** dela.

Então trate a escolha da arquitetura como pouco sustentada e a faixa como o
resultado: **~0,91–0,94 fora da amostra**, contra 0,776 do teste exploratório e
0,208 do chute. É essa comparação que sustenta a atividade A.2.2, não a terceira
casa decimal.

**Abertura × giro em linha melhorou de verdade.** Era o par que se confundia
mutuamente (f1 0,47 e 0,62 no teste exploratório). Um classificador binário só
dos dois passos, sob LOPO, acerta 0,939 — contra 0,500 de chute. A confusão
residual do modelo completo continua concentrada nesse par, e em dois
participantes específicos (ver [Onde ainda erra](#onde-ainda-erra)).

### Por que melhorou

Não foi janela maior nem modelo maior. Foram duas correções no sinal, as duas com
causa mecânica, medidas isoladamente pela comparação entre as estratégias A e B
na varredura:

1. **Normalizar pelo comprimento do tronco, não pelo span de ombros.** O span de
   ombros projetado na imagem tende a zero quando o dançarino fica de perfil.
   Dividir coordenadas por ele explode as features exatamente nos quadros de
   rotação — que são os quadros que distinguem abertura de giro em linha. O
   comprimento do tronco (quadril → ombro) é aproximadamente invariante à
   rotação em torno do eixo vertical. Só essa troca levou 0,789 → 0,927 (A → B,
   floresta, janela de 4,5 s).

2. **Usar o deslocamento de ombros com sinal, não o módulo.** A razão
   `span/tronco` em módulo dobra a curva de rotação no zero: uma rotação suave
   vira uma oscilação de aparência aleatória, e a média dela não distingue nada.
   O `dx` assinado de uma abertura a 10 Hz é
   `+0,12 +0,52 +0,85 +0,89 +0,73 +0,07 −0,32 −0,80 −0,90 …` — contínuo e físico.

A terceira melhora, menor, vem de **preservar a ordem temporal** (estratégia C):
a janela é fatiada em 5 sub-intervalos e cada um entra com as próprias
estatísticas, na ordem. É o que permite codificar "a rotação continua depois da
3ª pisada", que é a diferença de domínio entre os dois passos — o giro em linha
tem 5 pisadas e roda até fechar as 5, a abertura tem 3 marcações e logo vem a
pausa. Ganho de 0,927 → 0,946 na floresta (B → C).

### Onde ainda erra

- **A lacuna até o teto praticamente fechou: 0,946 contra 0,939.** Com o modelo
  calibrado essa diferença era de 0,068, e a destilação da floresta para a rede
  estava listada como próximo passo. Deixou de ser prioridade: não há mais 7
  pontos a recuperar, há 0,7.
- **A abertura segue sendo o ponto fraco.** No modelo embarcável tem f1 0,83, e
  19 das suas 111 janelas vão para giro em linha; na volta, 17 das 153 janelas de
  giro em linha vão para abertura. Esse par concentra quase toda a confusão
  residual — nenhuma janela de abertura ou de giro em linha é confundida com os
  passos rotacionalmente parados (balanço, base 1, base 2).
- **A dispersão entre dobras caiu, mas 7 pessoas continuam sendo 7 pessoas.** No
  embarcável a média de 0,939 vem de dobras entre 0,904 e 0,990 — bem mais
  apertado que os 0,756–0,962 do modelo calibrado, e esse é o sinal mais claro de
  que a calibração por gravação atrapalhava a generalização. Ainda assim, com 7
  participantes uma dobra ruim é 14% do resultado, e "funciona" e "funciona para
  quem dança parecido com o treino" não se distinguem com esta amostra.
- **A auditoria não encontrou rótulo otimista.** As 14 gravações dos dois passos
  difíceis foram inspecionadas por descritores de domínio (rotação total,
  inversões por segundo, desaceleração no fim). Giro em linha mede 3,0–4,3 rad/s
  de rotação contra 1,35–2,90 da abertura, sem nenhuma gravação de um passo
  caindo na faixa típica do outro — as execuções são distintas e os rótulos se
  sustentam. Uma gravação chama atenção: `horario_0/mov_abertura/r4`, com 0,13
  inversões/s contra 0,89–1,38 das outras aberturas e um terço do tempo quase
  parada. Parece execução lenta ou hesitante, e vale conferir no vídeo.
- **Segmentar por ciclo do movimento foi pior que janela de duração fixa**
  (0,936 contra 0,946). O motivo é que o ciclo tem de ser *estimado* do sinal: o
  BPM da sessão não está no dataset. A estimativa por autocorrelação erra feio em
  algumas gravações (1,6 s e 4,9 s para o mesmo passo). Com o BPM gravado no
  rótulo, essa estratégia passa a usar um valor conhecido e merece nova medição.

### O que falta para o número subir

Em ordem de retorno esperado:

1. **Mais participantes.** 7 pessoas, uma execução de cada passo por pessoa. É a
   limitação dominante: a variação entre dobras (0,90–0,99) continua da mesma
   ordem que a diferença entre as melhores configurações.
2. **Gravar o BPM e o participante no rótulo.** O sufixo atual é
   `|r<n>s<i>t<ms>`; `|p<id>r<n>s<i>t<ms>b<bpm>` resolve a segmentação de
   participante na origem (hoje depende de um corte manual por horário) e troca o
   ciclo estimado por conhecido. Vive dentro da coluna `Rotulo`, então não mexe no
   contrato de 38 colunas da planilha. Ver `CaptureLabel.kt`.
3. **Segunda câmera ou outro ângulo.** Todas as 43 gravações são de frente. A
   rotação é justamente o que uma câmera frontal mede pior, e é o discriminador do
   par difícil.
4. **Destilação da floresta para a rede** — hoje de baixo retorno, com a lacuna
   em 0,007. Só volta à mesa se mais dados reabrirem a diferença.

## Fluxo de ponta a ponta

1. **Coletar no app** — na tela de Treino, selecione um passo no seletor de
   rótulo e use o botão "Gravar movimento". Amostra a 10 Hz e envia cada amostra
   para a planilha (ver [`SheetsUploader.kt`](../../app/src/main/java/com/dancaai/app/export/SheetsUploader.kt)
   e `local.properties`). Grave pelo menos 10 s por passo; abaixo de 5 s a
   gravação é descartada no carregamento, por não conter um ciclo completo.

2. **Instalar as dependências** (uma vez):

   ```bash
   cd training/movement_classifier
   python -m venv .venv
   .venv\Scripts\activate
   pip install -r requirements.txt
   ```

3. **Conferir o dataset** antes de treinar:

   ```bash
   python load_data.py
   ```

   Sem argumento, baixa o CSV público da planilha. Imprime uma linha por
   gravação com amostras, duração, taxa de amostragem e maior buraco, e congela
   um snapshot em `data/`. **A taxa é o teste de sanidade da segmentação**: o app
   amostra a 10 Hz, então qualquer gravação muito acima disso significa que duas
   gravações distintas foram fundidas numa chave (ver [Armadilhas](#armadilhas)).
   Para usar um CSV já baixado: `python load_data.py data/capturas.csv`.

4. **Treinar e avaliar:**

   ```bash
   python train.py
   ```

   Roda a varredura das quatro estratégias de janelamento × dois modelos, sempre
   sob LOPO, imprime matriz de confusão, f1 por passo, voto majoritário por
   gravação, acurácia por dobra, a auditoria do par difícil e o controle de
   calibração. Salva o melhor modelo em `models/movement_classifier.keras` e a
   ordem das features em `models/movement_classifier_meta.json`.

5. **Exportar para TFLite:**

   ```bash
   python export_tflite.py
   ```

   Gera `models/movement_classifier.tflite`, confere que o modelo convertido
   responde igual ao Keras e imprime o contrato de integração — a ordem exata das
   entradas e das saídas, que é o que o lado Kotlin precisa reproduzir.

6. **Regerar o vetor-ouro** sempre que o modelo mudar:

   ```bash
   python vetor_ouro.py
   ```

   Grava `app/src/test/resources/golden_vector_movimento.txt`, que o
   `MovementFeaturesTest` do app usa para conferir que o Kotlin monta as 182
   features na mesma ordem e com as mesmas fórmulas. A janela sai dos metadados
   do modelo exportado, não de uma constante: quando a configuração vencedora
   mudou de 3,0 s para 4,5 s, um vetor-ouro de janela fixa continuaria passando e
   validaria o app contra um tamanho que o modelo não usa mais.

   O vetor-ouro é calculado sobre uma trajetória **sintética**, definida por
   fórmula. Trinta e tantos quadros de pontos corporais de um participante são
   dado de pessoa, e este repositório é público.

## Como o dataset é interpretado

As 38 colunas da planilha e o formato do rótulo estão documentados no
[README do projeto](../../README.md#gravação-de-movimento). O que este módulo
acrescenta:

- **Uma amostra do classificador é uma janela de tempo, não uma pose.** Balanço,
  giro invertido e giro em linha passam pelas mesmas poses em instantes
  diferentes; o que separa é a trajetória e a posição dela no compasso.
- **O eixo de tempo é o `t<ms>` do rótulo, não a coluna `Timestamp`.** O
  Timestamp tem resolução de 1 segundo e a gravação amostra a 10 Hz, então dez
  amostras compartilham o mesmo valor. O Timestamp serve só para segmentar
  participante.
- **A identidade de uma gravação é a tripla (participante, passo, `r`).** O
  contador `r` do [`MovementRecorder`](../../app/src/main/java/com/dancaai/app/analysis/MovementRecorder.kt)
  reinicia a cada participante, então `mov_balanco|r1` existe sete vezes na
  planilha.
- **Janelas da mesma gravação não são exemplos independentes.** Com passo de
  0,5 s, janelas vizinhas são quase a mesma coisa. Agrupar por participante na
  validação resolve, porque uma gravação pertence a um participante só.

### Armadilhas

Duas, já corrigidas em `load_data.py`, que invalidam o resultado se voltarem:

1. **A segmentação por intervalo de 5 minutos funde dois participantes.** Na
   coleta de 04/11/2026 duas pessoas se seguiram com menos de 5 minutos de
   intervalo. Com o `r` repetido entre elas, `mov_base1/r2` juntava a gravação de
   uma às 21:09:49 com a da outra às 21:14:12, numa chave só. Daí o corte manual
   em `2026-11-04 21:13:30` (`CORTES_MANUAIS`), que separa as duas e fecha em
   **7 participantes, 44 gravações**. Sem o corte, duas pessoas atravessam treino
   e teste e o número mede memorização. O sintoma é a taxa aparente: 18–20 Hz em
   vez de 10 Hz.
2. **Uma gravação degenerada.** `horario_0/mov_giro_linha/r6` tem 2,2 s e 23
   amostras — menos de um ciclo até no andamento mais lento que o metrônomo
   permite (60 BPM). É descartada, deixando **43 gravações e 5.444 amostras**.

### Participante vindo do rótulo

O app passou a gravar o participante no rótulo, no segmento `|p<NN>` entre o
ângulo e o metadado de quadro (ver `ParticipantId` em `CaptureLabel.kt`):

```
mov_giro_linha@frente|p07|r2s014t0467     com o campo
mov_giro_linha@frente|r2s014t0467         formato anterior, ainda válido
```

`load_data.py` **prefere o campo quando ele existe** e cai no heurístico de
horário quando não — as 44 gravações de 04/11/2026 são anteriores ao campo. Os
dois ficam em espaços de nomes distintos (`p07` contra `horario_3`), igual a
`modelo_postura/treinar_multilabel.py`, para que um grupo vindo do rótulo nunca
se confunda com um grupo inferido do horário.

O segmento é opcional no `PADRAO_ROTULO`, e tem de continuar sendo: se o padrão
passasse a exigi-lo, as gravações antigas deixariam de casar e simplesmente
desapareceriam do relatório, sem erro. A lista `CORTES_MANUAIS` é a mesma dos
scripts de postura e precisa ser mantida em sincronia com eles — os dois leem a
mesma planilha, e se divergirem passam a discordar sobre quem é quem.

### A calibração do yaw: medida e descartada

Havia a opção de calibrar o sinal de rotação em radianos, dividindo `|dx|` pelo
percentil 95 da própria gravação e aplicando `arcsin`. Ela foi **removida do
pipeline** (`CALIBRAR_YAW = False` em `train.py`), por duas razões que se somam.

**Mecânica.** A calibração precisa de uma estatística da gravação INTEIRA, e em
tempo de inferência o app não tem isso — tem a janela corrente de 4,5 s de uma
execução que ainda não terminou. Qualquer aproximação (percentil da janela,
referência do enquadramento inicial) é uma grandeza diferente da que o modelo
viu no treino, e divergência treino/inferência não dá erro: dá resposta errada no
celular e certa no notebook.

**Medida.** Calibrar não ajudava o modelo que de fato embarca — atrapalhava:

| | janela | gravação | pior dobra |
|---|---|---|---|
| Embarcável, com calibração | 0,891 | 0,907 | 0,756 |
| **Embarcável, sem calibração** | **0,939** | **0,953** | **0,904** |
| Floresta (teto), com calibração | 0,959 | 1,000 | 0,850 |
| Floresta (teto), sem calibração | 0,946 | 0,953 | — |

A leitura: a calibração **ajudava a floresta e atrapalhava a rede**. Ela injeta a
escala de cada gravação na feature, e a floresta aproveita isso para separar
gravações — o que não transfere para uma pessoa nova. O salto da pior dobra, de
0,756 para 0,904, é o sinal mais direto disso.

`controle_de_calibracao()` continua medindo as duas versões em cada execução, na
configuração vencedora, para a escolha permanecer visível em vez de virar
folclore.

## Estratégias de janelamento avaliadas

Todas medidas sob a mesma validação, para a comparação ser contra a mesma régua.
Detalhe de cada uma no cabeçalho de [`features.py`](features.py).

| | descrição | melhor acurácia por janela |
|---|---|---|
| A | janela fixa, estatísticas sobre coordenadas normalizadas pelo **span de ombros** — reprodução do teste exploratório | 0,794 |
| B | idem, sobre os sinais normalizados pelo **tronco**, com yaw assinado | 0,927 |
| C | **ordem temporal preservada**: 5 sub-intervalos, cada um com as próprias estatísticas | **0,946** |
| D | janela de **um ciclo** do movimento, estimado do sinal, reamostrada | 0,936 |

## Próximos passos (fora deste módulo)

- Embarcar o `.tflite` em `app/src/main/assets/` e rodar inferência via
  `org.tensorflow:tensorflow-lite`, montando as features em Kotlin na ordem que
  `export_tflite.py` imprime. **O app não deve decidir por uma janela só**: a
  acurácia por gravação acima vem de voto majoritário entre as janelas da
  execução, e é essa a decisão que corresponde à experiência de uso.
- Modo **Livre** (diz qual passo está sendo feito e se está correto) e modo
  **Desafio** (sequência pré-definida, diz se acertou), previstos no plano de
  trabalho. O modo Livre precisa de um mínimo de confiança antes de mostrar um
  passo, senão a tela troca de rótulo a cada janela durante a transição.
