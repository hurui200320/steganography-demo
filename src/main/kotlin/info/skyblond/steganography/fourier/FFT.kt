package info.skyblond.steganography.fourier

import info.skyblond.steganography.add
import info.skyblond.steganography.div
import info.skyblond.steganography.plus
import info.skyblond.steganography.times
import org.apache.commons.math3.complex.Complex
import org.apache.commons.math3.primes.Primes
import org.apache.commons.math3.transform.DftNormalization
import org.apache.commons.math3.transform.FastFourierTransformer
import org.apache.commons.math3.transform.TransformType
import org.apache.commons.math3.util.FastMath.cos
import org.apache.commons.math3.util.FastMath.sin

/**
 * Calculate FFT in any size.
 *
 * Ref: https://zjuturtle.com/2017/12/26/fft/
 * */
object FFT {
    @Suppress("FloatingPointLiteralPrecision")
    private const val DOUBLE_PI = 6.283185307179586476925287

    // ------------------------------ Number.h ------------------------------
    /**
     * Find a primitive root of prime [n].
     * */
    private fun findPrimitiveRoot(n: Int): Int {
        require(Primes.isPrime(n)) { "n should be prime!" }
        for (primeRootCandidate in 2 until n) {
            if (isPrimitiveRoot(primeRootCandidate, n))
                return primeRootCandidate
        }
        error("Primitive root not found for prime $n")
    }

    /**
     * Test if number [a] is a primitive root of mod [m].
     * */
    private fun isPrimitiveRoot(a: Int, m: Int): Boolean {
        val tot = m - 1
        val factors = factor(tot)
        for (pi in factors) {
            if (expModuloN(a, tot / pi, m) == 1)
                return false
        }
        return true
    }

    /**
     * Polynomial Factorization of [number]
     * */
    private fun factor(number: Int): List<Int> {
        var n = number
        val result = mutableListOf<Int>()
        if (n == 1) {
            result.add(1)
            return result
        }
        for (i in 2..n) {
            while (n != i) {
                if (n % i == 0) {
                    result.add(i)
                    n /= i
                } else {
                    break
                }
            }
        }
        result.add(n)
        return result
    }

    /**
     * (a^k)%n
     */
    private fun expModuloN(a: Int, k: Int, n: Int): Int {
        if (k == 0) return 1
        if (k == 1) return a % n
        if (k == 2) return (a * a) % n
        val k1 = k / 2
        val k2 = k - k1
        return if (k1 < k2) {
            val tmp1 = expModuloN(a, k1, n)
            val tmp2 = tmp1 * a % n
            tmp1 * tmp2 % n
        } else {
            val tmp = expModuloN(a, k1, n)
            tmp * tmp % n
        }
    }

    /**
     * (a^(-k))%n
     */
    @Suppress("SameParameterValue")
    private fun expModuloNInverse(a: Int, k: Int, n: Int): Int {
        if (k == 0) return 1
        if (k == 1) {
            for (inverse in 0 until n) {
                if ((a * inverse) % n == 1) return inverse
            }
            error("modular inverse not found!")
        }
        val modInverse = expModuloNInverse(a, 1, n)
        return expModuloN(modInverse, k, n)
    }
    // ------------------------------ Number.h ------------------------------

    // -------------------------------- FFT.h --------------------------------

    fun fft(data: Array<Complex>): Array<Complex> =
        if (isPowerOfTwo(data.size)) radix2FFT(data)
        else hybridFFT(data)

    private fun radix2FFT(data: Array<Complex>): Array<Complex> =
        FastFourierTransformer(DftNormalization.STANDARD)
            .transform(data, TransformType.FORWARD)

    @Suppress("LocalVariableName")
    private fun hybridFFT(data: Array<Complex>): Array<Complex> {
        val N = data.size
        if (N == 1 || N == 2) return radix2FFT(data)

        // if N is prime
        val factors = factor(N)
        if (factors.size == 1) return raderFFT(data)

        // N = N1 * N2
        val N1 = factors[0]
        val N2 = N / N1

        val X = Array(N1) { n1 ->
            Array(N2) { n2 ->
                data[N1 * n2 + n1]
            }
        }

        for (n1 in 0 until N1) {
            val row = Array(N2) { i -> X[n1][i] }
            val tmp = fft(row)
            for (n2 in 0 until N2) {
                X[n1][n2] = tmp[n2] * Complex(cos(DOUBLE_PI * n1 * n2 / N), -sin(DOUBLE_PI * n1 * n2 / N))
            }
        }

        for (n2 in 0 until N2) {
            val col = Array(N1) { n1 -> X[n1][n2] }
            val tmp = fft(col)
            for (n1 in 0 until N1)
                X[n1][n2] = tmp[n1]
        }

        val result = Array(data.size) { Complex.ZERO }
        for (n1 in 0 until N1) {
            for (n2 in 0 until N2) {
                result[N2 * n1 + n2] = X[n1][n2]
            }
        }

        return result
    }

