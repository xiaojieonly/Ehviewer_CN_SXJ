package com.hippo.ehviewer.web.service

import java.io.InputStream
import java.io.OutputStream

/**
 * 备份数据可插拔加密 SPI（ADR-0002 预留扩展点）。
 *
 * v1 仅提供 [NoopBackupEncryptor]（name = "none"，明文直通，不引任何加密依赖）。
 * 未来实现约定：
 * - "aes"：对称加密（AES-GCM），密钥建议由 dataDir/security.key 派生，仅用
 *   javax.crypto 即可，无需第三方依赖。
 * - "gpg"：对接外部 gpg 二进制或 BouncyCastle OpenPGP（引入新依赖需另行评估）。
 *
 * 作用位置约定：加密作用在"单个分片整体"上（先压缩后加密）；manifest 的 sha256
 * 始终记录分片落盘形态的摘要，restore 先校验摘要、再解密、最后解压。
 */
interface BackupEncryptor {
    /** "none" | "aes" | "gpg"（预留）。 */
    fun name(): String

    fun encrypt(input: InputStream, output: OutputStream)

    fun decrypt(input: InputStream, output: OutputStream)
}
