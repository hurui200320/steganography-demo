package info.skyblond.steganography.prng

import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.system.measureTimeMillis

/**
 * A simple performance test, not accurate, but good enough to
 * show the performance difference between SHA1PRNG and VMPC:
 * SHA1PRNG: 775 ms
 * VMPC:     328 ms
 * */
class PerformanceTest {
    private val n = 50000000

    @Test
    fun test() {
        val seed = SecureRandom().generateSeed(32)
        val sha1 = SHA1PRNGStream(seed)
        val vmpc = VMPCStream(seed)
        println("SHA1PRNG: ${foo(sha1)} ms")
        println("VMPC:     ${foo(vmpc)} ms")
    }

    private fun foo(randomStream: RandomStream): Long {
        // warm up
        for (i in 1..n / 50) {
            randomStream.read()
        }
        // test
        return measureTimeMillis {
            for (i in 1..n) {
                randomStream.read()
            }
        }
    }

    @Test
    fun test2() {
        val seed = SecureRandom().generateSeed(32)
        val vmpc = VMPCStream(seed)
        var count0 = 0
        var count1 = 0
        var count2 = 0
        for (i in 1..n) {
            when(vmpc.read() % 3){
                0 -> count0++
                1 -> count1++
                2 -> count2++
            }
        }
        println(count0)
        println(count1)
        println(count2)
    }
}
