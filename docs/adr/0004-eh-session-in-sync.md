# 0004: EH 登录会话进入同步（ehSession 独立实体）

状态：Accepted（2026-08-05）。契约落地：`contracts/sync-conflict-rules.md` v3.0、`contracts/sync-schemas.json#syncEhSession`、`contracts/openapi.yaml`（`SyncEhSessionDto` / `SyncEhSessionCookieDto`）。推翻 `docs/deployment.md` 旧决策「登录态不进 sync 实体；cookieStore 会话级」。

## 背景

1. EH 登录态（`ipb_member_id` / `ipb_pass_hash` / `igneous` 等 cookie）与 EH 设置（displayName / avatar / gallerySite）需要**跨设备一致性**：Web 登录 → App 同步拿会话；App 登录 → Web 同步拿会话；
2. 旧决策（deployment.md 迁移章节「路径 2 可选 cookies」）把登录态排除在 sync 实体之外、cookieStore 定为进程内**会话级**（服务器重启即失效），无法支撑上述双向一致需求，予以推翻。

## 决策

- **D1 ehSession 独立同步实体**：EH 登录会话 + 用户设置作为单一实体进入同步；字段 = `cookies[]`（name/value/domain/path/expiresAt/secure/httpOnly/persistent/hostOnly，镜像 okhttp3.Cookie）+ `displayName?` / `avatar?` / `gallerySite?`（0=e-hentai.org, 1=exhentai.org，同 `Settings.SITE_E`/`SITE_EX`）+ 同步元数据（lastModified / deviceId / deleted）。**单例实体**，每用户至多一条活记录，无自然 idempotency key，服务器固定键。
- **D2 LWW 双向同步，不参与 conflictStrategy**：ehSession 与 preferences 同级（ADR-0001 单用户、ADR-0003 推论「preferences 不受策略影响」先例）；仲裁恒为 LWW（±5s skew，契约 §1.2），A/C 平台序不适用；skew 内 last-received-wins。
- **D3 登出/清 cookie = tombstone**：`deleted=true`，**任何冲突策略下删除传播**（对齐契约 §3.8 tombstone 实体 history/bookmark 语义、§4.2 lifecycle）；服务端保留 tombstone 行并 bump `lastModified`，增量 pull 传播到其他设备。
- **D4 Web 端 cookie 加密落库**：cookie value 以 `enc:v1:` 前缀 + `security.key` 派生密钥（AES-GCM，同 BackupEncryptor aes 机制）加密后写入 anotherviewer.db；仅进程内解密供代理/抓取使用，**重启后从库恢复**（替代旧「会话级、重启失效」语义）。明文不落盘。
- **D5 老客户端兼容**：未升级契约的客户端忽略 `ehSession` 字段（§8 前向兼容义务）；老客户端行为不变，升级后自动开始交换会话。

## 推论

- cookies 仅收容站点域（`e-hentai.org` / `exhentai.org` / `ehgt.org` / `forums.e-hentai.org` 及子域）；非站点域 cookie 忽略。
- 登出语义：任一端登出产生 tombstone 双向传播；重新登录产生新的活记录（LWW 复活，§3.9）。
- `entityCounts` 增加 `ehSession`（0/1）；`/api/v1/sync/status` 与 pull 增量行为对老客户端保持不变。
- 安全前提：`security.key` 已在备份固定结构中随迁（backup 核心分片含 security.key），跨机器还原后加密 cookie 可解密。
- WebUI 新特性仅 PC 形态；App 未改动功能不重复测试（协作约定，沿用 ADR-0003）。
