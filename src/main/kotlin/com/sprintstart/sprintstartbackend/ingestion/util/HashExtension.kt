package com.sprintstart.sprintstartbackend.ingestion.util

import java.security.MessageDigest

/**
 * Computes a SHA-256 digest for raw artifact content.
 *
 * @receiver Raw bytes to hash.
 * @return Lowercase hexadecimal SHA-256 digest.
 */
fun ByteArray.sha256(): String {
    val digest =
        MessageDigest.getInstance("SHA-256")

    return digest
        .digest(this)
        .joinToString("") {
            "%02x".format(it)
        }
}
