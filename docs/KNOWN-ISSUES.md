# AnotherViewer 已知问题与待办承接

> 创建：2026-08-01 ｜ 目的：承接被清理的开发计划/报告文档中的**仍然有效**的内容（未实现功能、技术债、运维教训）。
> 来源文档（已删除，git 历史可查）：`PLAN-settings-overhaul.md`、`docs/PLAN-uiux-implementation.md`、`docs/webui-roadmap.md`、`docs/webui-parallel-execution.md`、`docs/webui-progress.md`、`docs/compose/plans/2026-06-13-smb-local-cache-upload.md`、`docs/superpowers/specs/2026-07-29-site-provider-plugin-system-design.md`、`docs/code-audit-2026-08-01.md`、`docs/ui-ux-review-2026-08-01.md`、`docs/cold-test-report.md`。

---

## 1. 未实现功能（有设计规格）

| 项 | 规格出处 | 当前状态 |
|---|---|---|
| **预读缓存**（reader prefetch） | webui-roadmap §1.2 / parallel-execution B6：`ehviewer.reader.prefetch-pages` 配置 + N+1~N+K 预读语义，后端预读进缓存 | 后端零实现；仅前端 `ImageReader.vue` 客户端预载 next2/prev1。需后端预读 + 前端/Android 配合 |
| **虚拟滚动** | webui-roadmap §3.4（`useVirtualScroll` 等 4 个 composable 已作为死代码删除） | HomeView 用普通 v-for，无虚拟滚动替代 |
| **CI 发布 web JAR** | webui-roadmap §4.4 | `azure-pipelines.yml` 仅构建 `app:assembleDebug` APK；`ehviewer-web` bootJar 无 CI 产物 |
| **真机 QA + H1 真机基线** | webui-progress §7 / roadmap Wave 4 | `web-frontend/e2e/baseline/` 229 张基线全部为 web 自渲染，未替换为 Android 真机三主题截图；iPad Safari A2HS、飞行模式读已缓存画廊、Android maskable 图标、SW 更新流均待真机验证 |
| **change-password 端点** | PLAN-settings-overhaul Wave 6C | 后端无 `POST /api/v1/auth/change-password`；前端 `AdminAccess.vue:11-12` 按钮禁用 + TODO |

## 2. 已知技术债（低优先级）

1. `AppCard.vue` 偏离冻结契约（硬编码画廊内容、无 default slot），`GalleryCard`/`DownloadItem` 因此自行用 token 复刻表面——可统一（契约在 `types/components.ts`）。
2. SMB 密码明文落库（`SmbConfigEntity.kt:30-31`，`SmbBackupService.updateConfig` 写入）；响应不回显。可接 `EncryptionService` 加密。
3. `/api/v1/auth/login` 与 `/auth/pair` 无速率限制（LAN 信任模型下有意识关闭；6 位配对码理论上可暴力）。
4. `HistoryService.listHistory` 全表加载（个人规模可接受，大数据量退化）。
5. `DownloadItem.downloadDir` 向客户端暴露服务器绝对路径。
6. `EhAuthService.pairCodes` 与 `CommentService.comments` 内存 Map 无界（processing tasks 已有界）。
7. `test.key`（仓库根）仍被 git 跟踪——应删除/轮换。
8. `GalleryController.addToHistory` 接受未校验的 `Map` body。
9. `start.sh:97` 键名笔误：`--ehviewer.cache.size-mb` 应为 `--ehviewer.download.cache-size-mb`（当前是 no-op）。
10. UX-01（悬浮圆裁切）与 UX-08（内容区宽度统一）需浏览器实测复核——静态审查未见残留问题。
11. `MetricsController` 占位零值：`memory.size.bytes = entries * 0L`、`process.completed.total`、`ws.connections.active`。
12. mock gallery detail 的 `imageUrl: https://ehgt.org/mock/…` 为死链（缩略图与阅读页已走本地 `/api/v1/image/mock-thumb.svg`）。

## 3. 运维教训（原 webui-progress §6 转存）

Android `app/` 含成人内容相关代码，子代理自主深度探索会累积触发模型内容审核（`data_inspection_failed`，曾消耗 ~490 万 token 后失败）。后续 `app/` 工作宜 lead 受控小步推进，或拆到最小内容敏感单元。
**缓解方案**：`docs/superpowers/plans/2026-07-30-ehentai-hide-and-cleanup.md`（E-Hentai 代码隐藏+瘦身计划，未执行，保留）。

## 4. 明确范围外

- **waifu2x 实装**：三份规划文档一致决策为范围外；接口已在 B7 冻结（`ImageProcessor` 接口 + `NoopProcessor`），部署侧无 waifu2x unit。
- **多站点插件系统**（site-provider SPI）：设计规格已弃（v3.0 被隐藏计划 D1 明确拒绝）。

## 5. 当前基线（替代旧文档的过期数字）

- 前端测试：`npx vitest run` 46 文件 / 392 测试全绿；`vue-tsc --noEmit` 0 错误。
- 后端测试：JUnit 71 个 `@Test`（11 文件）全绿；`bootJar` 可打包，Java 21。
- API 契约：`contracts/openapi.yaml` 61 路径（`/api/v1/` 前缀）；`contracts/sync-schemas.json` + `websocket-protocol.md` 与实作一致。
- 构建配方：`GRADLE_USER_HOME=<workspace>/.gradle-user-home`（沙盒只读 `~/.gradle`）+ AS JBR 21（`JAVA_HOME` 指向 JBR）+ gradle 9.4.1 wrapper。
