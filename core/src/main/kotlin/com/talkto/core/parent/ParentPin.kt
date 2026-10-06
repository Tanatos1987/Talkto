package com.talkto.core.parent

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The parents' PIN. Only a salted, many-times-hashed form is stored, never the digits. It keeps a child out of the
 * parents' corner; it is not meant to stop someone with the phone unlocked and a debugger.
 */
object ParentPin {
    const val LENGTH = 4
    private const val ROUNDS = 20_000

    fun valid(pin: String): Boolean = pin.length == LENGTH && pin.all(Char::isDigit)

    fun newSalt(random: SecureRandom = SecureRandom()): String =
        ByteArray(16).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    fun hash(pin: String, salt: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        var bytes = (salt + ":" + pin).toByteArray()
        repeat(ROUNDS) { bytes = md.digest(bytes + salt.toByteArray()) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** Constant-time comparison of a typed PIN with the stored hash. */
    fun matches(pin: String, salt: String?, stored: String?): Boolean {
        if (salt.isNullOrEmpty() || stored.isNullOrEmpty() || !valid(pin)) return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), stored.toByteArray())
    }
}
