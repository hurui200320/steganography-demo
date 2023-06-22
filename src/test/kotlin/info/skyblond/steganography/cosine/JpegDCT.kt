package info.skyblond.steganography.cosine

import org.apache.commons.math3.util.FastMath

/**
 * Standard but slow DCT used in JPEG.
 * Use as the reference to make sure the fast version is correct.
 * */
object JpegDCT {
    fun dct8(input: DCTChunk): DCTChunk {
        require(input.size == 8) { "Input must be 8x8" }
        return Array(8) { v ->
            require(input[v].size == 8) { "Input must be 8x8" }
            DoubleArray(8) { u ->
                var sum = 0.0
                for (y in input.indices) {
                    for (x in input[y].indices) {
                        sum += input[y][x] * cosTable[x][u] * cosTable[y][v]
                    }
                }
                0.25 * alpha(u) * alpha(v) * sum
            }
        }
    }

    private val alpha0 = 1.0 / FastMath.sqrt(2.0)

    private fun alpha(x: Int): Double = if (x == 0) alpha0 else 1.0

    private val cosTable = Array(8) { x ->
        DoubleArray(8) { u ->
            FastMath.cos((2 * x + 1) * u * FastMath.PI / 16.0)
        }
    }

    fun idct8(input: DCTChunk): DCTChunk {
        require(input.size == 8) { "Input must be 8x8" }
        return Array(8) { y ->
            require(input[y].size == 8) { "Input must be 8x8" }
            DoubleArray(8) { x ->
                var sum = 0.0
                for (v in input.indices) {
                    for (u in input[y].indices) {
                        sum += alpha(u) * alpha(v) * input[v][u] * cosTable[x][u] * cosTable[y][v]
                    }
                }
                0.25 * sum
            }
        }
    }
}
