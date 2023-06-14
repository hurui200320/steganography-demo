package info.skyblond.steganography.prng

import java.security.MessageDigest

/**
 * SHA1PRNG.
 * */
class SHA1PRNGStream(
    seed: ByteArray
) : DelegatedRandomStream(ByteArray(20)) {
    private val digest = MessageDigest.getInstance("SHA")
    private var state = ByteArray(20)

    init {
        digest.reset()
        state = digest.digest(seed)
    }

    /**
     * From: [sun.security.provider.SecureRandom.updateState]
     * */
    private fun updateState(state: ByteArray, output: ByteArray) {
        var last = 1
        var v: Int
        var t: Byte
        var zf = false

        // state(n + 1) = (state(n) + output(n) + 1) % 2^160;
        for (i in state.indices) {
            v = state[i].toInt() + output[i].toInt() + last
            t = v.toByte()
            zf = zf or (state[i] != t)
            state[i] = t
            last = v shr 8
        }

        if (!zf) {
            state[0]++
        }
    }

    override fun reloadBuffer() {
        digest.update(state)
        val output = digest.digest()
        updateState(state, output)
        output.copyInto(buffer)
    }
}
