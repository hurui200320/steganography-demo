package info.skyblond.steganography.dwtdctsvd

import org.apache.commons.math3.util.FastMath
import java.util.concurrent.ConcurrentHashMap

/**
 * Calculate the DCT for N inputs.
 * The DCT is identical to the one used in JPEG.
 * */
class FixedDCT(val N: Int) {
    /**
     * Orthogonal DCT-II, the input size must be [N].
     * */
    fun dct(input: DoubleArray): DoubleArray {
        require(input.size == N) { "Input size must be $N, but ${input.size} is given" }
        return DoubleArray(input.size) { k ->
            var sum = 0.0
            for (n in input.indices) {
                sum += input[n] * dctTable[n][k]
            }
            sum * alpha(k)
        }
    }

    private val alpha0 = 1.0 / FastMath.sqrt(N.toDouble())
    private val alpha1 = FastMath.sqrt(2.0 / N)
    private val isqrt2 = 1.0 / FastMath.sqrt(2.0)

    private fun alpha(x: Int): Double = if (x == 0) alpha0 else alpha1

    /**
     * Lookup table for \cos{\left[\frac{\pi}{N}(n + 0.5)k\right]}.
     * N = 8, n and k ranged 0..7
     * */
    private val dctTable = Array(N) { n ->
        DoubleArray(N) { k ->
            FastMath.cos(FastMath.PI / N * (n + 0.5) * k)
        }
    }

    /**
     * Orthogonal DCT-III, the input size must be [N].
     * */
    fun idct(input: DoubleArray): DoubleArray {
        require(input.size == N) { "Input size must be $N, but ${input.size} is given" }
        return DoubleArray(input.size) { k ->
            var sum = isqrt2 * input[0]
            for (n in 1 until input.size) {
                sum += input[n] * dctTable[k][n]
            }
            sum * alpha1
        }
    }

    companion object {
        private val cache = ConcurrentHashMap<Int, FixedDCT>()

        fun create(n: Int): FixedDCT = cache.computeIfAbsent(n) { FixedDCT(n) }
    }
}
