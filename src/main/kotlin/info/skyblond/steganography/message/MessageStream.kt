package info.skyblond.steganography.message

/**
 * Supplier of a writable message.
 *
 * The [nextBit] break each byte into bits from 0 to 7.
 *
 * The data to be written can have two parts:
 * + payload: must have, the message itself
 * + extra: optional, might be nonce, etc.
 *
 * The size (measured in bytes) of payload and extra
 * must not change once the message is fixed.
 *
 * The data is repeated once finished. For example, write "test",
 * then the data will be "testtesttest..."
 * */
interface MessageStream {
    fun nextBit(): Boolean
    val payloadSize: Int
    val extraSize: Int
}
