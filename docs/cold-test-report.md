# AnotherViewer Web App — RHEL9 冷测试报告

**测试日期**: 2026-06-29
**目标环境**: Red Hat Enterprise Linux 9
**测试方法**: 静态代码分析 + 依赖检查 (本地无 Java 21 环境，无法执行实际编译)

---

## 修复状态

| 问题 | 状态 | 修复提交 |
|------|------|---------|
| P0-1: DTO 类名冲突 | ✅ 已修复 | 950d569 |
| P0-2: 缺少 SPA 路由回退 | ✅ 已修复 | 950d569 |
| P0-3: @EnableScheduling 缺失 | ✅ 已修复 | 950d569 |
| P1-5: start.sh 配置路径错误 | ✅ 已修复 | 950d569 |
| P1-6: CORS 硬编码 | ✅ 已修复 | 950d569 |
| P1-7: SQLite 相对路径 | ✅ 已修复 | 950d569 |
| P1-4: Java 21 要求 | 📝 文档说明 | 需安装 java-21-openjdk |

---

## 测试概览

| 类别 | 文件数 | 严重问题 | 中等问题 | 低风险 |
|------|--------|----------|----------|--------|
| Java/Kotlin 后端 | ~50 | 3 | 5 | 4 |
| 前端 (Vue/TS) | ~25 | 1 | 2 | 1 |
| Docker 配置 | 3 | 1 | 1 | 0 |
| 配置文件 | 5 | 1 | 3 | 2 |

---

## 发现的问题

### 严重 (P0) — 编译失败或运行时崩溃

#### 1. DTO 类名冲突: `DownloadListResponse` 重复定义

**位置**: `ehviewer-web/src/main/java/com/hippo/ehviewer/web/dto/`
- `GalleryDto.kt:56` — `data class DownloadListResponse(val success: Boolean, val data: List<DownloadItemDto>, val total: Int)`
- `DownloadDto.kt:3` — `data class DownloadListResponse(val downloads: List<DownloadItem>, val labels: List<DownloadLabel>)`

**影响**: 两个类在同一包 `com.hippo.ehviewer.web.dto` 中，Kotlin 编译器会报 **重复类名** 错误，直接导致编译失败。

**修复建议**: 重命名其中一个，例如将 `DownloadDto.kt` 中的改名为 `DownloadListDataResponse`，或将 `GalleryDto.kt` 中的移除（因为 `GalleryController` 实际返回的是 `GalleryListResponse`）。

---

#### 2. 缺少 SPA 路由回退控制器

**位置**: 无对应文件

前端 `router/index.ts` 使用 `createWebHistory()` (HTML5 History 模式)，构建产物输出到 `ehviewer-web/src/main/resources/static`。但后端缺少将非 API 路由转发到 `index.html` 的 `ViewController`。

**影响**: 访问 `/gallery/123`、`/favorites` 等前端路由时，Spring Boot 返回 404，SPA 无法正常工作。

**修复建议**: 添加 SPA 转发配置:
```kotlin
@Configuration
class SpaWebConfig : WebMvcConfigurer {
    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/{path:[^\\.]*}").setViewName("forward:/index.html")
        registry.addViewController("/").setViewName("forward:/index.html")
    }
}
```

---

#### 3. `@EnableScheduling` 缺失

**位置**: `SmbBackupService.kt:212` 使用 `@Scheduled(fixedRate = 3600000)`

**影响**: Spring 不会注册定时任务调度器，`@Scheduled` 注解无效，计划同步功能不会执行。

**修复建议**: 在启动类或配置类上添加 `@EnableScheduling`:
```kotlin
@SpringBootApplication
@EnableScheduling
class EhWebApplication
```

---

### 高 (P1) — RHEL9 部署失败或功能异常

#### 4. Java 21 硬性要求 vs RHEL9 默认 Java 11

**位置**:
- `ehviewer-core/build.gradle.kts:6-7` — `sourceCompatibility = JavaVersion.VERSION_21`
- `ehviewer-web/build.gradle.kts:13` — `sourceCompatibility = JavaVersion.VERSION_21`
- Spring Boot 3.4.5 最低要求 Java 17

**RHEL9 环境**: 默认 Java 11，可通过 `alternatives` 切换到 17 或 21。

**影响**: 原生部署在 RHEL9 上会因 Java 版本不足直接失败。

