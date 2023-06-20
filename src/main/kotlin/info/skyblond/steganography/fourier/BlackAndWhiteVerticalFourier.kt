package info.skyblond.steganography.fourier

import java.awt.image.BufferedImage

/**
 * Encode the data vertically, aka using half of the width and full height.
 * */
class BlackAndWhiteVerticalFourier(targetAmp: Double) : BlackAndWhiteHalfFourier(targetAmp) {
    override fun checkInputSize(width: Int, height: Int, mask: BufferedImage) {
        require(mask.width < width / 2) { "Mask too big" }
    }

    override fun calculateBox(width: Int, height: Int, mask: BufferedImage): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        // top left
        val x0 = width / 2 - mask.width
        val y0 = height / 2 - mask.height / 2
        // bottom right, extra 1 px to avoid axis
        val x1 = width / 2 + mask.width + 1
        val y1 = height / 2 + mask.height / 2
        return (x0 to y0) to (x1 to y1)
    }
}
