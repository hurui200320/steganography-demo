package info.skyblond.steganography.message

class TestMessageStream : MessageStream {
    private var flag = true
    override fun nextBit(): Boolean {
        val v = flag
        flag = !flag
        return v
    }

    override val payloadSize: Int = 1
    override val extraSize: Int = 0

}
