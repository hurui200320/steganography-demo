package info.skyblond.steganography.lsb

import info.skyblond.steganography.message.MessageStream
import info.skyblond.steganography.prng.RandomStream

/**
 * This LSB encoding chooses one of the RGB channels and encodes 1bit info per pixel (bit0 or bit 1).
 * There has a 25% chance for this LSB encoding to skip a pixel.
 * For the rest of 75%, each channel has equal chances to be selected.
 * For each channel, this encoding randomly chooses bit 0 or bit 1 for encoding.
 * */
class RGBRandomBit01LSB : LinearLSB() {
    override fun encodeRandom(
        x: Int, y: Int, originalARGB: Int,
        randomStream: RandomStream, messageStream: MessageStream
    ): Int {
        // 0 -> Blue (7-0)
        // 1 -> Green (15-8)
        // 2 -> Red (23-16)
        val channel = randomStream.read() % 4
        // skip if channel is 3
        if (channel == 3) return originalARGB
        val bit = randomStream.read() % 2
        return setBit(originalARGB, 0x01 shl (channel * 8 + bit), messageStream.nextBit())
    }

    override fun decodeRandom(x: Int, y: Int, pixelARGB: Int, randomStream: RandomStream): List<Boolean> {
        val channel = randomStream.read() % 4
        // skip if channel is 3
        if (channel == 3) return emptyList()
        val bit = randomStream.read() % 2
        return listOf(pixelARGB and (0x01 shl (channel * 8 + bit)) != 0)
    }
}
