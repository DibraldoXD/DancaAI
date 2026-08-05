package com.dancaai.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Motor de metrônomo do módulo de Ritmo.
 *
 * Compasso fixo de 4 tempos: os tiques 1, 2 e 3 soam iguais; o 4º soa diferente,
 * sinalizando a pausa/quebra do forró. O único parâmetro configurável é o [bpm].
 *
 * O agendamento das batidas é feito em número de amostras de áudio, não em tempo
 * de relógio (`Handler.postDelayed`/`Timer`): a posição de reprodução do próprio
 * [AudioTrack] é o "relógio", o que elimina o drift acumulado típico de
 * metrônomos ingênuos — importante porque esses instantes de batida serão a
 * referência para a futura sincronização com a transferência de peso.
 */
class Metronome(
    private val sampleRate: Int = 44_100,
) {
    companion object {
        const val MIN_BPM = 60
        const val MAX_BPM = 200
        const val DEFAULT_BPM = 120

        private const val BEATS_PER_BAR = 4
        private const val PAUSE_BEAT_INDEX = 3 // 4º tempo (0-based) = pausa/quebra
    }

    /** BPM atual, sempre restrito a [MIN_BPM]..[MAX_BPM]. Pode ser alterado com o metrônomo tocando. */
    @Volatile
    var bpm: Int = DEFAULT_BPM
        set(value) { field = value.coerceIn(MIN_BPM, MAX_BPM) }

    /** Chamado na main thread a cada tique, com o índice do tempo dentro do compasso (0..3; 3 = pausa). */
    var onBeat: ((beatIndexInBar: Int) -> Unit)? = null

    @Volatile private var running = false
    private var audioTrack: AudioTrack? = null
    private var workerThread: Thread? = null

    // lazy: só toca Looper.getMainLooper() quando start() roda de fato — permite
    // testar o resto da classe (bpm, samplesPerBeat) em teste JVM puro sem Robolectric.
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val normalClick = renderClick(frequencyHz = 1500.0, durationMs = 18)
    private val pauseClick = renderClick(frequencyHz = 880.0, durationMs = 34)

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return

        val minBufBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val minBufSamples = maxOf(minBufBytes / 2, 1)
        val chunkSamples = maxOf(minBufSamples, sampleRate / 4) // ~250ms de folga

        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(chunkSamples * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        val track = builder.build()
        audioTrack = track
        running = true
        track.play()

        workerThread = Thread({ runScheduler(track, chunkSamples) }, "metronome-writer").also { it.start() }
    }

    fun stop() {
        if (!running) return
        running = false
        // stop() (em vez de pause()) desbloqueia um track.write() pendente na thread do scheduler.
        audioTrack?.stop()
        workerThread?.join(300)
        workerThread = null
        audioTrack?.release()
        audioTrack = null
    }

    /** Amostras entre um tique e o próximo, para o BPM informado (truncamento sub-amostra é desprezível). */
    internal fun samplesPerBeat(currentBpm: Int): Int = (sampleRate.toLong() * 60L / currentBpm).toInt()

    private fun runScheduler(track: AudioTrack, chunkSamples: Int) {
        var samplesUntilNextBeat = 0
        var beatIndex = 0
        val mixBuffer = ShortArray(chunkSamples)

        // clique que começou perto do fim de um buffer e precisa continuar no início do próximo.
        var pendingClick: ShortArray? = null
        var pendingClickPos = 0

        while (running) {
            mixBuffer.fill(0)
            var offset = 0

            pendingClick?.let { click ->
                val remaining = click.size - pendingClickPos
                val toCopy = minOf(remaining, chunkSamples)
                System.arraycopy(click, pendingClickPos, mixBuffer, 0, toCopy)
                pendingClickPos += toCopy
                if (pendingClickPos >= click.size) pendingClick = null
                offset = toCopy
            }

            while (offset < chunkSamples) {
                if (samplesUntilNextBeat <= 0) {
                    val click = if (beatIndex == PAUSE_BEAT_INDEX) pauseClick else normalClick
                    val spaceLeft = chunkSamples - offset
                    val toCopy = minOf(click.size, spaceLeft)
                    System.arraycopy(click, 0, mixBuffer, offset, toCopy)
                    if (toCopy < click.size) {
                        pendingClick = click
                        pendingClickPos = toCopy
                    }
                    val firedBeat = beatIndex
                    mainHandler.post { onBeat?.invoke(firedBeat) }
                    beatIndex = (beatIndex + 1) % BEATS_PER_BAR
                    samplesUntilNextBeat = samplesPerBeat(bpm)
                }
                val step = minOf(samplesUntilNextBeat, chunkSamples - offset)
                offset += step
                samplesUntilNextBeat -= step
            }

            track.write(mixBuffer, 0, chunkSamples)
        }
    }

    /** Sintetiza um clique curto: seno puro com envelope de decaimento exponencial. */
    private fun renderClick(frequencyHz: Double, durationMs: Int): ShortArray {
        val n = (sampleRate * durationMs / 1000.0).toInt()
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val envelope = exp(-t * 40.0)
            val sample = sin(2.0 * PI * frequencyHz * t) * envelope
            out[i] = (sample * Short.MAX_VALUE * 0.8).toInt().toShort()
        }
        return out
    }
}
