package com.sprintstart.sprintstartbackend.canonical.model.mapper

import java.security.MessageDigest

object Sha256 {
     fun sha256(bytes: ByteArray): String {
        val digest =
            MessageDigest.getInstance("SHA-256")

        return digest
            .digest(bytes)
            .joinToString("") {
                "%02x".format(it)
            }
    }
}