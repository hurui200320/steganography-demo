package info.skyblond.steganography.dwtdctsvd

import info.skyblond.steganography.writeJPG
import info.skyblond.steganography.writePNG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.apache.commons.math3.linear.RealMatrix
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.floor


object Test {

    private val sourceImage = ImageIO.read(File("./pic/pexels-lukas-hartmann-1497306.jpg"))
    private val outputDir = File("./output/dwt-dct-svd").apply { mkdirs() }

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking(Dispatchers.Default) {
        println("Mask: w:${maskImage.width} h:${maskImage.height}")
        println("Preparing SVD...")
        val obj = DWTDCTSVD(sourceImage)

        val dY = 46 // search result
        val dU = 58
        val dV = 86
        (20..150 step 2).map { d ->
            val encodedImage = searchSingleEncode(obj, d) { y, u, v -> v }
            async {
                val file = File(outputDir, "test_dY_${dY}_dU_${dU}_dV_$d.jpg")
                encodedImage.writeJPG(file, 0.7)
                searchSingleDecode(
                    "jpg_0.7_dY_${dY}_dU_${dU}_dV_$d",
                    ImageIO.read(file), d
                ) { y, u, v -> v }
            }
        }.awaitAll()
    }

    private suspend fun decode(
        name: String, image: BufferedImage,
        dY: Int, dU: Int, dV: Int,
    ) {
        println("Decoding image... $dY $dU $dV")
        val (yDWT, uDWT, vDWT) = decomposeImageYUVHaarDWT(image)
        val (yLL, _) = yDWT
        val (uLL, _) = uDWT
        val (vLL, _) = vDWT
        val yDCTSVDChunks = yLL.chunked4x4().applyDCTSVD()
        val uDCTSVDChunks = uLL.chunked4x4().applyDCTSVD()
        val vDCTSVDChunks = vLL.chunked4x4().applyDCTSVD()

        val yBitStream = buildList {
            for (y in 0 until yDCTSVDChunks.size - 1) {
                for (x in 0 until yDCTSVDChunks[0].size - 1) {
                    val vu = uDCTSVDChunks[y][x].s.readBit(dU)
                    val vv = vDCTSVDChunks[y][x].s.readBit(dV)
                    val vy = yDCTSVDChunks[y][x].s.readBit(dY)
                    add(vy + vu + vv)
                }
            }
        }
        val data = kMeans1D(yBitStream.toDoubleArray())
        val outputImage = BufferedImage(maskImage.width, data.size / maskImage.width, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until outputImage.height) {
            for (x in 0 until outputImage.width) {
                val color = if (data[y * outputImage.width + x]) Color.WHITE else Color.BLACK
                outputImage.setRGB(x, y, color.rgb)
            }
        }
        outputImage.writePNG(File(outputDir, "recovered_$name.png"))
    }

    private suspend fun searchSingleEncode(
        obj: DWTDCTSVD, d: Int,
        selector: (Array<Array<RealMatrix>>, Array<Array<RealMatrix>>, Array<Array<RealMatrix>>) -> Array<Array<RealMatrix>>
    ): BufferedImage {
        println("Applying steganography... $d")
        val iter = mask.iterator()
        return obj.steganography { ySArr, uSArr, vSArr ->
            for (y in 0 until ySArr.size - 1) {
                for (x in 0 until ySArr[0].size - 1) {
                    val bit = iter.next()
                    selector(ySArr, uSArr, vSArr)[y][x].setBit(bit, d)
                }
            }
        }
    }

