package info.skyblond.steganography.cosine

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals

class DCTTest {
    @Test
    fun testJpegInverse() {
        repeat(5000){
            val input = Array(8) {
                DoubleArray(8) {
                    Random.nextInt(-128, 128).toDouble()
                }
            }

            val dct = JpegDCT.dct8(input)
            val idct = JpegDCT.idct8(dct)

            idct shouldEqualTo input
        }
    }

    @Test
    fun testFastDctVSJpegDct(){
        repeat(5000) {
            val input = Array(8) {
                DoubleArray(8) {
                    Random.nextInt(-128, 128).toDouble()
                }
            }

            val dctJpg = JpegDCT.dct8(input)
            val dct = input.dct2d()

            dct shouldEqualTo dctJpg

            val idctJpeg = JpegDCT.idct8(dctJpg)
            val idct = dct.idct2d()

            idct shouldEqualTo idctJpeg
        }
    }

    @Test
    fun performance() {
        val n = 1000000
        var input = Array(8) {
            DoubleArray(8) {
                Random.nextInt(-128, 128).toDouble()
            }
        }
        // warm up
        println("JPEG DCT warmup...")
        for (i in 1..n / 50) input = JpegDCT.dct8(JpegDCT.dct8(input))

        println("JPEG DCT test...")
        var time = measureTimeMillis {
            for (i in 1..n) input = JpegDCT.dct8(JpegDCT.dct8(input))
        }
        println("JPEG DCT time: ${time}ms")

        println("Fast DCT warmup...")
        for (i in 1..n / 50) input = input.dct2d().idct2d()

        println("Fast DCT test...")
        time = measureTimeMillis {
            for (i in 1..n) input = input.dct2d().idct2d()
        }
        println("Fast DCT time: ${time}ms")

        println(input.contentDeepToString())
    }

    private infix fun Array<DoubleArray>.shouldEqualTo(expect: Array<DoubleArray>){
        for (i in 0..7) {
            val expectRow = expect[i]
            val actualRow = this[i]
            for (j in 0..7) {
                try {
                    assertEquals(expectRow[j], actualRow[j], 1e-9)
                } catch (t: Throwable){
                    println("$i $j")
                    throw t
                }
            }
        }
    }
}