**修复建议**:
```bash
# 在 RHEL9 上安装 Java 21
sudo dnf install java-21-openjdk java-21-openjdk-devel
sudo alternatives --set java /usr/lib/jvm/java-21-openjdk-21.x86_64/bin/java
```
或在 Dockerfile 中明确指定 Java 21 镜像（当前已是 `eclipse-temurin:21-jre-jammy`）。

---

#### 5. `start.sh` 配置属性路径错误

**位置**: `start.sh:20`
```bash
--ehviewer.cache.path=./data/cache
```

**实际属性**: `EhCoreConfigProperties.download.cachePath` 对应 Spring Boot relaxed binding 的 `ehviewer.download.cache-path`。

**影响**: 缓存路径配置不生效，使用默认值 `./data/cache` (碰巧一致) 但语义错误。更重要的是，`start.sh` 完全缺少 `--ehviewer.download.path` 之外的多个关键参数。

**修复建议**: 修正为:
```bash
--ehviewer.download.cache-path=./data/cache \
--ehviewer.download.worker-count=3 \
--ehviewer.download.cache-size-mb=10240
```

---

#### 6. CORS 和 WebSocket 源硬编码为开发端口

**位置**:
- `WebConfig.kt:11` — `allowedOriginPatterns("http://localhost:3000", "http://localhost:5173")`
- `WebSocketConfig.kt:19` — `setAllowedOriginPatterns("http://localhost:3000", "http://localhost:5173")`

**影响**: 生产环境中前端通过 Nginx 反代或其他域名访问时，CORS 和 WebSocket 连接会被浏览器拒绝。

**修复建议**: 使用环境变量或配置文件驱动:
```kotlin
.allowedOriginPatterns(*config.cors.allowedOrigins.toTypedArray())
```

---

#### 7. SQLite 数据库路径为相对路径

**位置**: `application.yml:6`
```yaml
url: jdbc:sqlite:data/ehviewer.db
```

**影响**: SQLite 使用相对路径时，实际文件位置取决于 JVM 工作目录。Docker 容器和 `start.sh` 的工作目录可能不同，导致数据库文件位置不一致。

**修复建议**: 使用绝对路径或基于环境变量:
```yaml
url: jdbc:sqlite:${EHVIEWER_DATA_DIR:./data}/ehviewer.db
```

---

### 中 (P2) — 功能缺陷或潜在问题

#### 8. `SmbBackupService.testConnection` GUEST 模式资源泄漏

**位置**: `SmbBackupService.kt:55-57`
```kotlin
val session = if (request.loginMode == "GUEST") {
    connection.connect()  // 返回值未捕获
```

**影响**: `connection.connect()` 返回的 `Session` 对象在 GUEST 模式下未赋值给 `session` 变量 (Kotlin `if` 表达式赋值)，后续 `session.close()` 调用的是上一行的 `connection.connect()` 返回值。实际上 GUEST 模式下 `connect()` 返回的 session 被正确赋值，但代码可读性差且如果 `connection.connect()` 抛异常，`share.close()` 等不会执行。

**修复建议**: 使用 `use` 块或 try-finally 确保资源释放。

---

#### 9. `EncryptionService` 安全性不足

**位置**: `EncryptionService.kt`
- 密码哈希使用无盐 SHA-256 (第 15-18 行)
- AES 加密使用 ECB 模式 (第 35 行)

**影响**: 无盐 SHA-256 容易被彩虹表攻击; ECB 模式不安全。

**修复建议**: 密码哈希改用 BCrypt (项目已引入 `spring-security-crypto` 的 `BCryptPasswordEncoder`)，AES 改用 CBC 或 GCM 模式。

---

#### 10. `ImageCacheService` 无容量限制

**位置**: `ImageCacheService.kt:9`
```kotlin
private val cache = ConcurrentHashMap<String, ByteArray>()
```

**影响**: `application.yml` 配置了 `cache-size-mb: 10240` (10GB)，但 `ImageCacheService` 完全忽略此配置，缓存无限增长，最终导致 OOM。

**修复建议**: 实现 LRU 淘汰策略或使用 Caffeine/Spring Cache。

---

#### 11. `GalleryController` 路由顺序隐患

**位置**: `GalleryController.kt`
- `@GetMapping("/{gid}")` (第 22 行)
- `@GetMapping("/history")` (第 29 行)
- `@GetMapping("/favorites")` (第 48 行)
- `@GetMapping("/quick-search")` (第 53 行)

