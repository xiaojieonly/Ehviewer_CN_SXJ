<!--
  SearchBar.vue — web replica of `SearchBar.java` +
  `app/src/main/res/layout/widget_search_bar.xml`.

  Floating CardView (`- -card-radius` 2px / `- -card-elevation` 2dp shadow,
  `- -gallery-search-bar-margin-h/v` outer margins) holding one 48px row:
  48px menu icon (12px padding → 24px glyph) · title (20sp,
  `- -text-color-secondary`, single line) swapped with the edit text (16sp,
  transparent background, IME `actionSearch`) · 48px action icon — plus the
  clip-animated (300ms, decelerate-quart) suggestion list under a 1px divider.

  State machine mirrors Android `SearchBar.STATE_*`:
    normal (= title mode) → search (= input mode) → search-list (input +
    dropdown). The component is fully controlled: it reports transitions
    (`update:state`) and the scene decides, exactly like
    `SearchBar.Helper` / `OnStateChangeListener`.

  F6 additive (per search-component task spec): a clear (✕) button inside the
  edit row when the query is non-empty.
-->
<template>
  <div class="search-bar" :class="`search-bar--${state}`">
    <div class="search-bar__card">
      <div class="search-bar__row">
        <!-- Left (menu) icon — 48×48 with 12px padding; spacer keeps the
             Android 48px title margins when no icon is set. -->
        <button
          v-if="leftIcon"
          type="button"
          class="search-bar__icon"
          aria-label="Open menu"
          @click="emit('click-menu')"
        >
          <AppIcon :name="leftIcon" />
        </button>
        <span v-else class="search-bar__icon-spacer" aria-hidden="true" />

        <!-- Center: title (normal) or edit text (search / search-list). -->
        <button
          v-if="state === 'normal'"
          type="button"
          class="search-bar__title"
          @click="emit('click-title')"
        >
          {{ title }}
        </button>
        <div v-else class="search-bar__edit">
          <input
            ref="inputRef"
            class="search-bar__input"
            type="text"
            :value="query"
            :placeholder="hint"
            enterkeyhint="search"
            autocomplete="off"
            aria-label="Search query"
            @input="onInput"
            @keydown.enter.prevent="applySearch"
            @keydown.esc="emit('back')"
            @keydown.backspace="onBackspace"
          />
          <button
            v-if="query"
            type="button"
            class="search-bar__clear"
            aria-label="Clear query"
            @click="clearQuery"
          >
            <AppIcon name="close-dark" size="20px" />
          </button>
        </div>

        <!-- Right (action) icon. -->
        <button
          v-if="rightIcon"
          type="button"
          class="search-bar__icon"
          aria-label="Search action"
          @click="emit('click-action')"
        >
          <AppIcon :name="rightIcon" />
        </button>
        <span v-else class="search-bar__icon-spacer" aria-hidden="true" />
      </div>

      <!-- Suggestion list — Android `list_container`: 1px header divider +
           rows separated by 1px dividers, revealed with a 300ms clip. -->
      <div
        class="search-bar__list-clip"
        :class="{ 'is-open': listOpen }"
        :style="{ maxHeight: listOpen ? `${suggestions.length * 48}px` : '0' }"
        :aria-hidden="!listOpen"
      >
        <div class="search-bar__list-header" />
        <ul class="search-bar__list">
          <li v-for="(suggestion, index) in suggestions" :key="suggestion.text + index">
            <button
              type="button"
              class="search-bar__suggestion"
              @click="emit('select-suggestion', suggestion, index)"
              @contextmenu.prevent="emit('dismiss-suggestion', suggestion, index)"
            >
              <span class="search-bar__suggestion-text">{{ suggestion.text }}</span>
              <span v-if="suggestion.hint" class="search-bar__suggestion-hint">
                {{ suggestion.hint }}
              </span>
            </button>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { SearchBarEmits, SearchBarProps } from '@/types/components'
import AppIcon from '@/components/atoms/AppIcon.vue'

const props = withDefaults(defineProps<SearchBarProps>(), {
  state: 'normal',
  title: '',
  query: '',
  hint: 'Search',
  leftIcon: 'reorder',
  rightIcon: 'magnify-dark',
  allowEmptySearch: true,
  suggestions: () => [],
})

const emit = defineEmits<SearchBarEmits>()

