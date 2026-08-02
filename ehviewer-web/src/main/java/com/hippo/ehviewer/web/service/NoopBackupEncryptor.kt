package com.hippo.ehviewer.web.service

import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.OutputStream

/** v1 默认加密器：明文直通，不加密（GPG/AES 由 [BackupEncryptor] SPI 后续扩展）。 */
@Component
class NoopBackupEncryptor : BackupEncryptor {
    override fun name(): String = "none"

    override fun encrypt(input: InputStream, output: OutputStream) {
        input.transferTo(output)
    }

    override fun decrypt(input: InputStream, output: OutputStream) {
        input.transferTo(output)
    }
}
