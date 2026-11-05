package com.dancaai.app

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object PostureValidator {

    // Índices MediaPipe
    private const val LEFT_SHOULDER  = 11
    private const val RIGHT_SHOULDER = 12
    private const val LEFT_HIP       = 23
    private const val RIGHT_HIP      = 24

    // Thresholds ajustáveis. SHOULDER_FORWARD_THRESHOLD e MIN_FRONTAL_RATIO são
    // internal (não private) de propósito: são a única fonte de verdade desses dois
    // critérios, referenciados também no debug do TrainingScreen e nos testes para
    // não haver drift entre eles.
    private const val SHOULDER_LEVEL_THRESHOLD_DEG  = 5f    // ângulo da linha de ombros com a horizontal

    /**
     * Zdiff (ombros − quadril, em z) normalizado pelo span de ombros.
     *
     * NÃO É MAIS UM CRITÉRIO DE DECISÃO. A verificação de ombros encurvados saiu das
     * regras (ver a seção 2 de [validate]); este valor permanece porque vai para a
     * coluna `Threshold_Ombros_Encurvados` da planilha de coleta, e serve de referência
     * de calibração para o classificador que vai assumir o desvio.
     *
     * Por que a regra caiu, medido nas 815 capturas rotuladas de 8 participantes: o
     * limiar ótimo de Zdiff depende da distância da câmera, e varia 3,8× dentro da
     * faixa que o dataset cobre — 0,38 com o tronco em 0,098 (longe), 1,34 em 0,140,
     * 1,44 em 0,164 (perto). Nenhum valor único serve, e a troca de denominador não
     * resolve: `dz` bruto, `dz/span`, `dz/tronco` e o ângulo sagital `atan2(dz, dy)`
     * têm todos mediana ~2× maior de perto que de longe. A causa é o próprio z do
     * MediaPipe, que é estimado — a perspectiva exagera profundidade de perto e a
     * achata de longe. O único sinal estável com a distância, `tronco/span`, não usa z
     * e é fraco demais sozinho (AUC 0,373 contra 0,224 do `dz/span`).
     *
     * Isso confirmou em campo o que a validação entre participantes já indicava: o
     * recall era bimodal (0/20 em dois participantes, 20/20 em outros dois). Em teste
     * no aparelho, com o praticante praticamente em L a vários metros a regra dizia
     * "postura ok", e perto da câmera acusava desvio com postura correta.
     */
    internal const val SHOULDER_FORWARD_THRESHOLD   = 1.40f

    /**
     * Frontalidade mínima para avaliar a inclinação lateral do tronco.
     *
     * Substitui o antigo `MIN_FRONTAL_SPAN = 0.15f`, que comparava o span de ombros
     * cru contra um valor absoluto. Span cru encolhe por dois motivos independentes —
     * girar o corpo E afastar-se da câmera —, então o portão antigo barrava quem
     * estava de frente mas longe. Em dança o corpo inteiro tem de caber no quadro, e
     * aí os ombros ocupam ~8–12% da largura da imagem: das 815 capturas frontais do
     * dataset, só 74 (9,1%) passavam o portão, e a inclinação lateral do tronco ficava
     * inerte — nenhum teste em aparelho jamais a viu disparar.
     *
     * O portão protegia também a verificação de ombros encurvados, que desde então saiu
     * das regras por depender do eixo z (ver [SHOULDER_FORWARD_THRESHOLD]). Quando o
     * classificador assumir esse desvio, o mesmo critério de frontalidade provavelmente
     * volta a valer para ele — a inferência sobre um praticante de perfil não tem mais
     * sentido para um modelo do que tinha para a regra.
     *
     * O valor 0,60 vem de 2.387 frames de balanço, base 1 e base 2 — passos sem
     * rotação, logo frontais por construção —, cuja razão mínima observada é 0,658.
     * Fica logo abaixo disso: nenhum frame comprovadamente frontal é rejeitado, e
     * ainda rejeita 40% dos frames de giro. Cobertura das capturas de postura:
     * 9,1% → 92,9%.
     */
    internal const val MIN_FRONTAL_RATIO            = 0.60f
    private const val LATERAL_TILT_THRESHOLD_DEG     = 10f  // inclinação lateral do tronco em graus

    /**
     * Piso de sanidade para segmentos do corpo em coordenadas normalizadas.
     *
     * Abaixo disso o segmento é degenerado (landmarks colapsados num ponto) e qualquer
     * razão calculada sobre ele é ruído amplificado. O portão antigo garantia um piso de
     * span por acidente, já que comparava contra 0,15 absoluto; a razão de frontalidade
     * não garante, então o piso passa a ser explícito.
     */
    private const val MIN_SEGMENT_LENGTH             = 1e-4f

    /**
     * O quanto o praticante está de frente para a câmera: 0 de perfil, ~0,85 de frente.
     *
     * É a razão entre o span de ombros e o comprimento do tronco, as duas medidas no
     * plano da imagem. Os dois numeradores encolhem junto quando a pessoa se afasta da
     * câmera, então a razão cancela a distância e a estatura e sobra a orientação: ao
     * girar o corpo, o span projetado tende a zero enquanto o tronco permanece.
     *
     * Devolve 0 quando o tronco é degenerado, para que o chamador trate como "não dá
     * para afirmar que está de frente" em vez de dividir por ~0 e aprovar.
     */
    internal fun frontalityRatio(shoulderSpan: Float, torsoLength: Float): Float =
        if (torsoLength < MIN_SEGMENT_LENGTH) 0f else shoulderSpan / torsoLength

    fun validate(landmarks: List<NormalizedLandmark>): PostureResult {
        if (landmarks.size < 25) return PostureResult.Unknown

        val leftShoulder  = landmarks[LEFT_SHOULDER]
        val rightShoulder = landmarks[RIGHT_SHOULDER]
        val leftHip       = landmarks[LEFT_HIP]
        val rightHip      = landmarks[RIGHT_HIP]

        // Verifica visibilidade mínima
        val minVisibility = 0.4f
        if (listOf(leftShoulder, rightShoulder, leftHip, rightHip)
                .any { it.visibility().orElse(0f) < minVisibility }
        ) return PostureResult.Unknown

        val issues = mutableListOf<PostureIssue>()
        var status = PostureStatus.OK

        // 1. Verifica nivelamento dos ombros — ângulo da linha de ombros com a horizontal
        // Latyshev [11]: desvio MediaPipe nos ombros é 1.6–2.1% da altura — adequado para ângulo.
        // Threshold 5° está acima da margem de erro do sensor (~2°) e dentro do range clínico aceitável.
        val shoulderDY = leftShoulder.y() - rightShoulder.y() // positivo = ombro esq mais baixo
        val shoulderDX = kotlin.math.abs(rightShoulder.x() - leftShoulder.x())
        val shoulderAngleDeg = Math.toDegrees(
            kotlin.math.atan2(kotlin.math.abs(shoulderDY).toDouble(), shoulderDX.toDouble())
        ).toFloat()
        if (shoulderAngleDeg > SHOULDER_LEVEL_THRESHOLD_DEG) {
            if (shoulderDY > 0) issues.add(PostureIssue.LEFT_SHOULDER_DROP)
            else                issues.add(PostureIssue.RIGHT_SHOULDER_DROP)
            status = PostureStatus.WARNING
        }

        // Portão de vista frontal da verificação 3: a inclinação lateral do tronco lê
        // um deslocamento em x, que de perfil mede profundidade, não desvio postural.
        // Ver MIN_FRONTAL_RATIO.
        val shoulderSpan = kotlin.math.abs(rightShoulder.x() - leftShoulder.x())
        val shoulderMidX = (leftShoulder.x() + rightShoulder.x()) / 2f
        val shoulderMidY = (leftShoulder.y() + rightShoulder.y()) / 2f
        val hipMidX      = (leftHip.x()      + rightHip.x())      / 2f
        val hipMidY      = (leftHip.y()      + rightHip.y())      / 2f
        val torsoLength  = kotlin.math.hypot(
            (shoulderMidX - hipMidX).toDouble(), (shoulderMidY - hipMidY).toDouble()
        ).toFloat()
        val isFrontalView = frontalityRatio(shoulderSpan, torsoLength) >= MIN_FRONTAL_RATIO

        // 2. Ombros encurvados — DESATIVADO nas regras; ver SHOULDER_FORWARD_THRESHOLD.
        //
        // Nenhuma regra geométrica sobre o eixo z do MediaPipe sobrevive à variação de
        // distância, e por isso este desvio não é mais decidido aqui. O lugar dele é o
        // classificador embarcado, que recebe a distância como entrada e aprende a
        // correção em vez de supô-la constante. Até ele existir, o app fica em silêncio
        // sobre ombros encurvados: para um app de treino, não dizer nada é menos danoso
        // que afirmar o contrário do que está acontecendo — o praticante corrigiria uma
        // postura correta, ou confiaria num "está ok" medido errado.
        //
        // Deliberadamente NÃO removido: PostureIssue.SHOULDERS_FORWARD segue no enum,
        // porque é o identificador gravado no banco e as sessões já registradas o
        // contêm; e SHOULDER_FORWARD_THRESHOLD segue como constante, porque vai para a
        // coluna `Threshold_Ombros_Encurvados` da planilha, cujo contrato de 38 colunas
        // o Apps Script espera na ordem exata.

        // 3. Verifica inclinação lateral do tronco — apenas em vista frontal
        // Calcula o ângulo do segmento quadril→ombro com a vertical via atan2.
        // Latyshev [11]: ombros e quadril têm precisão <3% da altura — adequado para cálculo angular.
        if (isFrontalView) {
            val dx = shoulderMidX - hipMidX
            val dy = hipMidY - shoulderMidY  // positivo quando quadril está abaixo do ombro (normal)
            val lateralTiltDeg = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), dy.toDouble())).toFloat()
            when {
                lateralTiltDeg >  LATERAL_TILT_THRESHOLD_DEG -> {
                    issues.add(PostureIssue.TRUNK_TILT_LEFT)
                    status = PostureStatus.WARNING
                }
                lateralTiltDeg < -LATERAL_TILT_THRESHOLD_DEG -> {
                    issues.add(PostureIssue.TRUNK_TILT_RIGHT)
                    status = PostureStatus.WARNING
                }
            }
        }

        return if (issues.isEmpty()) {
            PostureResult.Good
        } else {
            PostureResult.Bad(issues, status)
        }
    }
}

