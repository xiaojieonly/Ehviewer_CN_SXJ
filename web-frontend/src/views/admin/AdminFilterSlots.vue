<!--
  AdminFilterSlots.vue — 管理面板 · 筛选槽位。

  槽位是命名正则预设（{id, name, pattern}），下载/收藏/历史列表页可一键套用；
  服务端整体存储（PUT /api/v1/download/slots 全量替换），随备份导出。

  交互：所有变更先在本地数组生效 → filterSlotsApi.put 持久化；失败 snack
  提示并回滚。客户端校验：name 非空、pattern 可用 new RegExp 编译（失败红字
  提示「正则表达式无效」）。上限 20 个（与服务端一致），满时禁用添加。
-->
<template>
  <div class="admin-filter-slots">
    <header class="admin-filter-slots__toolbar">
      <h1 class="admin-filter-slots__title">筛选槽位</h1>
    </header>
    <p class="admin-filter-slots__desc">命名正则筛选，下载/收藏/历史页可一键套用 · 服务端存储，随备份导出</p>

    <main class="admin-filter-slots__body">
      <div class="admin-filter-slots__column">
        <!-- ═══ 槽位列表 ══════════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="槽位列表" />
          <PrefCard>
            <p v-if="slots.length === 0" class="admin-filter-slots__empty">还没有筛选槽位</p>
            <div v-for="slot in slots" :key="slot.id" class="slot-row">
              <div class="slot-row__info">
                <span class="slot-row__name">{{ slot.name }}</span>
                <code class="slot-row__pattern">{{ slot.pattern }}</code>
              </div>
              <button
                type="button"
                class="pref-action-btn"
                :aria-label="`删除 ${slot.name}`"
                @click="removeSlot(slot.id)"
              >
                <AppIcon name="delete-dark" size="20px" />
              </button>
            </div>
          </PrefCard>
        </section>

        <!-- ═══ 添加槽位 ══════════════════════════════════════════════════ -->
        <section>
          <SectionHeader title="添加槽位" />
          <PrefCard>
            <div class="add-form">
              <div class="add-form__fields">
                <div class="add-form__field">
                  <AppTextField
                    v-model="nameDraft"
                    label="名称"
                    :error-text="nameError"
                    :maxlength="32"
                    @update:model-value="nameError = ''"
                  />
                </div>
                <div class="add-form__field">
                  <AppTextField
                    v-model="patternDraft"
                    label="正则表达式"
                    :error-text="patternError"
                    :maxlength="256"
                    @update:model-value="patternError = ''"
                  />
                </div>
              </div>
              <div class="add-form__actions">
                <span v-if="atLimit" class="add-form__hint">最多 20 个槽位，已达上限</span>
                <button type="button" class="btn-primary" :disabled="atLimit" @click="addSlot">添加</button>
              </div>
            </div>
          </PrefCard>
        </section>
      </div>
    </main>

    <!-- Snackbar. -->
    <Transition name="snack">
      <div v-if="snack" class="snackbar" role="status">{{ snack }}</div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import AppIcon from '@/components/atoms/AppIcon.vue'
import { AppTextField, PrefCard, SectionHeader } from '@/components/form'
import { filterSlotsApi, type FilterSlot } from '@/api/filterSlots'

/** 与服务端一致的槽位数量上限。 */
const MAX_SLOTS = 20

const slots = ref<FilterSlot[]>([])
const nameDraft = ref('')
const patternDraft = ref('')
const nameError = ref('')
const patternError = ref('')

const snack = ref('')
let snackTimer: number | undefined

const atLimit = computed(() => slots.value.length >= MAX_SLOTS)

onMounted(async () => {
  try {
    slots.value = await filterSlotsApi.get()
  } catch (error) {
    console.error('[AdminFilterSlots] failed to load slots', error)
    slots.value = []
  }
})

onBeforeUnmount(() => {
  if (snackTimer) window.clearTimeout(snackTimer)
})

function showSnack(message: string): void {
  snack.value = message
  if (snackTimer) window.clearTimeout(snackTimer)
  snackTimer = window.setTimeout(() => {
    snack.value = ''
  }, 2600)
}

