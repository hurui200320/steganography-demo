package info.skyblond.steganography.dwtdctsvd

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.commons.math3.util.FastMath
import kotlin.math.ceil
import kotlin.math.log2

// DWT2D, ref: https://github.com/rafat/wavelib

/**
 * The wavelet used in DWT.
 * */
data class Wavelet(
    val decomposeFilterLength: Int,
    val lowPassDecomposeFilter: DoubleArray,
    val highPassDecomposeFilter: DoubleArray,

    val reconstructFilterLength: Int,
    val lowPassReconstructFilter: DoubleArray,
    val highPassReconstructFilter: DoubleArray,
) {
    init {
        require(decomposeFilterLength == lowPassDecomposeFilter.size) { "Low pass decompose filter length not match decompose filter length" }
        require(decomposeFilterLength == highPassDecomposeFilter.size) { "High pass decompose filter length not match decompose filter length" }

        require(reconstructFilterLength == lowPassReconstructFilter.size) { "Low pass reconstruct filter length not match reconstruct filter length" }
        require(reconstructFilterLength == highPassReconstructFilter.size) { "High pass reconstruct filter length not match reconstruct filter length" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Wavelet) return false

        if (decomposeFilterLength != other.decomposeFilterLength) return false
        if (!lowPassDecomposeFilter.contentEquals(other.lowPassDecomposeFilter)) return false
        if (!highPassDecomposeFilter.contentEquals(other.highPassDecomposeFilter)) return false
        if (reconstructFilterLength != other.reconstructFilterLength) return false
        if (!lowPassReconstructFilter.contentEquals(other.lowPassReconstructFilter)) return false
        return highPassReconstructFilter.contentEquals(other.highPassReconstructFilter)
    }

    override fun hashCode(): Int {
        var result = decomposeFilterLength
        result = 31 * result + lowPassDecomposeFilter.contentHashCode()
        result = 31 * result + highPassDecomposeFilter.contentHashCode()
        result = 31 * result + reconstructFilterLength
        result = 31 * result + lowPassReconstructFilter.contentHashCode()
        result = 31 * result + highPassReconstructFilter.contentHashCode()
        return result
    }

    companion object {

        val haar = Wavelet(
            decomposeFilterLength = 2,
            lowPassDecomposeFilter = doubleArrayOf(1.0 / FastMath.sqrt(2.0), 1.0 / FastMath.sqrt(2.0)),
            highPassDecomposeFilter = doubleArrayOf(-1.0 / FastMath.sqrt(2.0), 1.0 / FastMath.sqrt(2.0)),
            reconstructFilterLength = 2,
            lowPassReconstructFilter = doubleArrayOf(1.0 / FastMath.sqrt(2.0), 1.0 / FastMath.sqrt(2.0)),
            highPassReconstructFilter = doubleArrayOf(1.0 / FastMath.sqrt(2.0), -1.0 / FastMath.sqrt(2.0)),
        )
    }
}

/**
 * A subregion/sub-band.
 * */
data class SubRegion(
    val data: Array<DoubleArray>
) {
    val width = data[0].size
    val height = data.size

    init {
        for (i in 1 until data.size) {
            require(data[i].size == width)
        }
    }

    operator fun get(x: Int, y: Int): Double =
        if (x in 0 until width && y in 0 until height)
            data[y][x]
        else 0.0

    operator fun set(x: Int, y: Int, v: Double) {
        data[y][x] = v
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SubRegion) return false

        return data.contentDeepEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.contentDeepHashCode()
    }

    override fun toString(): String = "Subregion(w:$width, h:$height)"

    /**
     * Dump all content to string for print.
     * May consume a lot of resources (RAM).
     * */
    fun toFullString(): String {
        val sb = StringBuilder()
        sb.append("Subregion size: w:$width, h:$height\n")
        for (i in data.indices) {
            for (j in data[i].indices) {
                val s = "%.6f".format(data[i][j])
                sb.append(s).append("\t")
            }
            sb.append("\n")
        }
        return sb.toString()
    }
}

/**
 * Represent one decomposition.
 * */
data class Decomposition(
    val lh: SubRegion,
    val hl: SubRegion,
    val hh: SubRegion,
) {
    val width = lh.width
    val height = lh.height

    init {
        require(hl.width == width)
        require(hh.width == width)
        require(hl.height == height)
        require(hh.height == height)
    }

    val horizontalEdge: SubRegion
        get() = lh
    val verticalEdge: SubRegion
        get() = hl
    val diagonalEdge: SubRegion
        get() = hh
}

/**
 * Calculate the max decomposition level:
 * ceil(log2([signalLength] / (filterLength - 1)))
 *
 * Note: I don't know why we have to use filterLength - 1
 * instead of filterLength itself.
 * */