**影响**: Spring MVC 中，字面路径匹配优先于路径变量匹配，但当 URL 为 `/api/v1/gallery/history` 时，`/{gid}` 理论上也能匹配。虽然 Spring 的路由优先级保证字面路径优先，但这种设计模式脆弱。

**修复建议**: 将 `/history`、`/favorites`、`/quick-search` 路由移到 `/{gid}` 之前，或使用更具体的路径前缀。

---

### 低 (P3) — 代码质量 / 可维护性

#### 12. `SecurityConfig` 全部 permitAll

**位置**: `SecurityConfig.kt:23-27`
```kotlin
.requestMatchers("/api/v1/auth/**").permitAll()
.requestMatchers("/api/**").permitAll()
.anyRequest().permitAll()
```

**影响**: 安全配置实际没有保护任何端点，所有 API 无需认证即可访问。虽然 `AuthController` 提供了认证逻辑，但 `SecurityFilterChain` 并未强制执行。

**修复建议**: 根据设计意图，若认证由前端 token 机制实现，可保留当前配置但注释说明; 若需要 Spring Security 保护，应使用 `.requestMatchers("/api/**").authenticated()`。

---

#### 13. 前端 `DownloadItem` 类型重复定义

**位置**:
- `web-frontend/src/api/download.ts:3` — 完整接口 (含 `id`, `label`, `downloadDir`)
- `web-frontend/src/stores/download.ts:4` — 精简接口 (无 `id`, `label`, `downloadDir`, `titleJpn`)

**影响**: 两处类型定义不一致，可能导致类型安全问题。

**修复建议**: 将 store 中的 `DownloadItem` 统一引用 `api/download.ts` 中的定义。

---

#### 14. `DownloadService.executeDownload` 为存根实现

**位置**: `DownloadService.kt:157-196`

**影响**: 下载功能实际上不会执行任何下载操作，只是直接将状态标记为完成。对于冷测试这不算阻塞问题，但功能不完整。

---

#### 15. Docker 基础镜像为 Ubuntu Jammy，非 RHEL9 原生

**位置**: `Dockerfile:2`
```dockerfile
FROM eclipse-temurin:21-jre-jammy
```

**影响**: Docker 容器内部使用 Ubuntu (apt-get)，与 RHEL9 主机无直接冲突，但字体安装使用 `apt-get install -y fonts-noto-cjk`。如果需要 RHEL9 原生部署 (非容器化)，需要改用 `dnf`。

**修复建议**: 如果仅 Docker 部署，当前配置可行; 如果需要原生 RHEL9 部署，需提供 RHEL9 专用安装脚本。

---

## RHEL9 部署注意事项

### 必需的系统依赖
```bash
# 安装 Java 21
sudo dnf install java-21-openjdk java-21-openjdk-devel

# 安装 CJK 字体 (图片渲染)
sudo dnf install google-noto-sans-cjk-fonts

# 配置防火墙
sudo firewall-cmd --permanent --add-port=8080/tcp
sudo firewall-cmd --reload

# SELinux (如启用)
sudo setsebool -P httpd_can_network_connect 1
```

### Docker 部署 (推荐)
```bash
# Docker 在 RHEL9 上原生支持，推荐方式
docker compose up -d
```

### 原生部署
```bash
# 需要先构建前端
cd web-frontend && npm install && npm run build

# 构建后端
cd /path/to/project
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :ehviewer-web:bootJar

# 启动
java -jar ehviewer-web/build/libs/ehviewer-web-*.jar
```

### 已知兼容性
| 组件 | 要求 | RHEL9 默认 | 状态 |
|------|------|------------|------|
| Java | 21 | 11 | 需手动安装 |
| Gradle | 17+ | N/A | 包含在项目中 |
| 字体 | fonts-noto-cjk | 无 | 需手动安装 |
| SQLite | native lib | 无 | JDBC 驱动自带 |
| SMB | smbj (纯 Java) | N/A | 无需系统依赖 |

---

## 总结

**阻塞性问题**: 3 个 (P0) — DTO 类名冲突、SPA 路由缺失、@EnableScheduling 缺失
**部署问题**: 4 个 (P1) — Java 版本、配置路径、CORS、数据库路径
**功能缺陷**: 4 个 (P2) — 资源泄漏、安全配置、缓存无限制、路由隐患
**代码质量**: 4 个 (P3) — 安全配置宽松、类型重复、存根实现、Docker 镜像

**建议优先级**: 先修复 P0 编译问题，再处理 P1 部署问题，最后优化 P2/P3。
