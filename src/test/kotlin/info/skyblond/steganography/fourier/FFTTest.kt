package info.skyblond.steganography.fourier

import org.apache.commons.math3.complex.Complex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.opentest4j.AssertionFailedError
import kotlin.random.Random

class FFTTest {

    @Test
    fun testAny() {
        repeat(5000) {
            val data = Array(Random.nextInt(1, 1024 + 32)) {
                Complex(Random.nextDouble(), Random.nextDouble())
            }
            val fft = FFT.fft(data)
            val ifft = FFT.ifft(fft)
            data shouldEqual ifft
        }
    }

    @Test
    fun testRadix2() {
        repeat(5000) {
            val data = Array(1024) { Complex(Random.nextDouble(), Random.nextDouble()) }
            val fft = FFT.fft(data)
            val ifft = FFT.ifft(fft)
            data shouldEqual ifft
        }
    }

    @Test
    fun testPrime() {
        repeat(5000) {
            val data = Array(1021) { Complex(Random.nextDouble(), Random.nextDouble()) }
            val fft = FFT.fft(data)
            val ifft = FFT.ifft(fft)
            data shouldEqual ifft
        }
    }

    @Test
    fun testHybrid() {
        repeat(5000) {
            val data = Array(1026) { Complex(Random.nextDouble(), Random.nextDouble()) }
            val fft = FFT.fft(data)
            val ifft = FFT.ifft(fft)
            data shouldEqual ifft
        }
    }

    private infix fun Array<Complex>.shouldEqual(other: Array<Complex>) {
        assertEquals(this.size, other.size)
        for (i in this.indices) {
            try {
                assertEquals(this[i].real, other[i].real, 1e-10)
                assertEquals(this[i].imaginary, other[i].imaginary, 1e-10)
            } catch (e: AssertionFailedError) {
                throw AssertionFailedError(
                    "Complex not equal at index $i. Expecting ${this[i]}, actual: ${other[i]}",
                    this[i], other[i]
                )
            }
        }
    }
}
