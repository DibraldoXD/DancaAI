package com.dancaai.app.analysis

import com.dancaai.app.MovementDirection
import com.dancaai.app.PostureIssue
import com.dancaai.app.PostureResult
import com.dancaai.app.PostureStatus
import com.dancaai.app.WeightInfo
import com.dancaai.app.WeightLeg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAccumulatorTest {

    private val accumulator = SessionAccumulator()

    @Test
    fun `sessao sem frames nao produz razoes`() {
        val metrics = accumulator.snapshot()

        assertTrue(metrics.isEmpty)
        assertNull(metrics.goodPostureRatio)
        assertNull(metrics.correctTransitionRatio)
    }

    @Test
    fun `frames sem pose valida ficam fora do denominador`() {
        repeat(3) { accumulator.onPosture(PostureResult.Good) }
        repeat(97) { accumulator.onPosture(PostureResult.Unknown) }

        val metrics = accumulator.snapshot()

        // sair do enquadramento não pode contar como postura ruim
        assertEquals(3, metrics.poseFrames)
        assertEquals(3, metrics.goodPostureFrames)
        assertEquals(1f, metrics.goodPostureRatio!!, 0.0001f)
    }

    @Test
    fun `razao de postura mistura frames bons e ruins`() {
        repeat(3) { accumulator.onPosture(PostureResult.Good) }
        accumulator.onPosture(bad(PostureIssue.SHOULDERS_FORWARD))

        val metrics = accumulator.snapshot()

        assertEquals(4, metrics.poseFrames)
        assertEquals(0.75f, metrics.goodPostureRatio!!, 0.0001f)
    }

    @Test
    fun `cada desvio do frame e contado separadamente`() {
        accumulator.onPosture(bad(PostureIssue.SHOULDERS_FORWARD, PostureIssue.LEFT_SHOULDER_DROP))
        accumulator.onPosture(bad(PostureIssue.SHOULDERS_FORWARD))

        val metrics = accumulator.snapshot()

        // um frame com dois desvios continua sendo um frame
        assertEquals(2, metrics.poseFrames)
        assertEquals(
            mapOf(PostureIssue.SHOULDERS_FORWARD to 2, PostureIssue.LEFT_SHOULDER_DROP to 1),
            metrics.postureIssueCounts,
        )
        assertEquals(PostureIssue.SHOULDERS_FORWARD to 2, metrics.topPostureIssues.first())
    }

    @Test
    fun `contadores de peso sao substituidos e nao somados`() {
        // o StepCounter reemite os totais acumulados a cada frame
        accumulator.onWeight(weight(correct = 4, error = 1))
        accumulator.onWeight(weight(correct = 5, error = 1))
        accumulator.onWeight(weight(correct = 6, error = 2))

        val metrics = accumulator.snapshot()

        assertEquals(6, metrics.correctTransitions)
        assertEquals(2, metrics.errorTransitions)
        assertEquals(8, metrics.totalTransitions)
        assertEquals(0.75f, metrics.correctTransitionRatio!!, 0.0001f)
    }

    @Test
    fun `reset zera todas as contagens`() {
        accumulator.onPosture(PostureResult.Good)
        accumulator.onPosture(bad(PostureIssue.SHOULDERS_FORWARD))
        accumulator.onWeight(weight(correct = 3, error = 1))

        accumulator.reset()

        assertTrue(accumulator.snapshot().isEmpty)
        assertEquals(emptyMap<PostureIssue, Int>(), accumulator.snapshot().postureIssueCounts)
    }

    @Test
    fun `snapshot nao acompanha mudancas posteriores`() {
        accumulator.onPosture(PostureResult.Good)
        val taken = accumulator.snapshot()

        accumulator.onPosture(bad(PostureIssue.SHOULDERS_FORWARD))

        assertEquals(1, taken.poseFrames)
        assertEquals(emptyMap<PostureIssue, Int>(), taken.postureIssueCounts)
    }

    private fun bad(vararg issues: PostureIssue) =
        PostureResult.Bad(issues.toList(), PostureStatus.WARNING)

    private fun weight(correct: Int, error: Int) = WeightInfo(
        leg = WeightLeg.LEFT,
        direction = MovementDirection.NEUTRAL,
        showError = false,
        correctCount = correct,
        errorCount = error,
    )
}
