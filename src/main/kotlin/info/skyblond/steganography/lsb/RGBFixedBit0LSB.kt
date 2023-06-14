package info.skyblond.steganography.lsb

import info.skyblond.steganography.message.MessageStream
import info.skyblond.steganography.prng.NopStream
import info.skyblond.steganography.prng.RandomStream

/**
 * Pixel: [A,R,G,B]
 * This lsb encoding use RGB: [A, bit0, bit1, bit2] [A, bit3, bit4, bit5]......
 *
 * No random is used, use [NopStream] for `randomMessage`.
 * */
class RGBFixedBit0LSB : AbstractLSB() {
    override fun encodeRandom(
        x: Int, y: Int, originalARGB: Int,
        randomStream: RandomStream, messageStream: MessageStream
    ): Int {
        var data = originalARGB
        // R -> G -> B
        for (i in 2 downTo 0) {
            data = setBit(data, 0x01 shl i * 8, messageStream.nextBit())
        }
        return data
    }

    override fun decodeRandom(x: Int, y: Int, pixelARGB: Int, randomStream: RandomStream): List<Boolean> {
        val data = mutableListOf<Boolean>()
        // R -> G -> B
        for (i in 2 downTo 0) {
            data.add(pixelARGB and (0x01 shl i * 8) != 0)
        }
        return data
    }
}