const inputRef = ref<HTMLInputElement | null>(null)

const listOpen = computed<boolean>(
  () => props.state === 'search-list' && props.suggestions.length > 0,
)

/** Android `applySearch()` — respects `allowEmptySearch`. */
function applySearch(): void {
  const q = props.query ?? ''
  if (!q && !props.allowEmptySearch) {
    return
  }
  emit('search', q)
}

function onInput(event: Event): void {
  emit('update:query', (event.target as HTMLInputElement).value)
}

/** Android `onSearchEditTextBackPressed` — back with an empty edit text. */
function onBackspace(): void {
  if (!props.query) {
    emit('back')
  }
}

function clearQuery(): void {
  emit('update:query', '')
  void nextTick(() => inputRef.value?.focus())
}

// Focus the edit text when entering a search state (Android shows the IME);
// drop focus when returning to the title row.
watch(
  () => props.state,
  (state) => {
    void nextTick(() => {
      if (state === 'search' || state === 'search-list') {
        inputRef.value?.focus()
        inputRef.value?.select()
      } else {
        inputRef.value?.blur()
      }
    })
  },
)
</script>

<style scoped>
.search-bar {
  /* Floating above the content list — Android gallery_search_bar margins. */
  margin: var(--gallery-search-bar-margin-v) var(--gallery-search-bar-margin-h);
}

.search-bar__card {
  background: var(--color-background-floating);
  border-radius: var(--card-radius);
  box-shadow:
    0 var(--card-elevation) 4px var(--shadow-color),
    0 0 1px var(--shadow-color);
  overflow: hidden;
  transition: box-shadow 200ms var(--ease-decelerate-quart);
}

.search-bar--search .search-bar__card,
.search-bar--search-list .search-bar__card {
  box-shadow:
    0 calc(var(--card-elevation) * 2) 8px var(--shadow-color),
    0 0 1px var(--shadow-color);
}

.search-bar__row {
  display: flex;
  align-items: center;
  height: 48px;
}

.search-bar__icon {
  flex: 0 0 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  padding: 12px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-primary);
  cursor: pointer;
  transition: background-color 150ms var(--ease-decelerate-quart);
}

.search-bar__icon:hover {
  background: var(--color-surface);
}

.search-bar__icon:active {
  background: var(--color-surface-activated);
}

.search-bar__icon-spacer {
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
}

/* Title row — Android `search_title`: 20sp, textColorSecondary, one line. */
.search-bar__title {
  flex: 1 1 auto;
  min-width: 0;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  font-size: var(--text-little-large);
  color: var(--text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
  transition: color 150ms var(--ease-decelerate-quart);
}

.search-bar__title:hover {
  color: var(--text-color-primary);
}

/* Edit row — Android `SearchEditText`: 16sp, null background, one line. */
.search-bar__edit {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 4px;
  height: 48px;
}

.search-bar__input {
  flex: 1 1 auto;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--text-little-small);
  color: var(--text-color-primary);
  caret-color: var(--color-accent);
}

.search-bar__input::placeholder {
  color: var(--text-color-secondary);
}

.search-bar__clear {
  flex: 0 0 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--drawable-color-secondary);
  cursor: pointer;
  transition:
    background-color 150ms var(--ease-decelerate-quart),
    color 150ms var(--ease-decelerate-quart);
}

.search-bar__clear:hover {
  background: var(--color-surface);
  color: var(--drawable-color-primary);
}

/* Suggestion list — Android `list_container` clip animation (300ms). */
.search-bar__list-clip {
  overflow: hidden;
  transition: max-height 300ms var(--ease-decelerate-quart);
}

.search-bar__list-header {
  height: 1px;
  background: var(--color-divider);
}

.search-bar__list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.search-bar__list li + li .search-bar__suggestion {
  border-top: 1px solid var(--color-divider);
}

.search-bar__suggestion {
  display: flex;
  align-items: baseline;
  gap: 12px;
  width: 100%;
  height: 48px;
  padding: 0 16px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color 120ms var(--ease-decelerate-quart);
}

.search-bar__suggestion:hover {
  background: var(--color-surface);
}

.search-bar__suggestion-text {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--text-little-small);
  color: var(--text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.search-bar__suggestion-hint {
  flex: 0 1 auto;
  font-size: var(--text-small);
  color: var(--text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
