# 备份格式契约（backup-format）

> 依据 ADR-0002（备份分卷 = 独立 7z 分片序列）。本文件描述 `BackupService` 产物的固定格式，实现见 `ehviewer-web/.../service/BackupService.kt`。

## 产物结构

备份落盘于 `<data-dir>/backups/`（`data-dir` 见 `--data-dir`/`EHVIEWER_DATA_DIR`，默认 `./data`）：

```
backups/
├── manifest.json       # 清单（见下）
├── slice-01.7z         # ehviewer.db（SQLite 一致性快照）+ config.json（ServerConfig KV）
├── slice-02.7z         # security.key（dataDir 下缺失则跳过）
└── slice-NN.7z         # downloads/<子目录>、cache/<子目录>（仅 includeDownloads=true）
```

- 每个分片是**独立 7z 文件**（LZMA2，solid），由固定 4 线程池并行压缩（多核）
- 分片是**可独立存储/传输/校验**的实体（可拷到 NAS、U 盘异地备份）；还原按 manifest 顺序解压并逐片验哈希
- 每次导出前清空旧分片与残留 `.tmp`；manifest 随本次导出覆盖

## manifest.json

```json
{
  "formatVersion": 1,
  "exportedAt": "2026-08-02T10:00:00+08:00",
  "appVersion": "1.1.0",
  "slices": [
    { "name": "slice-01.7z", "sha256": "<hex>", "sizeBytes": 12345 }
  ],
  "includesDownloads": false
}
```

| 字段 | 说明 |
|---|---|
| `formatVersion` | 当前固定 `1`；restore 拒绝其他版本 |
| `exportedAt` | ISO-8601 导出时间 |
| `appVersion` | 导出时应用版本（与 `webVersion` 同源） |
| `slices[].name` | 分片文件名（`slice-%02d.7z` 顺序命名） |
| `slices[].sha256` | 分片 SHA-256（restore 逐片校验，不符拒绝） |
| `slices[].sizeBytes` | 分片字节数 |
| `includesDownloads` | 是否包含下载内容分片 |

## SQLite 一致性快照

db 快照用 `VACUUM INTO '<绝对路径>'` 生成（sqlite-jdbc；目标强制绝对路径，SQL 字面量单引号翻倍转义）。快照文件内容上与原库等价，包含所有同步实体表与 `server_config` 表。

## 还原语义（restore）

1. 解包上传的 zip → 校验 `manifest.json` 的 `formatVersion == 1` → 逐片 SHA-256 核对（不符抛异常拒绝，原数据不动）
2. 解压分片到临时目录
3. 替换（失败回滚已改名文件）：
   - `ehviewer.db` → 旧文件改名 `.bak`（已存在追加时间戳）→ 写入新快照
   - `security.key` → 同样 `.bak` + 替换（缺失则跳过）
   - `config.json` → **回写 `server_config` 表**（全量 upsert，幂等；不生成文件，db 是配置的唯一权威）
   - `downloads/`、`cache/`（includeDownloads 备份）→ 覆盖合并
4. 返回 `{"success":true,"message":"还原成功，重启后生效"}`——**db 文件替换需重启进程生效**（运行时 SQLite 连接持有旧文件句柄）

## 传输封装

- **浏览器下载**：`GET /api/v1/backup/export` 将分片 + manifest 打包为单 zip 流式返回（`application/zip`，`anotherviewer-backup-<yyyyMMdd-HHmmss>.zip`）
- **WebUI 还原**：`POST /api/v1/backup/restore` multipart（字段 `file`）。**上限 50MB**（Spring multipart 全局配置）——WebUI 还原面向元数据备份；含下载内容的 GB 级备份请手动解包/拷贝 data-dir（见 deployment.md 迁移）

## 加密/签名扩展点（预留）

`BackupEncryptor` 接口（`name(): "none" | "aes" | "gpg"`，`encrypt/decrypt`）为 AES 口令加密与 GPG 签名/加密预留；约定**先压缩后加密**、sha256 记录加密后落盘形态。v1 仅 `NoopBackupEncryptor`（默认，不引 BouncyCastle）。