fun calculateMaxDecomposeLevel(signalLength: Int, wave: Wavelet): Int =
    log2(signalLength / (wave.decomposeFilterLength - 1.0)).toInt()

/**
 * Do DWT with sym padding.
 * Sym padding: ... x2 x1 | x1 x2 ... xn | xn xn-1 ...
 *
 * The [inputLength] should be even, if not, you may add a zero in the end.
 * The [input] gives a value based on an index ranged from 0 until [inputLength],
 * the [input] itself handles the offset and row/column stuff.
 *
 * The [setOrAddHighPassResult] takes (index, value, add), if add is true,
 * then add the value to the index, otherwise set the value to the index.
 * The [setOrAddHighPassResult] should handle the offset and row/column stuff.
 * */
private fun dwtSym(
    input: (Int) -> Double, inputLength: Int,
    wave: Wavelet,
    setOrAddLowPassResult: (Int, Double, Boolean) -> Unit,
    setOrAddHighPassResult: (Int, Double, Boolean) -> Unit,
    resultLength: Int
) {
    for (i in 0 until resultLength) {
        setOrAddLowPassResult(i, 0.0, false)
        setOrAddHighPassResult(i, 0.0, false)
        val t = 2 * i + 1 // the tao
        for (l in 0 until wave.decomposeFilterLength) {
            // f(t-l), based on t-l (aka the x), using sym to padding data
            val x = t - l
            val inputIndex = when {
                x in 0 until inputLength -> x
                x < 0 -> (-x - 1)
                // x >= inputLength
                else -> (2 * inputLength - x - 1)
            }
            // do the conv
            setOrAddLowPassResult(i, wave.lowPassDecomposeFilter[l] * input(inputIndex), true)
            setOrAddHighPassResult(i, wave.highPassDecomposeFilter[l] * input(inputIndex), true)
        }
    }
}

/**
 * Calculate the dimension (width and height) of each decomposition.
 * 0 -> the first decomposition,
 * j-1 -> the last decomposition containing LL.
 * */
private fun calculateDecomposeDimensions(
    width: Int, height: Int, j: Int, wave: Wavelet
): Array<Pair<Int, Int>> {
    val dimensions = Array(j) { -1 to -1 }
    // calculate the output length
    var convWidth: Int = width
    var convHeight: Int = height
    val filterLen: Int = wave.decomposeFilterLength
    for (i in 0 until j) {
        // calculate the size after conv
        convWidth += filterLen - 2
        convHeight += filterLen - 2
        // then down sampling by 2, ceil
        convWidth = ceil(convWidth / 2.0).toInt()
        convHeight = ceil(convHeight / 2.0).toInt()
        // log the dimension of i-th decomposition
        dimensions[i] = convWidth to convHeight
    }
    return dimensions
}

/**
 * Apply 2d dwt to the [source] using the given [wave].
 * The [j] is decomposition level, at least 1.
 *
 * Sym padding: ... x2 x1 | x1 x2 ... xn | xn xn-1 ...
 *
 * @see calculateDecomposeDimensions
 * */
suspend fun dwt2d(
    wave: Wavelet, source: Array<DoubleArray>, j: Int
): Pair<SubRegion, ArrayList<Decomposition>> = coroutineScope {
    require(j > 0) { "J must be positive integer" }
    val width = source[0].size
    val height = source.size
    run {
        val yMax: Int = calculateMaxDecomposeLevel(height, wave)
        val xMax: Int = calculateMaxDecomposeLevel(width, wave)
        val maxLevel: Int = minOf(yMax, xMax)
        require(j <= maxLevel) { "The Signal Can only be decomposed $maxLevel times using this wavelet" }
    }

    val dimensions = calculateDecomposeDimensions(width, height, j, wave)
    // result, Triple(LH, HL, HH)
    val result = ArrayList<Decomposition>(j)

    // the input of decomposition, the initial input is source
    var input: Array<DoubleArray> = source
    var inputHeight: Int = height
    var inputWidth: Int = width

    for (level in 0 until j) {
        val (levelWidth, levelHeight) = dimensions[level]
        // do the left and right part (row filter and column down sampling)
        val lowPassResult = Array(inputHeight) { DoubleArray(levelWidth) }
        val highPassResult = Array(inputHeight) { DoubleArray(levelWidth) }
        (0 until inputHeight).map { y ->
            async {
                dwtSym(
                    { i -> input[y][i] }, inputWidth, wave,
                    { i, d, add -> lowPassResult[y][i] = d + if (add) lowPassResult[y][i] else 0.0 },
                    { i, d, add -> highPassResult[y][i] = d + if (add) highPassResult[y][i] else 0.0 },
                    levelWidth
                )
            }
        }.awaitAll()
        // do column filter and row down sampling
        val cLL = Array(levelHeight) { DoubleArray(levelWidth) }
        val cLH = Array(levelHeight) { DoubleArray(levelWidth) }
        val cHL = Array(levelHeight) { DoubleArray(levelWidth) }
        val cHH = Array(levelHeight) { DoubleArray(levelWidth) }

        (0 until levelWidth).map { x ->
            async {
                // LX
                dwtSym(
                    { i -> lowPassResult[i][x] }, inputHeight, wave,
                    { i, d, add -> cLL[i][x] = d + if (add) cLL[i][x] else 0.0 },
                    { i, d, add -> cLH[i][x] = d + if (add) cLH[i][x] else 0.0 },
                    levelHeight
                )
                // HX
                dwtSym(
                    { i -> highPassResult[i][x] }, inputHeight, wave,
                    { i, d, add -> cHL[i][x] = d + if (add) cHL[i][x] else 0.0 },
                    { i, d, add -> cHH[i][x] = d + if (add) cHH[i][x] else 0.0 },
                    levelHeight
                )
            }
        }.awaitAll()

        result.add(
            Decomposition(
                lh = SubRegion(cLH),
                hl = SubRegion(cHL),
                hh = SubRegion(cHH)
            )
        )
        // for the next loop, use LL as input
        input = cLL
        inputWidth = levelWidth
        inputHeight = levelHeight
    }

    return@coroutineScope SubRegion(input) to result
}

