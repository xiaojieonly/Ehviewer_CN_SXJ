# 0003: 三方模型与可配置同步冲突策略

状态：Accepted（2026-08-03）。契约落地：`contracts/sync-conflict-rules.md` v2.0、`contracts/sync-schemas.json#syncPolicy`、`contracts/openapi.yaml`（`/api/v1/sync/policy`）。

## 背景（产品模型，用户定义，权威）

1. WebUI 与 Android App 都可**单独**连接远端平台（画廊站）；
2. App 进入运行了 WebUI 的网络 → 后台自动同步，且**作为 WebUI 的客户端行动**，而非单独行动；
3. 两端数据同步冲突**默认以 Android App 为准**，用户可在高级面板切换。

v1 同步契约硬编码 LWW（+skew tie-break），无法表达「默认 App 为准」；单用户假设（ADR-0001）仍成立，但同一用户的两个端点代表不同操作意图，需要可配置仲裁。

## 决策

- **D1 策略可配**：`conflictStrategy` ∈ {`device_priority`(默认), `lww`, `web_priority`}；平台序 `android > web` 置常量。A/C 下同键冲突**无条件**优先级胜——不为长期离线场景做保护设计（理论上不存在长期不可访问场景），时间戳仅用于高水位/增量 pull/展示。`lww` = v1 完整语义，作为回退兜底。
- **D2 策略设置 App 权威**：policy 存服务器，随 pull 下发；WebUI 可改但下一次 android push 携带的 policy 覆盖之；WebUI 面板明示。
- **D3 客户端模式分档**：Tier-0 独立 / Tier-1 同步+流式（默认） / Tier-2 浏览代理 / Tier-3 下载托管（押后独立波）。档位 App 可选；Tier-2/3 路由复用 `MOCK_EH_BASE_URL` 拦截器模式。
- **D4 网络感知自动同步**：网络回调 + probe 已配对 baseUrl 分片；`autoSyncIntervalSec` 默认 900（0=仅网络变化触发）；新网络不自动配对；mDNS 后续可选。

## 推论

- 删除传播按策略：tombstone 实体（history/bookmark）任何策略下双向传播；soft 实体 B 不传播（v1 union），A/C 优先端删除无条件传播、非优先端删除在优先端持有时不传播（契约 v2 §3.8，MASTER §4.2 矩阵 P0 细化）。
- 粒度 Level-1（全局单选项）本波；Level-2 按实体组覆盖预留 schema 不做；Level-3 逐条仲裁永不做。
- 切换即时生效、不追溯重合并（B→A 后需在权威端重做删除；进 tooltip）。
- 兼容：旧 App×新服务器收敛于 App 视图；新 App×旧服务器降级不报错；B=完整回退兜底。
- preferences 不受策略影响（ADR-0001：后同步者即最终设置）。
- WebUI 新特性仅 PC 形态；App 未改动功能不重复测试（协作约定）。
