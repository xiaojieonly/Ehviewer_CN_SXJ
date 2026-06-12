# SMB Storage Architecture for EhViewer

## 1. 总结

在 EhViewer 中实现了完整的 SMB/CIFS 存储架构，包含两大功能模块：

- **SMB 下载位置**：支持将 SMB 共享目录直接挂载为下载存储目的地，通过 `SmbUri`/`SmbUniFile` 抽象层透明访问远程文件系统
- **SMB 备份同步**：独立于下载位置的备份机制，支持自动（下载完成后触发）和手动（全量同步）两种模式，带前台/后台执行能力

适用于移动设备存储空间有限的场景，用户可将下载内容集中存储到局域网 NAS，实现多设备共享与数据安全。

## 3. 导入以下外部依赖库

- `com.hierynomus:smbj:0.12.0` — SMB2/3 协议 Java 客户端库（已有依赖，本 PR 扩展使用其 `PipeShare`/`NamedPipe`/持久连接能力）

## 5. 目前已实现功能

### SMB 协议层

| 模块 | 文件 | 说明 |
|------|------|------|
| URI 模型 | `SmbUri.java` | 自定义 `smb://host:port/share/path` URI 解析，拒绝凭据嵌入和 `..` 路径 |
| UniFile 后端 | `SmbUniFile.java` | 基于 `SmbConnection` 的 `UniFile` 实现，透明支持目录操作、文件读写 |
| URI 处理器 | `SmbUriHandler.java` | 注册到 `UniFile` 框架，自动识别 `smb://` scheme |
| 连接管理 | `SmbConnection.java` | SMBJ 封装，支持持久连接（`open()`/`close()`）、`writeFile()` 带分块回调、`testConnection()`、`listShareNames()` |
| 配置存储 | `SmbSettings.java` / `SmbCredentialStore.java` | 密码通过 Android Keystore AES-GCM 加密存储 |
| 登录模式 | `SmbLoginMode.java` | 枚举：`ANONYMOUS` / `PASSWORD` |

### SMB 下载位置（已有功能 + 本 PR 修复）

- 设置页面对话框配置：IP、端口、共享名、路径、用户名/密码
- `SmbValidateTask` 异步验证连接并写入测试文件
- `Settings.getDownloadLocation()` 自动识别 `smb://` scheme 返回 `SmbUniFile`
- 修复：配置验证逻辑收紧（`a59d37d`）、目录检测改进（`760548d`）、最终验证完善（`fdb914d`）

### SMB 备份同步（新增功能）

**配置流程（对话框方案）：**

1. **服务器连接** — 输入 IP/端口/用户名/密码 → `SmbTestTask` 验证连接
2. **共享名输入** — 输入共享名称 → 验证共享是否存在
3. **文件夹浏览** — 列出共享内文件夹（A-Z 排序），支持子文件夹导航、新建文件夹、选择当前目录

**同步执行：**

- **自动同步**：`DownloadManager` 在下载完成后触发 `SmbBackupManager.syncGalleryToBackup()`
- **手动同步**：设置页面"同步所有下载到备份"按钮
- **前台模式**：`SmbBackupSyncAllTask` (AsyncTask) + 进度对话框（进度条、当前 gallery 名称、取消/后台按钮）
- **后台模式**：`SmbBackupService` (ForegroundService) + 通知栏进度、WakeLock 防休眠、取消按钮

**性能优化：**

- 持久连接复用：备份全程仅建立一次 SMB 连接（`connection.open()` / `connection.close()`）
- `writeFile()` 带 `Runnable onChunk` 回调，支持分块传输追踪
- `CountingInputStream` 追踪传输字节数用于速度计算
- 已同步文件跳过：按文件名 + 大小判断（`exists() && length() == file.length()`）

**其他：**

- 中英文完整本地化（`values/strings.xml` + `values-en/strings.xml`）
- 配置记忆：`SharedPreferences` 缓存上次输入的服务器信息
- 下载设置页面新增：SMB 备份开关、配置入口、同步按钮
- 配置摘要显示完整路径（`host:port/share/path`）

## 6. 已知未实现功能

- 进度对话框中文件名和传输速度的实时 UI 显示（`CountingInputStream` 和 `onChunk` 回调已实现字节追踪，但 `AsyncTask.publishProgress` 合并机制导致 UI 刷新不及时，待改为 `Handler` 方案）
- `SmbConnection.openShare()` 仍为每次操作创建新连接（仅备份流程通过 `open()/close()` 持久连接规避，其他调用路径如 `SmbUniFile` 未优化）
- SMB 服务器共享列表自动枚举（DCE/RPC `NetShareEnumAll` 因 smbj 0.12.0 对 IPC$ 命名管道的 IOCTL/Write 支持问题未实现，改为用户手动输入共享名）
- SMB 存储上的压缩包浏览（`SmbUniFile.createRandomAccessFile()` 抛出 `FileNotFoundException`）
- 备份任务的断点续传
- 多 SMB 服务器配置管理（当前仅支持一组备份服务器配置）
- 备份任务的调度与重试策略

## 7. 测试

使用 Legion Tab TB-320FC (Android) 连接自建 NAS (SMB2/3) 进行测试：

| 测试场景 | 结果 |
|----------|------|
| SMB 服务器连接（密码认证） | 通过 |
| SMB 服务器连接（匿名认证） | 通过 |
| 共享名输入与文件夹浏览 | 通过 |
| 子文件夹导航与新建文件夹 | 通过 |
| SMB 作为下载存储位置 | 通过 |
| 下载完成后自动备份 | 通过 |
| 手动全量同步（9000+ 条目） | 通过 |
| 前台备份模式（进度对话框） | 通过 |
| 后台备份模式（通知栏） | 通过 |
| 取消备份操作 | 通过 |
| 已同步文件跳过（增量同步） | 通过 |
| 配置记忆与复用 | 通过 |
| 中英文界面切换 | 通过 |
| 屏幕旋转/配置变更 | 通过 |

**总变更**：26 个文件，+2924 行 / -19 行，17 个 commit
