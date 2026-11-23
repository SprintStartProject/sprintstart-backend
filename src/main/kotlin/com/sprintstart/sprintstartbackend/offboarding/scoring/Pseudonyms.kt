package com.sprintstart.sprintstartbackend.offboarding.scoring

import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val ALGORITHM = "HmacSHA256"
private const val PREFIX = "P-"
private const val PSEUDONYM_BYTES = 6

/**
 * Names for people that cannot be turned back into an email or a login without the key.
 *
 * A pseudonym is a keyed hash of the identity within a scope. The same identity gets the same pseudonym
 * every time, so a researcher can follow one person across several exports, and a different one in
 * another scope, so two projects cannot be joined on it. Without the key, guessing an email and hashing it
 * confirms nothing.
 *
 * @property scope What the pseudonyms are unique to, such as a project.
 */
class Pseudonyms(
    private val key: ByteArray,
    private val scope: String,
) {
    /**
     * The pseudonym of [identity], spelled as a [PersonKey] spells it: `P-` and twelve hexadecimal digits.
     */
    fun of(identity: String): String {
        val digest = hmac(key, "$scope\u0000$identity")
        return PREFIX + HexFormat.of().formatHex(digest, 0, PSEUDONYM_BYTES)
    }

    companion object {
        /**
         * Derives the key pseudonyms are made with from a secret the application already holds.
         *
         * The secret is only ever the input of a keyed hash with a fixed purpose, so the pseudonym key is
         * not the secret itself and nothing else is protected by it.
         */
        fun keyFrom(secret: String): ByteArray = hmac(secret.toByteArray(Charsets.UTF_8), PURPOSE)

        private const val PURPOSE = "sprintstart/offboarding/pseudonyms/v1"

        private fun hmac(key: ByteArray, text: String): ByteArray {
            val mac = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key, ALGORITHM)) }
            return mac.doFinal(text.toByteArray(Charsets.UTF_8))
        }
    }
}
