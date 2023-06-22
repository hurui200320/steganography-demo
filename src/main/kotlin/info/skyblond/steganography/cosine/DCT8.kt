package info.skyblond.steganography.cosine

import org.apache.commons.math3.util.FastMath

/**
 * Calculate the DCT for 8 inputs.
 * The DCT is identical to the one used in JPEG.
 * */
object DCT8 {
    /**
     * Orthogonal DCT-II, the input size must be 8.
     * */
    fun dct8(input: DoubleArray): DoubleArray {
        require(input.size == 8) { "Input size must be 8, but ${input.size} is given" }
        return DoubleArray(input.size) { k ->
            var sum = 0.0
            for (n in input.indices) {
                sum += input[n] * dctTable[n][k]
            }
            sum * alpha(k)
        }
    }

    private val alpha0 = 1.0 / FastMath.sqrt(8.0)
    private val alpha1 = FastMath.sqrt(0.25)
    private val isqrt2 = 1.0 / FastMath.sqrt(2.0)

    private fun alpha(x: Int): Double = if (x == 0) alpha0 else alpha1

    /**
     * Lookup table for \cos{\left[\frac{\pi}{N}(n + 0.5)k\right]}.
     * N = 8, n and k ranged 0..7
     * */
    private val dctTable = Array(8) { n ->
        DoubleArray(8) { k ->
            FastMath.cos(FastMath.PI / 8.0 * (n + 0.5) * k)
        }
    }

    /**
     * Orthogonal DCT-III, the input size must be 8.
     * */
    fun idct8(input: DoubleArray): DoubleArray {
        require(input.size == 8) { "Input size must be 8, but ${input.size} is given" }
        return DoubleArray(input.size) { k ->
            var sum = isqrt2 * input[0]
            for (n in 1 until input.size) {
                sum += input[n] * dctTable[k][n]
            }
            sum * alpha1
        }
    }
}
