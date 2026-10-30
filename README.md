# Dança AI

Aplicativo Android de treino solo de dança de salão que usa visão computacional para analisar os movimentos do usuário em tempo real e dar feedback simples e direto — como um espelho de estúdio com professor embutido.

Este repositório é o TCC (Trabalho de Conclusão de Curso) de **Felipe Santana Medeiros** e **João Luiz de Miranda Cilli**, alunos de Engenharia de Computação da Universidade Federal de Itajubá (UNIFEI). O plano de trabalho completo (revisão bibliográfica, objetivos e metodologia) está em [`ECO_plano_de_trabalho_TCC01/`](ECO_plano_de_trabalho_TCC01).

O estilo de dança escolhido como escopo é o **forró**.

## Como funciona

O celular fica apoiado num tripé/suporte, apontado para o usuário (~2 m de distância). A câmera captura o vídeo, o [MediaPipe Pose Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker) extrai 33 pontos corporais por frame, e esses pontos alimentam os módulos de análise abaixo. Tudo roda **on-device** (nada é enviado pra servidor).

O app se organiza em quatro pilares, descritos no plano de trabalho:

- **Transferência de peso** — classifica o peso do usuário entre perna esquerda/direita/neutro a partir dos pontos da parte inferior do corpo, com feedback visual imediato.
- **Postura** — avalia o alinhamento de tronco e ombros a partir dos pontos da parte superior do corpo, sinalizando quando o usuário está desalinhado.
- **Ritmo** — o usuário configura um metrônomo (BPM); a ideia final é comparar o tempo do metrônomo com o tempo real da troca de peso pra avaliar se o usuário está dançando no compasso.
- **Execução de movimentos** — reconhecer os passos do forró (Balanço, Base 1, Base 2, Abertura, Giro Invertido, Giro em Linha) em dois modos: Livre (diz qual passo está sendo feito) e Desafio (segue uma sequência pré-definida).

## O que já está pronto

**Visão computacional e biomecânica**
- Captura de câmera em tempo real com CameraX + alternância frontal/traseira ([`PoseCameraView.kt`](app/src/main/java/com/dancaai/app/camera/PoseCameraView.kt))
- Detecção de pose com MediaPipe (33 landmarks), com fallback gracioso quando a lib nativa não está disponível (ex.: emulador) ([`PoseLandmarkerHelper.kt`](app/src/main/java/com/dancaai/dancaai/PoseLandmarkerHelper.kt))
- Cálculo de ângulos articulares (joelhos, quadris, ombros, cotovelos) ([`AngleCalculator.kt`](app/src/main/java/com/dancaai/dancaai/AngleCalculator.kt))
- Validação de postura (nivelamento de ombros, tronco encurvado, alinhamento lateral) ([`PostureValidator.kt`](app/src/main/java/com/dancaai/dancaai/PostureValidator.kt))
- Detecção de transferência de peso esquerda/direita/neutro ([`StepCounter.kt`](app/src/main/java/com/dancaai/dancaai/StepCounter.kt))
- Overlay do esqueleto sobre o vídeo, com codificação verde/vermelho por status ([`OverlayView.kt`](app/src/main/java/com/dancaai/dancaai/OverlayView.kt))
- Guia de posicionamento de câmera ([`CameraGuide.kt`](app/src/main/java/com/dancaai/dancaai/CameraGuide.kt))

**Ritmo**
- Metrônomo configurável (BPM, 60–200) com motor de áudio próprio via `AudioTrack` — scheduling orientado a amostras (sem drift de tempo), compasso fixo de 4 tempos com tique diferenciado na pausa/quebra, BPM persistido entre sessões ([`Metronome.kt`](app/src/main/java/com/dancaai/app/audio/Metronome.kt))
- Sincronização ritmo × transferência de peso: cada troca de perna de apoio é julgada contra a grade de batidas do metrônomo (no tempo / adiantado / atrasado, com o desvio em ms), com os dois relógios na mesma base e o instante *audível* do tique estimado pela posição de reprodução ([`RhythmAnalyzer.kt`](app/src/main/java/com/dancaai/app/analysis/RhythmAnalyzer.kt))

**App e design**
- Telas em Jetpack Compose: Onboarding, Home, Nova Sessão, Treino, Resultados, Histórico, Detalhe de sessão, Perfil ([`ui/screens`](app/src/main/java/com/dancaai/app/ui/screens))
- Design system próprio (cores, tipografia, componentes reutilizáveis) ([`ui/theme`](app/src/main/java/com/dancaai/app/ui/theme), [`ui/components`](app/src/main/java/com/dancaai/app/ui/components))
- Navegação entre telas ([`DancaApp.kt`](app/src/main/java/com/dancaai/app/navigation/DancaApp.kt))
- Persistência real: histórico de sessões em Room e perfil/preferências em DataStore — nome, nível, contagem de sessões, sequência de dias e espaço ocupado vêm todos dos dados gravados ([`SessionRepository.kt`](app/src/main/java/com/dancaai/app/data/SessionRepository.kt))

