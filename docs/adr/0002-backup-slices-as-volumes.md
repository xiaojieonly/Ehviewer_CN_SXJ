# 0002: 备份分卷 = 独立 7z 分片序列

备份产物形态为**多个独立 7z 分片 + manifest.json**（每个分片含 SHA-256），由固定线程池并行压缩（利用多核）；分片作为可独立存储/传输/校验的实体设计（用户可将分片拷贝到 NAS、U 盘等异地存储）。还原按 manifest 顺序解压并逐片验哈希。GPG 签名/加密经 `BackupEncryptor` 接口预留（v1 仅 Noop 实现，不引 BouncyCastle）。

决策背景：用户要求"多核固实分卷压缩 + GPG 技术路线备好"。纯 Java 无真正的固实单流多卷 7z 写入实现（Commons Compress 只读分卷），且固实=单流会杀死并行。以"分片序列"等价实现"分卷"语义：并行压缩（多核）、分片独立可传（异地备份）、manifest 驱动完整性校验。

推论：
- 浏览器下载时外层 zip 包装（便捷），服务器 `backups/` 保留裸分片（真实分卷形态）
- AES 口令加密与 GPG 签名/加密在同一接口上扩展，格式契约见 `contracts/backup-format.md`
- 还原是破坏性操作：旧文件改名 `.bak` 保留，restore 接口需要鉴权（Bearer token，与其他 /api 一致）
