package info.skyblond.steganography.prng

import org.junit.jupiter.api.Test
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.test.assertEquals

class SHA1PRNGTest {
    /**
     * Make sure our implementation is identical to the standard SHA1PRNG
     * */
    @Test
    fun test() {
        val seed = Random.nextBytes(20)
        val ref = SecureRandom.getInstance("SHA1PRNG")
            .apply { setSeed(seed) }
        val our = SHA1PRNGStream(seed)

        val refBuffer = ByteArray(16 * 1024) // 16KB
        repeat(3000) {
            ref.nextBytes(refBuffer)
            for (i in refBuffer.indices) {
                assertEquals(refBuffer[i].toUByte().toInt(), our.read())
            }
        }
    }
}