    private suspend fun searchSingleDecode(
        name: String, image: BufferedImage, d: Int,
        selector: (SubRegion, SubRegion, SubRegion) -> SubRegion
    ) {
        println("Decoding image... $d")
        val (yDWT, uDWT, vDWT) = decomposeImageYUVHaarDWT(image)
        val (yLL, _) = yDWT
        val (uLL, _) = uDWT
        val (vLL, _) = vDWT

        val selectedLL = selector(yLL, uLL, vLL)
        val selectedDCTSVDChunks = selectedLL.chunked4x4().applyDCTSVD()

        val yBitStream = buildList {
            for (y in 0 until selectedDCTSVDChunks.size - 1) {
                for (x in 0 until selectedDCTSVDChunks[0].size - 1) {
                    val v = selectedDCTSVDChunks[y][x].s.readBit(d)
                    add(v)
                }
            }
        }
        val data = kMeans1D(yBitStream.toDoubleArray())
        val outputImage = BufferedImage(maskImage.width, data.size / maskImage.width, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until outputImage.height) {
            for (x in 0 until outputImage.width) {
                val color = if (data[y * outputImage.width + x]) Color.WHITE else Color.BLACK
                outputImage.setRGB(x, y, color.rgb)
            }
        }
        outputImage.writePNG(File(outputDir, "recovered_$name.png"))
    }

    private suspend fun applySteganography(
        obj: DWTDCTSVD,
        dY: Int, dU: Int, dV: Int,
    ): BufferedImage {
        println("Applying steganography... $dY $dU $dV")
        val iter = mask.iterator()
        return obj.steganography { ySArr, uSArr, vSArr ->
            for (y in 0 until ySArr.size - 1) {
                for (x in 0 until ySArr[0].size - 1) {
                    val bit = iter.next()
                    uSArr[y][x].setBit(bit, dU)
                    vSArr[y][x].setBit(bit, dV)
                    ySArr[y][x].setBit(bit, dY)
                }
            }
        }
    }


    /**
     * Encode:
     * (floor([RealMatrix].getEntry(i,i) / [d]) + f([bit])) * [d],
     *
     * The result is the SVD entry will be quantized by [d].
     * If the bit is true, then the reminder will be bigger than 0.5d_i,
     * otherwise it's smaller than 0.5d_i.
     * */
    private fun RealMatrix.setBit(bit: Boolean, d: Int) {
        val b = if (bit) 0.75 else 0.25
        val s = this.getEntry(0, 0)
        val m = floor(s / d) + b
        this.setEntry(0, 0, m * d)
    }

    private fun RealMatrix.readBit(d: Int): Double {
        val s = this.getEntry(0, 0)
        return (s % d) / d
    }

    private fun kMeans1D(inputs: DoubleArray): BooleanArray {
        var threshold: Double
        val err = 1e-6
        // center point of 0 and 1
        val center = doubleArrayOf(inputs.min(), inputs.max())
        while (true) {
            threshold = (center[0] + center[1]) / 2
            val bits = BooleanArray(inputs.size) { inputs[it] >= threshold }
            // check the distance and correct center
            center[0] = inputs.filterIndexed { index, _ -> !bits[index] }.average() // the avg of all bit0's value
            center[1] = inputs.filterIndexed { index, _ -> bits[index] }.average() // the avg of all bit1's value
            val newThreshold = (center[0] + center[1]) / 2
            if (abs(newThreshold - threshold) < err) {
                // update the threshold and exit
                threshold = newThreshold
                break
            }
        }
        return BooleanArray(inputs.size) { inputs[it] >= threshold }
    }


    private val maskImage = ImageIO.read(File("./pic/fft_mask/mask3.png"))

    private val maskData = BooleanArray(maskImage.height * maskImage.width) { index ->
        maskImage.getRGB(
            index % maskImage.width, index / maskImage.width
        ) == Color.WHITE.rgb
    }

    private val mask = object : Iterable<Boolean> {
        override fun iterator(): Iterator<Boolean> = object : Iterator<Boolean> {
            override fun hasNext(): Boolean = true
            private var d = maskData.iterator()
            override fun next(): Boolean {
                if (!d.hasNext()) d = maskData.iterator()
                return d.next()
            }
        }
    }
}