## O que ainda falta desenvolver

- **Reconhecimento de movimentos**: classificação dos passos do forró (Balanço, Base 1/2, Abertura, Giros) via modelo embarcado treinado com vídeos de dançarinos — ainda não implementado
- **Modos Livre e Desafio** da execução de movimentos
- **Templates de referência**: gravação/carregamento de movimentos de instrutores
- **Pontuação de sessão**: as telas mostram as medições brutas (proporção de tempo em postura conforme, de trocas corretas, desvio médio de ritmo), não uma nota de 0 a 100. A fórmula de score depende da calibração com praticantes; como o banco guarda só as medições, ela valerá retroativamente para as sessões já gravadas. Até lá, "Score médio" na Home e a média de 30 dias no Histórico ficam em branco
- **Modelo de classificação de postura**: hoje a postura é validada por regras geométricas; o plano prevê complementar com um modelo embarcado treinado
- **Guia de enquadramento no app**: [`CameraGuide.kt`](app/src/main/java/com/dancaai/dancaai/CameraGuide.kt) está implementado, mas só é usado pela `MainActivity` de protótipo, que não é alcançável pela interface
- **Vibração e feedback por voz**: as preferências são persistidas no Perfil, mas a sessão de treino ainda não as consome
- Migração de telas com estado mais complexo (ex. `TrainingScreen`) para `ViewModel`, com o aparato de coleta de dataset atrás de um "modo coleta"

## Stack técnica

- **Kotlin** + **Jetpack Compose** (UI declarativa)
- **CameraX** — captura de câmera
- **MediaPipe Tasks Vision** (Pose Landmarker, modelo `lite`) — pose estimation
- **AudioTrack** (Android SDK) — motor de áudio do metrônomo, sem dependências externas
- Sem injeção de dependência (Hilt/Koin) por ora — escopo de TCC não justifica a complexidade extra
- `minSdk 24`, `targetSdk 35`, `compileSdk 35`

## Estrutura do projeto

```
app/src/main/java/com/dancaai/
├── app/
│   ├── audio/            # Metrônomo (motor de áudio + persistência)
│   ├── camera/           # Integração CameraX + MediaPipe
│   ├── analysis/          # Acumulador de sessão, ritmo, gravação de movimento
│   ├── data/              # Modelos, repositório, Room e DataStore
│   ├── navigation/        # Rotas e grafo de navegação Compose
│   └── ui/
│       ├── components/    # Design system (botões, cards, chips...)
│       ├── screens/        # Telas do app
│       └── theme/          # Cores, tipografia, shapes
└── dancaai/               # Módulo de visão computacional (pose, postura, ângulos)
```

## Como rodar

Pré-requisitos: Android Studio, um **celular físico** (o MediaPipe com delegate de GPU não funciona no emulador — a câmera funciona, mas sem o esqueleto).

```bash
./gradlew installDebug
```

Ou abra o projeto no Android Studio e rode a configuração padrão (`app`).

### Envio de capturas pro Google Sheets (coleta de dados)

Durante a coleta com usuários, a tela de Treino permite registrar capturas de pontos corporais (botão "Registrar" ou captura contínua sincronizada ao metrônomo) e enviá-las automaticamente pra uma planilha do Google Sheets, via [`SheetsUploader.kt`](app/src/main/java/com/dancaai/app/export/SheetsUploader.kt). Essa etapa é temporária: existe pra treinar os modelos de classificação (postura, movimentos) e será desativada quando a coleta terminar.

O envio depende de uma URL de Google Apps Script publicada, que **não é commitada** — precisa ser configurada localmente:

1. Peça a URL do Apps Script já publicado a quem o configurou (o script recebe um POST e escreve a linha na planilha).
2. No arquivo `local.properties` (na raiz do projeto, já ignorado pelo git), adicione:
   ```properties
   SHEETS_WEBHOOK_URL=https://script.google.com/macros/s/SEU_ID_AQUI/exec
   ```
3. Rebuild o app. Sem essa linha, `SheetsUploader.enabled` é `false` e o envio vira um no-op silencioso — a captura continua funcionando localmente (visível na revisão de debug ao encerrar a sessão), só não sobe pra planilha.

O payload enviado é um POST JSON no formato `{"values": [...]}`, com uma linha de 38 colunas nesta ordem fixa (2 de metadado + 11 landmarks × x/y/z + 3 métricas derivadas) (ver `toSheetRow()` em [`TrainingScreen.kt`](app/src/main/java/com/dancaai/app/ui/screens/TrainingScreen.kt)):

