package info.skyblond.steganography.dwtdctsvd

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.random.Random

class DWTTest {

    @Test
    fun test() {
        repeat(200) { foo() }
    }

    private fun foo() = runBlocking(Dispatchers.Default){
        val height = 32
        val width = 30
        val j = 3

        val input2D = Array(height) { DoubleArray(width) { Random.nextInt(0, 256).toDouble() } }
        val (cLL, list) = dwt2d(Wavelet.haar, input2D, j)
        println("LL")
        println(cLL.toFullString())
        for (i in list.indices.reversed()) {
            println("LH$i (H edge)")
            println(list[i].lh.toFullString())

            println("HL$i (V edge)")
            println(list[i].hl.toFullString())

            println("HH$i (D edge)")
            println(list[i].hh.toFullString())
        }

        val idwt = idwt2d(cLL, list, Wavelet.haar)
        assertEquals(input2D.size, idwt.size)
        assertEquals(input2D[0].size, idwt[0].size)

        val errArray = DoubleArray(height * width)
        for (y in idwt.indices) {
            for (x in idwt[y].indices) {
                val error = abs(idwt[y][x] - input2D[y][x])
                errArray[y * width + x] = error
            }
        }

        val maxErr = errArray.max()
        println("Max error: $maxErr")

        assertTrue(maxErr < 1e-12)
    }
}
