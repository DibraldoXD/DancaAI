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

**App e design**
- Telas em Jetpack Compose: Onboarding, Home, Nova Sessão, Treino, Resultados, Histórico, Perfil ([`ui/screens`](app/src/main/java/com/dancaai/app/ui/screens))
- Design system próprio (cores, tipografia, componentes reutilizáveis) ([`ui/theme`](app/src/main/java/com/dancaai/app/ui/theme), [`ui/components`](app/src/main/java/com/dancaai/app/ui/components))
- Navegação entre telas ([`DancaApp.kt`](app/src/main/java/com/dancaai/app/navigation/DancaApp.kt))

## O que ainda falta desenvolver

- **Sincronização ritmo × passo**: comparar o timing dos tiques do metrônomo com os eventos de transferência de peso pra gerar um score de ritmo real (hoje os dois módulos existem separadamente, mas não conversam entre si)
- **Reconhecimento de movimentos**: classificação dos passos do forró (Balanço, Base 1/2, Abertura, Giros) via modelo embarcado treinado com vídeos de dançarinos — ainda não implementado
- **Modos Livre e Desafio** da execução de movimentos
- **DTW (Dynamic Time Warping)**: comparação da sequência executada pelo usuário com um template de referência
- **Templates de referência**: gravação/carregamento de movimentos de instrutores
- **Scoring real de sessão**: os scores exibidos em Resultados/Histórico ainda são mockados ([`MockRepository.kt`](app/src/main/java/com/dancaai/app/data/MockRepository.kt))
- **Modelo de classificação de postura**: hoje a postura é validada por regras geométricas; o plano prevê complementar com um modelo embarcado treinado
- Migração de telas com estado mais complexo (ex. `TrainingScreen`) para `ViewModel`
- Unificação das constantes de threshold de postura, hoje duplicadas entre `PostureValidator.kt`, `OverlayView.kt` e `TrainingScreen.kt`

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
│   ├── data/              # Modelos e repositório mockado
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

### Testes

```bash
./gradlew testDebugUnitTest
```

## Fluxo de desenvolvimento

Diretrizes de arquitetura, testes, commits e branches estão documentadas em [`CLAUDE.md`](CLAUDE.md).
