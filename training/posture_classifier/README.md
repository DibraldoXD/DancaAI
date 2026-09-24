# Classificador de postura (TFLite) — A.1.4

Estrutura de treino do classificador embarcado de postura previsto na F.1 do
cronograma do TCC02: "classificador TFLite embarcado treinado sobre coordenadas xyz
de dançarinos qualificados".

## Fluxo de ponta a ponta

1. **Coletar dados no app** — use os botões "Registrar" (captura instantânea) e
   "Contínua" (sincronizada com o metrônomo) na tela de Treino. Cada captura é
   enviada automaticamente pra sua planilha do Google Sheets (ver
   `SheetsUploader.kt` e `local.properties`).

2. **Rotular manualmente na planilha** — adicione uma coluna `Rotulo_Postura` (a
   última, depois de `Threshold_Ombros_Encurvados`) e preencha cada linha com
   `correta` ou `incorreta`, revisando o vídeo/momento da captura. Linhas sem
   rótulo reconhecido são ignoradas no treino.

3. **Exportar CSV** — na planilha: Arquivo → Fazer download → Valores separados por
   vírgula (.csv). Salve em `data/` (esta pasta é ignorada pelo git — os dados não
   vão pro GitHub).

4. **Instalar as dependências** (uma vez):

   ```bash
   cd training/posture_classifier
   python -m venv .venv
   .venv\Scripts\activate          # Windows
   pip install -r requirements.txt
   ```

5. **Conferir o dataset** antes de treinar:

   ```bash
   python load_data.py data/capturas.csv
   ```

   Mostra quantas capturas foram rotuladas e a contagem por classe. Vale ter pelo
   menos algumas dezenas de exemplos de cada classe antes de treinar de verdade.

6. **Treinar:**

   ```bash
   python train.py data/capturas.csv
   ```

   Treina um MLP pequeno (32→16→1, saída sigmoid), com early stopping e relatório
   de acurácia/matriz de confusão no conjunto de teste. Salva o modelo em
   `models/posture_classifier.keras`.

7. **Exportar pra TFLite:**

   ```bash
   python export_tflite.py
   ```

   Gera `models/posture_classifier.tflite`, com quantização dinâmica (menor,
   sem precisar de dataset de calibração).

## Features usadas

O modelo não usa as coordenadas cruas — `load_data.py` normaliza cada landmark
centralizando no ponto médio dos ombros e dividindo pelo span de ombros, igual à
lógica já usada em `PostureValidator.kt` (invariante à distância da câmera e à
estatura da pessoa). Essa mesma normalização precisa ser replicada no lado Android
quando o modelo for embarcado (próximo passo, ainda não implementado).

## Próximos passos (fora desta estrutura)

- Embarcar o `.tflite` no app (`app/src/main/assets/`) e rodar inferência via
  `org.tensorflow:tensorflow-lite` no `PoseCameraView`/`PostureValidator`, em
  paralelo ou substituindo as regras geométricas atuais.
- Documentar a acurácia obtida (exigido no cronograma: "acurácia documentada").
- Repetir a mesma estrutura para o classificador de movimentos (A.2.2, os 6 passos
  do forró) quando chegar a hora — provavelmente em `training/movement_classifier/`.