```
timestamp, label,
nose.x, nose.y, nose.z,
leftEar.x/y/z, rightEar.x/y/z,
leftShoulder.x/y/z, rightShoulder.x/y/z,
leftHip.x/y/z, rightHip.x/y/z,
leftKnee.x/y/z, rightKnee.x/y/z,
leftAnkle.x/y/z, rightAnkle.x/y/z,
shoulderSpan, zDiff, thresholdOmbrosEncurvados
```

O Apps Script do lado do Google precisa esperar exatamente essas colunas, nessa ordem — se a URL não estiver funcionando, confira primeiro se o script ainda está publicado com acesso "Qualquer pessoa" e se aceita esse formato de `values`.

#### Seletor do que está sendo gravado

A coluna `label` (`Rotulo` na planilha) não é mais descritiva: ela carrega o rótulo de treino, escolhido no seletor da tela de Treino (pílula "GRAVANDO", acima dos botões de captura). Antes do seletor o app mandava `"Captura #1"` e os rótulos de verdade eram digitados à mão na planilha depois da coleta — origem de erro silencioso, já que uma linha mal rotulada só aparece no treino.

O formato é `assunto@angulo`, com sufixo `|c<ciclo>t<tempo>` nas capturas contínuas. Os valores vêm de [`CaptureLabel.kt`](app/src/main/java/com/dancaai/app/data/model/CaptureLabel.kt):

| Assunto | Token | | Ângulo | Token |
|---|---|---|---|---|
| Postura boa | `post_boa` | | Frente | `frente` |
| Ombro esq. caído | `post_ruim_OE` | | Lado E | `lado_E` |
| Ombro dir. caído | `post_ruim_OD` | | Lado D | `lado_D` |
| Ombros à frente | `post_ruim_OF` | | Costas | `costas` |
| Balanço | `mov_balanco` | | | |
| Base 1 / Base 2 | `mov_base1` / `mov_base2` | | | |
| Abertura | `mov_abertura` | | | |
| Giro invertido (Base 3) | `mov_giro_invertido` | | | |
| Giro em linha (Base 4 / 5 pisadas) | `mov_giro_linha` | | | |
| Teste de envio | `teste` | | | |

Exemplos: `post_ruim_OF@lado_D`, `mov_base1@frente|c3t2` (3º ciclo do metrônomo, 2º tempo do compasso).

#### Gravação de movimento

O botão "Registrar" captura **um instante**. Um passo de forró não se distingue de outro por uma pose isolada — balanço, giro invertido e giro em linha passam pelas mesmas poses em momentos diferentes; o que separa os três é a trajetória. Para isso existe o botão **"Gravar movimento"**: toca para iniciar, executa o passo, toca para parar.

- Amostra a **10 Hz** ([`MovementRecorder.kt`](app/src/main/java/com/dancaai/app/analysis/MovementRecorder.kt)). 30 Hz geraria ~120 requisições num passo de 4 s, e o Apps Script recebe uma linha por POST.
- Cada amostra vira uma linha com sufixo `|r<gravação>s<índice>t<ms>`, por exemplo `mov_giro_linha@frente|r2s014t0467`. O tempo decorrido vai junto do índice porque frames sem pessoa no enquadramento não viram amostra: é o salto no tempo que revela o buraco.
- Só habilita com um **passo** selecionado. Gravar a 10 Hz com rótulo de postura despejaria centenas de amostras de trajetória no dataset de postura, onde contariam como poses.
- Para sozinha aos **30 s** e ao pausar a sessão ou trocar o rótulo, para não misturar dois passos no mesmo bloco `r<n>`.

Os scripts de postura descartam toda linha com sufixo `|r<n>`: são pontos de trajetória, não poses escolhidas, e centenas delas no mesmo bloco dominariam o treino.

Os tokens de postura são exatamente os que os scripts de `modelo_postura/` esperam na coluna `Rotulo`; **renomear um token aqui desalinha o dataset já coletado**. Os scripts separam o assunto do ângulo por `@` logo após carregar o CSV, então linhas antigas (anteriores ao seletor, sem `@`) continuam válidas e entram como ângulo `nao_informado`.

Como a mesma planilha recebe postura, movimento e verificação, os scripts de postura filtram `Rotulo` pelos quatro tokens `post_*` antes de montar o alvo. Sem esse filtro, uma linha `mov_*` ou `teste` entraria como **exemplo de postura boa** — o alvo é montado só por match positivo nos desvios, então o que não casa com nenhum é tratado como postura correta, sem erro e sem aviso.

### Testes

```bash
./gradlew testDebugUnitTest
```

## Fluxo de desenvolvimento

Diretrizes de arquitetura, testes, commits e branches estão documentadas em [`CLAUDE.md`](CLAUDE.md).
