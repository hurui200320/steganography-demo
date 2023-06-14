package info.skyblond.steganography.prng

import org.bouncycastle.crypto.prng.VMPCRandomGenerator
import org.bouncycastle.util.Pack

/**
 * [VMPCRandomGenerator].
 * */
class VMPCStream(
    seed: ByteArray
): DelegatedRandomStream(ByteArray(256)) {
    constructor(seed: Long) : this(Pack.longToBigEndian(seed))

    private val generator = VMPCRandomGenerator()

    init {
        generator.addSeedMaterial(seed)
    }
    override fun reloadBuffer() {
        generator.nextBytes(buffer)
    }
}