/**
 * Desvios posturais que o validador reconhece.
 *
 * O nome da constante é o identificador gravado no banco — estável mesmo que o
 * texto exibido mude. O [label] e o [advice] ficam aqui, junto da regra que
 * detecta o desvio, para que o conselho dado ao praticante não se descole do
 * critério biomecânico que o originou.
 */
enum class PostureIssue(val label: String, val advice: String) {
    LEFT_SHOULDER_DROP(
        "Ombro esquerdo caído",
        "A linha dos ombros pendeu para a esquerda. Procure mantê-la paralela ao chão.",
    ),
    RIGHT_SHOULDER_DROP(
        "Ombro direito caído",
        "A linha dos ombros pendeu para a direita. Procure mantê-la paralela ao chão.",
    ),
    SHOULDERS_FORWARD(
        "Ombros encurvados",
        "Os ombros ficaram à frente do quadril. Abra o peito e leve as escápulas para trás.",
    ),
    TRUNK_TILT_LEFT(
        "Tronco inclinado para a esquerda",
        "O tronco saiu do eixo vertical. Distribua o peso e mantenha o quadril sob os ombros.",
    ),
    TRUNK_TILT_RIGHT(
        "Tronco inclinado para a direita",
        "O tronco saiu do eixo vertical. Distribua o peso e mantenha o quadril sob os ombros.",
    ),
}

enum class PostureStatus { OK, WARNING, ERROR }

sealed class PostureResult {
    object Good    : PostureResult()
    object Unknown : PostureResult()
    data class Bad(val issues: List<PostureIssue>, val status: PostureStatus) : PostureResult()
}