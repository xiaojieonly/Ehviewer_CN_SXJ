/**
 * 实时对账失效标记（WP8 任务 2）——前端侧的多端写感知分发器。
 *
 * **模块级单例（非 pinia）**：App.vue 壳持有唯一的 /topic/sync 订阅，收到
 * `sync.update` 后按 `payload.entities` 逐实体 bump；各列表视图 watch
 * `consume(entity)` 的返回值静默重拉（照 availability.ts 的模块级 reactive
 * 风格——无初始化时序问题，视图直接 import 消费）。
 *
 * 计数器语义（watch 可追踪）：每次 bump 对传入实体各 +1。consume 只**读**
 * 计数（缺省 0），不改值——watch 的触发源是 bump 引发的响应式变更，消费方
 * 无需（也不应该）手动清零。非 immediate watch 天然跳过首帧，不打扰初次
 * 加载。
 */
import { reactive } from 'vue'

export const syncInvalidation = reactive<{
  /** 实体名 → 累计失效次数（单调递增；未 bump 过的实体读出 0）。 */
  versionMap: Record<string, number>
  /** 记一次实体失效（/topic/sync 的 entities 逐实体 +1）。 */
  bump(entities: string[]): void
  /**
   * 读某实体当前失效计数（缺省 0）。getter——经响应式代理读取，返回值可被
   * watch 追踪（watch(() => syncInvalidation.consume('history'), …)）。
   */
  consume(entity: string): number
}>({
  versionMap: {},

  bump(entities: string[]): void {
    for (const entity of entities) {
      this.versionMap[entity] = (this.versionMap[entity] ?? 0) + 1
    }
  },

  consume(entity: string): number {
    return this.versionMap[entity] ?? 0
  },
})
