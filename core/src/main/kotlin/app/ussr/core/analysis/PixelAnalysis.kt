package app.ussr.core.analysis

import app.ussr.core.model.VisualSignals
import kotlin.math.abs

/**
 * Pixel maths on a plain grayscale buffer, so it stays testable off-device.
 * The Android layer decodes a thumbnail and hands the bytes over.
 */
object PixelAnalysis {

    const val HASH_WIDTH = 9
    const val HASH_HEIGHT = 8

    /**
     * Difference hash: compare each pixel with its right neighbour on a 9x8 grayscale
     * downscale, one bit per comparison. Robust to resizing and re-compression, which is
     * what separates "the same picture twice" from "two pictures of the same wall".
     */
    fun dHash(gray: IntArray, width: Int = HASH_WIDTH, height: Int = HASH_HEIGHT): Long {
        require(gray.size == width * height) { "expected ${width * height} samples, got ${gray.size}" }
        var hash = 0L
        var bit = 0
        for (y in 0 until height) {
            for (x in 0 until width - 1) {
                val left = gray[y * width + x]
                val right = gray[y * width + x + 1]
                if (left > right) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Variance of the Laplacian — the standard cheap focus measure. A sharp frame has
     * strong second derivatives, a blurred one does not.
     */
    fun laplacianVariance(gray: IntArray, width: Int, height: Int): Double {
        require(gray.size == width * height) { "buffer does not match ${width}x$height" }
        if (width < 3 || height < 3) return 0.0
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val value = -4 * gray[i] +
                    gray[i - 1] + gray[i + 1] +
                    gray[i - width] + gray[i + width]
                sum += value
                sumSq += value.toDouble() * value
                count++
            }
        }
        if (count == 0) return 0.0
        val mean = sum / count
        return sumSq / count - mean * mean
    }

    fun meanLuma(gray: IntArray): Double =
        if (gray.isEmpty()) 0.0 else gray.sumOf { it.toDouble() } / gray.size

    /**
     * True for a frame that carries almost no information: a lens-cap black, a blown-out
     * white, or a flat single-tone screen grab.
     */
    fun isBlank(signals: VisualSignals): Boolean =
        signals.laplacianVariance < BLANK_VARIANCE &&
            (signals.meanLuma < BLANK_DARK || signals.meanLuma > BLANK_BRIGHT)

    /**
     * Box-downscale an arbitrary grayscale buffer to the target size. Callers that already
     * have a scaled bitmap can skip this; it exists so the hash is computed identically
     * whatever the decoder handed us.
     */
    fun downscale(
        gray: IntArray,
        srcWidth: Int,
        srcHeight: Int,
        dstWidth: Int,
        dstHeight: Int,
    ): IntArray {
        require(gray.size == srcWidth * srcHeight) { "buffer does not match ${srcWidth}x$srcHeight" }
        require(dstWidth > 0 && dstHeight > 0)
        val out = IntArray(dstWidth * dstHeight)
        for (dy in 0 until dstHeight) {
            val y0 = dy * srcHeight / dstHeight
            val y1 = maxOf(y0 + 1, (dy + 1) * srcHeight / dstHeight)
            for (dx in 0 until dstWidth) {
                val x0 = dx * srcWidth / dstWidth
                val x1 = maxOf(x0 + 1, (dx + 1) * srcWidth / dstWidth)
                var acc = 0L
                var n = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        acc += gray[y * srcWidth + x]
                        n++
                    }
                }
                out[dy * dstWidth + dx] = (acc / n).toInt()
            }
        }
        return out
    }

    /** How different two aspect ratios are, as a fraction. Used to keep bursts honest. */
    fun aspectDelta(aWidth: Int, aHeight: Int, bWidth: Int, bHeight: Int): Double {
        if (aHeight == 0 || bHeight == 0) return Double.MAX_VALUE
        val a = aWidth.toDouble() / aHeight
        val b = bWidth.toDouble() / bHeight
        return abs(a - b) / maxOf(a, b)
    }

    private const val BLANK_VARIANCE = 12.0
    private const val BLANK_DARK = 18.0
    private const val BLANK_BRIGHT = 238.0
}
