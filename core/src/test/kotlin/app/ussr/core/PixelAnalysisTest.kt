package app.ussr.core

import app.ussr.core.analysis.PixelAnalysis
import app.ussr.core.model.VisualSignals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PixelAnalysisTest {

    @Test
    fun `identical buffers hash identically`() {
        val gray = randomGray(9 * 8, seed = 1)
        assertEquals(PixelAnalysis.dHash(gray), PixelAnalysis.dHash(gray.copyOf()))
    }

    @Test
    fun `a re-compressed copy stays within the near-duplicate distance`() {
        val original = randomGray(9 * 8, seed = 7)
        // Simulate JPEG noise: nudge every sample by at most one level.
        val recompressed = IntArray(original.size) { (original[it] + Random(it).nextInt(-1, 2)).coerceIn(0, 255) }
        val distance = PixelAnalysis.hammingDistance(
            PixelAnalysis.dHash(original),
            PixelAnalysis.dHash(recompressed),
        )
        assertTrue("distance was $distance", distance <= 6)
    }

    @Test
    fun `unrelated pictures are far apart`() {
        val a = randomGray(9 * 8, seed = 11)
        val b = randomGray(9 * 8, seed = 12)
        val distance = PixelAnalysis.hammingDistance(PixelAnalysis.dHash(a), PixelAnalysis.dHash(b))
        assertTrue("distance was $distance", distance > 6)
    }

    @Test
    fun `a flat frame has near-zero laplacian variance and a noisy one does not`() {
        val flat = IntArray(32 * 32) { 128 }
        val noisy = randomGray(32 * 32, seed = 3)
        assertTrue(PixelAnalysis.laplacianVariance(flat, 32, 32) < 1.0)
        assertTrue(PixelAnalysis.laplacianVariance(noisy, 32, 32) > 100.0)
    }

    @Test
    fun `a black frame reads as blank and a dim but detailed one does not`() {
        val black = VisualSignals(dHash = 0, laplacianVariance = 0.5, meanLuma = 3.0)
        val dimButSharp = VisualSignals(dHash = 0, laplacianVariance = 400.0, meanLuma = 12.0)
        assertTrue(PixelAnalysis.isBlank(black))
        assertFalse(PixelAnalysis.isBlank(dimButSharp))
    }

    @Test
    fun `downscale averages into the target grid`() {
        val source = IntArray(4 * 4) { if (it < 8) 0 else 255 }
        val scaled = PixelAnalysis.downscale(source, 4, 4, 2, 2)
        assertEquals(listOf(0, 0, 255, 255), scaled.toList())
    }

    @Test
    fun `aspect delta ignores resolution and catches shape changes`() {
        assertEquals(0.0, PixelAnalysis.aspectDelta(4000, 3000, 2000, 1500), 1e-9)
        assertTrue(PixelAnalysis.aspectDelta(4000, 3000, 3000, 4000) > 0.4)
    }

    private fun randomGray(size: Int, seed: Int): IntArray {
        val random = Random(seed)
        return IntArray(size) { random.nextInt(0, 256) }
    }
}