    @Suppress("LocalVariableName", "DuplicatedCode")
    private fun raderFFT(data: Array<Complex>): Array<Complex> {
        val N = data.size
        val X0 = data.fold(Complex.ZERO) { acc, c -> acc + c }
        val g = findPrimitiveRoot(data.size)

        val aq = mutableListOf<Complex>()
        val bq = mutableListOf<Complex>()
        val product = mutableListOf<Complex>()

        val aqIndex = mutableListOf<Int>()
        val bqIndex = mutableListOf<Int>()

        aqIndex.add(1)
        bqIndex.add(1)
        aq.add(data[1])
        bq.add(Complex(cos(DOUBLE_PI / N), -sin(DOUBLE_PI / N)))
        var exp = expModuloN(g, 1, N)
        var expInverse = expModuloNInverse(g, 1, N)
        val expInverseBase = expInverse

        for (index in 1..N - 2) {
            aqIndex.add(exp)
            bqIndex.add(expInverse)

            aq.add(data[exp])
            val tmp = expInverse * DOUBLE_PI / N
            bq.add(Complex(cos(tmp), -sin(tmp)))

            exp = (exp * g) % N
            expInverse = (expInverse * expInverseBase) % N
        }

        // padding zero
        val M = nextPowerOf2(2 * N - 3)
        if (M != N - 1) {
            aq.add(1, M - N + 1, Complex.ZERO)
            for (index in 0 until M - N + 1)
                bq.add(bq[index])
        }

        val faq = radix2FFT(aq.toTypedArray())
        val fbq = radix2FFT(bq.toTypedArray())
        for (index in 0 until M) {
            product.add(faq[index] * fbq[index])
        }
        val inverseDFT = radix2IFFT(product.toTypedArray())
        val result = Array(N) { Complex.ZERO }
        result[0] = X0

        for (index in 0 until N - 1)
            result[bqIndex[index]] = inverseDFT[index] + data[0]
        return result
    }

    fun ifft(data: Array<Complex>): Array<Complex> =
        if (isPowerOfTwo(data.size)) radix2IFFT(data)
        else hybridIFFT(data)

    private fun radix2IFFT(data: Array<Complex>): Array<Complex> =
        FastFourierTransformer(DftNormalization.STANDARD)
            .transform(data, TransformType.INVERSE)

    @Suppress("LocalVariableName")
    private fun hybridIFFT(data: Array<Complex>): Array<Complex> {
        val N = data.size
        if (N == 1 || N == 2) return radix2IFFT(data)

        // if N is prime
        val factors = factor(N)
        if (factors.size == 1) return raderIFFT(data)

        // N = N1 * N2
        val N1 = factors[0]
        val N2 = N / N1

        val X = Array(N1) { n1 ->
            Array(N2) { n2 ->
                data[N1 * n2 + n1]
            }
        }

        for (n1 in 0 until N1) {
            val row = Array(N2) { i -> X[n1][i] }
            val tmp = ifft(row)
            for (n2 in 0 until N2) {
                X[n1][n2] = tmp[n2] * Complex(
                    cos(DOUBLE_PI * n1 * n2 / N), sin(DOUBLE_PI * n1 * n2 / N)
                ) * N2.toDouble()
            }
        }

        for (n2 in 0 until N2) {
            val col = Array(N1) { n1 -> X[n1][n2] }
            val tmp = ifft(col)
            for (n1 in 0 until N1)
                X[n1][n2] = tmp[n1] * N1.toDouble()
        }

        val result = Array(data.size) { Complex.ZERO }
        for (n1 in 0 until N1) {
            for (n2 in 0 until N2) {
                result[N2 * n1 + n2] = X[n1][n2] / N.toDouble()
            }
        }

        return result
    }

    @Suppress("LocalVariableName", "DuplicatedCode")
    private fun raderIFFT(data: Array<Complex>): Array<Complex> {
        val N = data.size
        val X0 = data.fold(Complex.ZERO) { acc, c -> acc + c }
        val g = findPrimitiveRoot(data.size)

        val aq = mutableListOf<Complex>()
        val bq = mutableListOf<Complex>()
        val product = mutableListOf<Complex>()

        val aqIndex = mutableListOf<Int>()
        val bqIndex = mutableListOf<Int>()

        aqIndex.add(1)
        bqIndex.add(1)
        aq.add(data[1])
        bq.add(Complex(cos(DOUBLE_PI / N), sin(DOUBLE_PI / N)))
        var exp = expModuloN(g, 1, N)
        var expInverse = expModuloNInverse(g, 1, N)
        val expInverseBase = expInverse

        for (index in 1..N - 2) {
            aqIndex.add(exp)
            bqIndex.add(expInverse)

            aq.add(data[exp])
            val tmp = expInverse * DOUBLE_PI / N
            bq.add(Complex(cos(tmp), sin(tmp)))

            exp = (exp * g) % N
            expInverse = (expInverse * expInverseBase) % N
        }

        // padding zero
        val M = nextPowerOf2(2 * N - 3)
        if (M != N - 1) {
            aq.add(1, M - N + 1, Complex.ZERO)
            for (index in 0 until M - N + 1)
                bq.add(bq[index])
        }

        val faq = radix2FFT(aq.toTypedArray())
        val fbq = radix2FFT(bq.toTypedArray())
        for (index in 0 until M) {
            product.add(faq[index] * fbq[index])
        }
        val inverseDFT = radix2IFFT(product.toTypedArray())
        val result = Array(N) { Complex.ZERO }
        result[0] = X0 / N.toDouble()

        for (index in 0 until N - 1)
            result[bqIndex[index]] = (inverseDFT[index] + data[0]) / N.toDouble()
        return result
    }

    /**
     * The next 2^k bigger than [n]
     * */
    private fun nextPowerOf2(n: Int): Int {
        if (isPowerOfTwo(n)) return n
        val newValue = 0x01 shl (Int.SIZE_BITS - n.countLeadingZeroBits())
        if (newValue <= 0 || newValue <= n) error("Size overflow! $this -> $newValue")
        return newValue
    }

    private fun isPowerOfTwo(n: Int): Boolean {
        return n > 0 && n and n - 1 == 0
    }

    // -------------------------------- FFT.h --------------------------------

}
