package com.sprintstart.sprintstartbackend.shared.crypto

import com.sprintstart.sprintstartbackend.ApplicationConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.encrypt.BytesEncryptor
import org.springframework.security.crypto.encrypt.Encryptors

@Configuration
class CryptoConfiguration(
    private val applicationConfig: ApplicationConfig,
) {
    @Bean
    fun symmetricEncryptor(): BytesEncryptor {
        val masterKey = applicationConfig.crypto.masterKey
        val salt = applicationConfig.crypto.salt
        return Encryptors.stronger(masterKey, salt)
    }
}
