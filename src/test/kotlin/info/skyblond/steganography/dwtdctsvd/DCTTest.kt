package info.skyblond.steganography.dwtdctsvd

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals

class DCTTest {
    @Test
    fun test() {
        val n = 4
        repeat(5000) {
            val input = Chunk(Array(n) {
                DoubleArray(n) {
                    Random.nextInt(-128, 128).toDouble()
                }
            })

            val dct = input.dct2d()
            val idct = dct.idct2d()

            idct shouldEqualTo input
        }
    }

    private infix fun Chunk.shouldEqualTo(expect: Chunk) {
        assertEquals(expect.size, this.size)
        for (i in this.indices) {
            val expectRow = expect.data[i]
            val actualRow = this.data[i]
            for (j in this.indices) {
                try {
                    assertEquals(expectRow[j], actualRow[j], 1e-9)
                } catch (t: Throwable) {
                    println("$i $j")
                    throw t
                }
            }
        }
    }
}
