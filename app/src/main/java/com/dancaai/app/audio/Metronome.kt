package com.dancaai.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Padrão de compasso do metrônomo — o "tipo de batida" do módulo de ritmo.
 *
 * Um dos tempos soa diferente para dar referência de onde o compasso começa: em
 * [THREE_AND_PAUSE] é a quebra do forró; em [FOUR_EVEN] é o primeiro tempo.
 */
enum class BeatPattern(
    val label: String,
    val beatLabels: List<String>,
    /** Índice do tempo que recebe o clique distinto. */
    val distinctBeatIndex: Int,
) {
    THREE_AND_PAUSE("1-2-3-Pausa", listOf("1", "2", "3", "Pausa"), 3),
    FOUR_EVEN("1-2-3-4", listOf("1", "2", "3", "4"), 0);

    val beatsPerBar: Int get() = beatLabels.size
}

/**
 * Motor de metrônomo do módulo de Ritmo.
 *
 * O agendamento das batidas é feito em número de amostras de áudio, não em tempo
 * de relógio (`Handler.postDelayed`/`Timer`): a posição de reprodução do próprio
 * [AudioTrack] é o "relógio", o que elimina o drift acumulado típico de
 * metrônomos ingênuos.
 *
 * O clique é escrito no buffer bem antes de ser ouvido — o buffer guarda cerca
 * de 250 ms e `AudioTrack.write` bloqueia até haver espaço, então a composição
 * roda adiantada em relação à reprodução. Por isso [onBeat] não dispara no
 * instante da escrita: o instante audível é estimado por `playbackHeadPosition`
 * e o callback é agendado para ele, recebendo esse instante. Sem essa correção a
 * comparação com a transferência de peso carregaria um viés da ordem de meio
 * tempo, o bastante para inverter o veredito de adiantado/atrasado.
 */
class Metronome(
    private val sampleRate: Int = 44_100,
) {
    companion object {
        const val MIN_BPM = 60
        const val MAX_BPM = 200
        const val DEFAULT_BPM = 120
    }

    /** BPM atual, sempre restrito a [MIN_BPM]..[MAX_BPM]. Pode ser alterado com o metrônomo tocando. */
    @Volatile
    var bpm: Int = DEFAULT_BPM
        set(value) { field = value.coerceIn(MIN_BPM, MAX_BPM) }

    /** Compasso atual. Pode ser trocado com o metrônomo tocando. */
    @Volatile
    var pattern: BeatPattern = BeatPattern.THREE_AND_PAUSE

    /**
     * Chamado na main thread a cada tique, com o índice do tempo dentro do
     * compasso e o instante em que ele se torna audível, na base de
     * [SystemClock.uptimeMillis] — a mesma dos frames da câmera, o que permite
     * comparar batida e transferência de peso sem converter relógios.
     */
    var onBeat: ((beatIndexInBar: Int, audibleAtUptimeMs: Long) -> Unit)? = null

    @Volatile private var running = false
    private var audioTrack: AudioTrack? = null
    private var workerThread: Thread? = null

    // lazy: só toca Looper.getMainLooper() quando start() roda de fato — permite
    // testar o resto da classe (bpm, samplesPerBeat) em teste JVM puro sem Robolectric.
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val normalClick = renderClick(frequencyHz = 1500.0, durationMs = 18)
    private val distinctClick = renderClick(frequencyHz = 880.0, durationMs = 34)

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
        // tiques já agendados para um instante futuro não vão soar — descartá-los
        // evita que o compasso pisque na tela depois do metrônomo parar.
        mainHandler.removeCallbacksAndMessages(null)
    }

    /** Amostras entre um tique e o próximo, para o BPM informado (truncamento sub-amostra é desprezível). */
    internal fun samplesPerBeat(currentBpm: Int): Int = (sampleRate.toLong() * 60L / currentBpm).toInt()

    private fun runScheduler(track: AudioTrack, chunkSamples: Int) {
        var samplesUntilNextBeat = 0
        var beatIndex = 0
        var framesWritten = 0L
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
                    val currentPattern = pattern
                    val indexInBar = beatIndex % currentPattern.beatsPerBar
                    val click =
                        if (indexInBar == currentPattern.distinctBeatIndex) distinctClick else normalClick
                    val spaceLeft = chunkSamples - offset
                    val toCopy = minOf(click.size, spaceLeft)
                    System.arraycopy(click, 0, mixBuffer, offset, toCopy)
                    if (toCopy < click.size) {
                        pendingClick = click
                        pendingClickPos = toCopy
                    }
                    notifyBeat(track, indexInBar, framesWritten + offset)
                    beatIndex = (indexInBar + 1) % currentPattern.beatsPerBar
                    samplesUntilNextBeat = samplesPerBeat(bpm)
                }
                val step = minOf(samplesUntilNextBeat, chunkSamples - offset)
                offset += step
                samplesUntilNextBeat -= step
            }

            track.write(mixBuffer, 0, chunkSamples)
            framesWritten += chunkSamples
        }
    }

    /**
     * Agenda o aviso do tique para o instante em que ele sai pelo alto-falante,
     * estimado pela distância entre o frame do clique e o que já foi reproduzido.
     */
    private fun notifyBeat(track: AudioTrack, indexInBar: Int, beatFrame: Long) {
        val framesAhead = (beatFrame - track.playbackHeadPosition.toLong()).coerceAtLeast(0L)
        val delayMs = framesAhead * 1000L / sampleRate
        val audibleAtUptimeMs = SystemClock.uptimeMillis() + delayMs
        mainHandler.postDelayed({ onBeat?.invoke(indexInBar, audibleAtUptimeMs) }, delayMs)
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