/**
 * The inverse of [dwtSym].
 *
 * @see dwtSym
 * */
private fun idwtSym(
    lowPass: (Int) -> Double, highPass: (Int) -> Double,
    setOrAddResult: (Int, Double, Boolean) -> Unit,
    inputLength: Int, wave: Wavelet,
) {
    val lpr = wave.lowPassReconstructFilter
    val hpr = wave.highPassReconstructFilter
    // up sampling
    var mIndex = 0
    var nIndex = 1

    for (v in 0 until inputLength) {
        setOrAddResult(mIndex, 0.0, false)
        setOrAddResult(nIndex, 0.0, false)
        for (l in 0 until wave.reconstructFilterLength / 2) {
            val t = 2 * l
            if ((v - l) in 0 until inputLength) {
                val lowPassValue = lowPass(v - l)
                val highPassValue = highPass(v - l)
                setOrAddResult(mIndex, lpr[t] * lowPassValue + hpr[t] * highPassValue, true)
                setOrAddResult(nIndex, lpr[t + 1] * lowPassValue + hpr[t + 1] * highPassValue, true)
            }
        }

        mIndex += 2
        nIndex += 2
    }
}

/**
 * The inverse of [dwt2d].
 *
 * @see dwt2d
 * */
suspend fun idwt2d(
    ll: SubRegion, decompositions: List<Decomposition>,
    wave: Wavelet
): Array<DoubleArray> = coroutineScope {
    // take the ll as initial cLL
    var cLL = ll.data

    // we start from the last decomposition
    for (level in decompositions.indices.reversed()) {
        val decomposition = decompositions[level]
        val levelWidth = decomposition.width
        val levelHeight = decomposition.height
        // for each decomposition, we recover:
        // L from LL and LH
        // H from HL and HHva
        val cL = Array(2 * levelHeight) { DoubleArray(levelWidth) }
        val cH = Array(2 * levelHeight) { DoubleArray(levelWidth) }
        (0 until levelWidth).map { x ->
            async {
                idwtSym(
                    { i -> cLL[i][x] },
                    { i -> decomposition.lh[x, i] },
                    { i, d, add -> cL[i][x] = d + if (add) cL[i][x] else 0.0 },
                    levelHeight, wave
                )
                idwtSym(
                    { i -> decomposition.hl[x, i] },
                    { i -> decomposition.hh[x, i] },
                    { i, d, add -> cH[i][x] = d + if (add) cH[i][x] else 0.0 },
                    levelHeight, wave
                )
            }
        }.awaitAll()
        // then reconstruct the full image from L and H
        val image = Array(2 * levelHeight) { DoubleArray(2 * levelWidth) }
        (0 until 2 * levelHeight).map { y ->
            async {
                idwtSym(
                    { i -> cL[y][i] },
                    { i -> cH[y][i] },
                    { i, d, add -> image[y][i] = d + if (add) image[y][i] else 0.0 },
                    levelWidth, wave
                )
            }
        }.awaitAll()
        // for the next loop, use the recovered image as LL
        cLL = image
        // the LL might bigger than other regions, for example,
        // the LL is recovered and is 16x16, but other regions are 16x15,
        // which the image should be 32x30. in this situation,
        // The extra line will be ignored when calculating idwt using level size.
    }
    return@coroutineScope cLL
}
