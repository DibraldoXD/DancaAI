package com.dancaai.app.analysis.movement

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Classificador embarcado dos 6 passos de forró.
 *
 * Carrega o `.tflite` treinado por `training/movement_classifier/` e classifica
 * uma janela de 3 s de sinais de pose. **A normalização está dentro do grafo**:
 * as features vão cruas, sem escalar, e replicar média/desvio aqui à mão seria
 * aplicá-las duas vezes.
 *
 * O modelo não vai para o repositório público
 * ───────────────────────────────────────────
 * O `.tflite` é treinado sobre dados corporais de participantes, e o
 * `.gitignore` do projeto barra esse tipo de artefato enquanto a publicação não
 * estiver amparada pelo consentimento e pelo CEP. O arquivo vive no repositório
 * privado `FelipeMDR/DancaAI-modelos` e é copiado para `app/src/main/assets/`
 * por quem compila.
 *
 * Por isso [load] devolve `null` em vez de lançar: uma instalação feita a partir
 * do repositório público, sem o modelo, deve rodar sem o módulo de execução de
 * movimentos — não quebrar na abertura da tela de treino.
 */
class MovementClassifier private constructor(
    private val interpreter: Interpreter,
) : Closeable {

    private val input = ByteBuffer
        .allocateDirect(MovementFeatures.SIZE * BYTES_PER_FLOAT)
        .order(ByteOrder.nativeOrder())

    private val output = Array(1) { FloatArray(DanceStep.entries.size) }

    /**
     * Classifica uma janela. `null` quando a janela é curta demais para render
     * features — o chamador deve seguir sem veredito, não mostrar um passo.
     */
    fun classify(window: List<FrameSignals>): StepPrediction? {
        if (window.size < 2) return null
        val features = MovementFeatures.vectorOf(window)

        input.rewind()
        for (value in features) input.putFloat(value)
        input.rewind()

        interpreter.run(input, output)
        val probabilities = output[0].copyOf()

        var best = 0
        for (i in probabilities.indices) if (probabilities[i] > probabilities[best]) best = i
        return StepPrediction(
            step = DanceStep.entries[best],
            confidence = probabilities[best],
            probabilities = probabilities,
        )
    }

    override fun close() = interpreter.close()

    companion object {
        private const val TAG = "MovementClassifier"
        private const val BYTES_PER_FLOAT = 4
        const val ASSET_NAME = "movement_classifier.tflite"

        /**
         * Carrega o modelo dos assets, ou devolve `null` se não estiver lá ou
         * não casar com o que este código espera.
         *
         * A conferência de forma não é zelo excessivo: o modelo recebe um vetor
         * de floats sem nomes, então um `.tflite` de outra versão — com outro
         * número de features ou de classes — responderia normalmente, com
         * valores errados e sem erro em lugar nenhum. Melhor rodar sem o módulo
         * do que mostrar um passo inventado.
         */
        fun load(context: Context): MovementClassifier? {
            val interpreter = try {
                context.assets.openFd(ASSET_NAME).use { descriptor ->
                    descriptor.createInputStream().use { stream ->
                        val buffer = stream.channel.map(
                            FileChannel.MapMode.READ_ONLY,
                            descriptor.startOffset,
                            descriptor.declaredLength,
                        )
                        Interpreter(buffer)
                    }
                }
            } catch (e: Exception) {
                Log.i(
                    TAG,
                    "Modelo '$ASSET_NAME' não disponível — o módulo de execução de " +
                        "movimentos fica desligado. Copie o arquivo do repositório " +
                        "privado de modelos para app/src/main/assets/ (ver README).",
                    e,
                )
                return null
            }

            val inputShape = interpreter.getInputTensor(0).shape()
            val outputShape = interpreter.getOutputTensor(0).shape()
            val features = inputShape.last()
            val classes = outputShape.last()
            if (features != MovementFeatures.SIZE || classes != DanceStep.entries.size) {
                Log.e(
                    TAG,
                    "Modelo incompatível: espera ${MovementFeatures.SIZE} features e " +
                        "${DanceStep.entries.size} classes, o arquivo tem $features e $classes. " +
                        "Provavelmente é de outra versão do treino — não vou usá-lo.",
                )
                interpreter.close()
                return null
            }
            return MovementClassifier(interpreter)
        }
    }
}
