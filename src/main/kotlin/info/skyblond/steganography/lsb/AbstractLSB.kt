package info.skyblond.steganography.lsb

import info.skyblond.steganography.message.MessageStream
import info.skyblond.steganography.prng.RandomStream
import java.awt.image.BufferedImage
import java.io.InputStream

abstract class AbstractLSB : LSB {
    /**
     * Given the [x] and [y] coordinate of the pixel,
     * the [originalARGB], the [randomStream] and the [messageStream],
     * return the encoded ARGB.
     * */
    protected abstract fun encodeRandom(
        x: Int, y: Int, originalARGB: Int,
        randomStream: RandomStream, messageStream: MessageStream
    ): Int

    /**
     * Given the [x] and [y] coordinate of the pixel,
     * the [pixelARGB], and the [randomStream],
     * return the decoded bit. True means 1, false means 0.
     * One pixel might contain multiple bits, index 0 is LSB
     * */
    protected abstract fun decodeRandom(
        x: Int, y: Int, pixelARGB: Int, randomStream: RandomStream
    ): List<Boolean>

    final override fun encode(
        image: BufferedImage, messageStream: MessageStream, randomStream: RandomStream
    ): BufferedImage {
        val result = BufferedImage(image.width, image.height, image.type)

        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val newColor = encodeRandom(x, y, image.getRGB(x, y), randomStream, messageStream)
                result.setRGB(x, y, newColor)
            }
        }
        return result
    }

    final override fun decode(image: BufferedImage, randomStream: RandomStream): InputStream =
        object : InputStream() {
            private val seq = sequence {
                var value = 0x00
                var pointer = 0
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) {
                        decodeRandom(x, y, image.getRGB(x, y), randomStream).forEach {
                            if (it) value = value or (0x01 shl pointer)
                            pointer++
                            if (pointer >= Byte.SIZE_BITS) {
                                yield(value.toByte())
                                pointer = 0
                                value = 0
                            }
                        }
                    }
                }
            }

            private var currentIter = seq.iterator()

            override fun read(): Int =
                if (currentIter.hasNext()) currentIter.next().toUByte().toInt() else -1

            override fun available(): Int =
                if (currentIter.hasNext()) 1 else 0

            override fun reset() {
                currentIter = seq.iterator()
            }
        }

    protected fun setBit(data: Int, mask: Int, messageBit: Boolean): Int =
        if (messageBit) {
            data or mask // set bit to 1
        } else {
            data and (mask.inv()) // set bit to 0
        }
}
