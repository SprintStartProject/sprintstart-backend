package com.sprintstart.sprintstartbackend.shared.crypto

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.security.crypto.encrypt.BytesEncryptor
import org.springframework.stereotype.Component
import java.util.Base64

@Component
@Converter
class SymmetricEncryptedStringConverter(
    private val encryptor: BytesEncryptor,
) : AttributeConverter<String, String> {
    override fun convertToDatabaseColumn(attribute: String?): String? {
        if (attribute.isNullOrBlank()) return attribute

        val encryptedBytes = encryptor.encrypt(attribute.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(encryptedBytes)
    }

    override fun convertToEntityAttribute(dbData: String?): String? {
        if (dbData.isNullOrBlank()) return dbData

        val decodedBytes = Base64.getDecoder().decode(dbData)
        val decryptedBytes = encryptor.decrypt(decodedBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}