function genId(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi && typeof cryptoApi.randomUUID === 'function') return cryptoApi.randomUUID()
  return `slot-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

function addSlot(): void {
  if (atLimit.value) return
  const name = nameDraft.value.trim()
  const pattern = patternDraft.value.trim()
  nameError.value = ''
  patternError.value = ''
  if (!name) {
    nameError.value = '名称不能为空'
    return
  }
  try {
    new RegExp(pattern)
  } catch {
    patternError.value = '正则表达式无效'
    return
  }
  nameDraft.value = ''
  patternDraft.value = ''
  void persist([...slots.value, { id: genId(), name, pattern }])
}

function removeSlot(id: string): void {
  void persist(slots.value.filter((s) => s.id !== id))
}

/** 改本地数组 → PUT 全量持久化；失败 snack 提示并回滚。 */
async function persist(next: FilterSlot[]): Promise<void> {
  const previous = slots.value
  slots.value = next
  try {
    const echoed = await filterSlotsApi.put(next)
    if (echoed) slots.value = echoed
  } catch (error) {
    console.error('[AdminFilterSlots] failed to persist slots', error)
    slots.value = previous
    showSnack('保存失败，已还原')
  }
}
</script>

<style scoped>
/* Scene shell — content column lives inside AdminLayout. */
.admin-filter-slots {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--color-bg);
}

/* --------------------------------- toolbar -------------------------------- */

.admin-filter-slots__toolbar {
  flex: 0 0 auto;
  padding: 16px var(--keyline-margin) 0;
}

.admin-filter-slots__title {
  margin: 0;
  font-size: clamp(17px, 20px, 24px);
  font-weight: 600;
  letter-spacing: 0.01em;
  color: var(--text-color-primary);
}

.admin-filter-slots__desc {
  margin: 6px var(--keyline-margin) 0;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
  line-height: 1.5;
}

/* ---------------------------------- body ---------------------------------- */

.admin-filter-slots__body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.admin-filter-slots__column {
  max-width: 760px;
  margin: 0 auto;
  padding: 4px var(--keyline-margin) calc(56px + var(--safe-area-bottom));
}

.admin-filter-slots__empty {
  margin: 0;
  padding: 28px var(--keyline-margin, 16px);
  text-align: center;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
}

/* --------------------------------- slot row ------------------------------- */

.slot-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px var(--keyline-margin, 16px);
}

.slot-row__info {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.slot-row__name {
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  color: var(--text-color-primary);
}

.slot-row__pattern {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: var(--text-super-small, 12px);
  color: var(--text-color-secondary);
  overflow-wrap: anywhere;
}

/* Row-level action button (same as AdminDownload). */
.pref-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-secondary);
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.pref-action-btn:hover {
  background: var(--color-surface);
}

/* --------------------------------- add form ------------------------------- */

.add-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px var(--keyline-margin, 16px);
}

.add-form__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.add-form__field {
  flex: 1 1 220px;
  min-width: 0;
}

.add-form__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.add-form__hint {
  font-size: var(--text-super-small, 12px);
  color: var(--drawable-color-secondary);
}

/* --------------------------------- buttons -------------------------------- */

.btn-primary {
  padding: 9px 22px;
  border: none;
  border-radius: var(--card-radius);
  background: var(--color-primary);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
  box-shadow: 0 1px 3px var(--shadow-color);
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    transform 120ms var(--ease-decelerate-quart);
}

.btn-primary:hover:not(:disabled) {
  background: var(--color-primary-dark);
}

.btn-primary:active:not(:disabled) {
  transform: scale(0.97);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: default;
}

/* --------------------------------- snackbar -------------------------------- */

.snackbar {
  position: fixed;
  left: 50%;
  bottom: calc(24px + var(--safe-area-bottom));
  translate: -50% 0;
  z-index: 300;
  max-width: min(480px, calc(100vw - 32px));
  padding: 12px 20px;
  border-radius: var(--card-radius);
  background: var(--gallery-slider-background);
  color: var(--color-white);
  font-size: clamp(13px, 14px, 16px);
  box-shadow: 0 4px 12px var(--shadow-color);
}

.snack-enter-active,
.snack-leave-active {
  transition:
    opacity var(--duration-scene-opacity) var(--ease-decelerate-quart),
    translate var(--duration-scene-translate) var(--ease-decelerate-quint);
}

.snack-enter-from,
.snack-leave-to {
  opacity: 0;
  translate: -50% 12px;
}

@media (prefers-reduced-motion: reduce) {
  .snack-enter-active,
  .snack-leave-active {
    transition: none;
  }
}
</style>
