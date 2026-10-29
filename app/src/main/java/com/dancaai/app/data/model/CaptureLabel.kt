package com.dancaai.app.data.model

/**
 * O que está sendo gravado numa captura de coleta.
 *
 * O [token] é o valor literal que vai para a coluna `Rotulo` da planilha e é o
 * alvo lido pelos scripts de treino — `treinar_multilabel.py` compara
 * `Rotulo` com `post_boa` / `post_ruim_<desvio>`. Antes deste seletor o app
 * enviava um rótulo descartável (`"Captura #1"`) e esses valores eram digitados
 * à mão na planilha depois da coleta; renomear um token aqui desalinha o dataset
 * já coletado.
 */
enum class CaptureSubject(val token: String, val display: String, val group: CaptureGroup) {
    POSTURE_GOOD("post_boa", "Postura boa", CaptureGroup.POSTURE),
    POSTURE_LEFT_SHOULDER_DROP("post_ruim_OE", "Ombro esq. caído", CaptureGroup.POSTURE),
    POSTURE_RIGHT_SHOULDER_DROP("post_ruim_OD", "Ombro dir. caído", CaptureGroup.POSTURE),
    POSTURE_SHOULDERS_FORWARD("post_ruim_OF", "Ombros à frente", CaptureGroup.POSTURE),
    MOVE_BALANCO("mov_balanco", "Balanço", CaptureGroup.MOVEMENT),
    MOVE_BASE_1("mov_base1", "Base 1", CaptureGroup.MOVEMENT),
    MOVE_BASE_2("mov_base2", "Base 2", CaptureGroup.MOVEMENT),
    MOVE_ABERTURA("mov_abertura", "Abertura", CaptureGroup.MOVEMENT),
    MOVE_GIRO_INVERTIDO("mov_giro_invertido", "Giro invertido (Base 3)", CaptureGroup.MOVEMENT),
    // "Giro de 5 pisadas" é o mesmo passo, com o nome que se usa na prática; os dois
    // aparecem no seletor para não haver dúvida na hora de gravar com outras pessoas.
    MOVE_GIRO_LINHA("mov_giro_linha", "Giro em linha (Base 4 / 5 pisadas)", CaptureGroup.MOVEMENT),

    /**
     * Linha descartável para conferir se o envio pro Sheets está de pé sem precisar
     * de uma captura real. Os scripts de `modelo_postura/` descartam este rótulo
     * explicitamente, então uma verificação esquecida na planilha não entra em treino.
     */
    CHECK_UPLOAD("teste", "Teste de envio", CaptureGroup.CHECK),
}

/**
 * Nome sem o parêntese de desambiguação, para a pílula do HUD.
 *
 * O seletor mostra "Giro em linha (Base 4 / 5 pisadas)" porque é na hora de
 * escolher que os dois nomes precisam aparecer juntos; na pílula esse texto
 * quebra em duas linhas e rouba espaço da câmera.
 */
val CaptureSubject.shortDisplay: String get() = display.substringBefore(" (").trim()

/** Separa a lista do seletor em blocos; os dois alvos de modelo são distintos. */
enum class CaptureGroup(val display: String) {
    POSTURE("Postura"),
    MOVEMENT("Movimento"),
    CHECK("Verificação"),
}

/**
 * Ângulo da câmera em relação ao dançarino.
 *
 * É dimensão própria, e não mais um item na lista de [CaptureSubject], porque o
 * eixo z do MediaPipe muda de natureza de perfil: um ombro à frente visto de
 * lado aparece em x, não em z. Sem registrar o ângulo não dá para separar os
 * dados depois nem explicar a variância entre capturas do mesmo desvio.
 */
enum class CaptureView(val token: String, val display: String) {
    FRONT("frente", "Frente"),
    SIDE_LEFT("lado_E", "Lado E"),
    SIDE_RIGHT("lado_D", "Lado D"),
    BACK("costas", "Costas"),
}

/**
 * Rótulo armado no seletor da tela de Treino, aplicado a cada captura enviada.
 *
 * Formato do token: `assunto@angulo`, com sufixo `|c<ciclo>t<tempo>` nas
 * capturas contínuas. O `@` e o `|` permitem que os scripts de treino filtrem
 * por prefixo (`startswith("post_boa")`) sem perder o metadado de compasso, que
 * é o que liga a captura ao tempo do metrônomo.
 */
data class CaptureLabel(
    val subject: CaptureSubject = CaptureSubject.POSTURE_GOOD,
    val view: CaptureView = CaptureView.FRONT,
) {
    /** Texto curto para a pílula do HUD — o que o usuário confere antes de gravar. */
    val display: String get() = "${subject.shortDisplay} · ${view.display}"

    /** Token da coluna `Rotulo`; [meta] distingue a origem da captura. */
    fun token(meta: CaptureMeta? = null): String {
        val base = "${subject.token}@${view.token}"
        return if (meta == null) base else "$base|${meta.suffix()}"
    }
}

/**
 * Origem da captura, anexada ao token depois do `|`.
 *
 * Vai no rótulo, e não em colunas novas, porque a planilha tem um contrato de 38
 * colunas que o Apps Script espera na ordem exata — ver README. Manter o metadado
 * aqui evita mexer no lado do Google no meio da coleta.
 */
sealed interface CaptureMeta {
    /** Captura contínua: um snapshot por tempo do compasso do metrônomo. */
    data class Beat(val cycle: Int, val beat: String) : CaptureMeta

    /**
     * Amostra de uma gravação de movimento. [elapsedMs] é o tempo desde o início
     * da gravação — é ele que reconstrói a trajetória, já que buracos em [sample]
     * (frames sem pessoa no enquadramento) deixam a sequência não-uniforme.
     */
    data class Frame(val recording: Int, val sample: Int, val elapsedMs: Long) : CaptureMeta
}

private fun CaptureMeta.suffix(): String = when (this) {
    is CaptureMeta.Beat -> "c$cycle" + "t${beat.slug()}"
    is CaptureMeta.Frame ->
        "r$recording" + "s%03d".format(sample) + "t%04d".format(elapsedMs)
}

/**
 * Normaliza o rótulo de tempo do compasso (`"Pausa"`) para caber num token sem
 * acento, espaço ou caixa alta, que viram ruído na hora de agrupar no pandas.
 */
private fun String.slug(): String =
    lowercase().replace(Regex("[^a-z0-9]"), "")
