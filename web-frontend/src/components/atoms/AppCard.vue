<template>
  <div
    class="app-card"
    :class="`app-card--${mode}`"
    role="button"
    tabindex="0"
    @click="emit('click', gallery)"
    @keydown.enter="emit('click', gallery)"
  >
    <!-- List mode: 80×120 thumb left + info column right -->
    <template v-if="mode === 'list'">
      <div class="app-card__thumb">
        <img :src="gallery.thumb" :alt="gallery.title" loading="lazy" />
      </div>
      <div class="app-card__info">
        <h3 class="app-card__title">{{ gallery.title }}</h3>
        <p v-if="gallery.uploader" class="app-card__uploader">{{ gallery.uploader }}</p>
        <RatingStars :rating="gallery.rating" />
        <CategoryChip v-if="categoryKey" :category="categoryKey" />
      </div>
    </template>

    <!-- Grid mode: image tile + corner triangle + language badge -->
    <template v-else>
      <div class="app-card__tile" :style="{ aspectRatio: tileAspect }">
        <img :src="gallery.thumb" :alt="gallery.title" loading="lazy" />
        <CategoryTriangle v-if="categoryKey" class="app-card__triangle" :category="categoryKey" />
        <span v-if="gallery.simpleLanguage" class="app-card__language">
          {{ gallery.simpleLanguage }}
        </span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * AppCard — the gallery card in both list and grid modes (roadmap §卡片规范,
 * replicating the `scene_gallery_list.xml` item layouts).
 *
 * Card surface: 2dp radius / 2dp elevation / 2dp outer margin, background =
 * `--color-surface` (`contentColorPrimary`, theme-aware).
 *
 * - `list`: fixed 80×120dp (2:3) thumbnail left; right column = title (16sp,
 *   `textColorPrimary`, max 2 lines end-ellipsized), uploader (14sp,
 *   `textColorSecondary`, single line), `RatingStars`, `CategoryChip`.
 * - `grid`: image tile (TileThumb aspect clamping 0.33–1.5, default 0.67) +
 *   32×24dp `CategoryTriangle` top-right + language code badge
 *   (`gallery.simpleLanguage`, 10sp white bold).
 *
 * The numeric `gallery.category` is an `EhConfig` bit value; it is converted
 * to a `GalleryCategory` via `CATEGORY_BY_BIT` before driving the chip /
 * triangle (unknown bits render neither).
 */
import { computed } from 'vue'
import { CATEGORY_BY_BIT, type GalleryInfo } from '@/types/components'
import RatingStars from './RatingStars.vue'
import CategoryChip from './CategoryChip.vue'
import CategoryTriangle from './CategoryTriangle.vue'

const props = defineProps<{
  /** Gallery to render (`category` is a `CATEGORY_BIT_VALUES` bit). */
  gallery: GalleryInfo
  /** Display mode, kept in sync with the list screen's layout mode. */
  mode: 'list' | 'grid'
}>()

const emit = defineEmits<{
  /** Card tapped — open gallery detail. */
  (e: 'click', gallery: GalleryInfo): void
}>()

/** Numeric category bit → `GalleryCategory` key (undefined when unknown). */
const categoryKey = computed(() => CATEGORY_BY_BIT[props.gallery.category])

/**
 * Grid tile aspect ratio, replicating `TileThumb.setThumbSize` clamping:
 * `w / h` clamped into [0.33, 1.5], falling back to 0.67 when unknown.
 */
const tileAspect = computed<string>(() => {
  const { thumbWidth: w, thumbHeight: h } = props.gallery
  if (!w || !h) return '0.67'
  return String(Math.min(1.5, Math.max(0.33, w / h)))
})
</script>

<style scoped>
.app-card {
  background: var(--color-surface);
  border-radius: var(--card-radius); /* 2px */
  box-shadow: 0 var(--card-elevation) var(--card-max-elevation) var(--shadow-color);
  margin: 2px;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 160ms var(--ease-decelerate-quart);
}

.app-card:hover {
  box-shadow: 0 var(--card-elevation) calc(var(--card-max-elevation) * 3) var(--shadow-color);
}

.app-card:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 1px;
}

/* ---------------------------------------------------------------- list --- */
.app-card--list {
  display: flex;
  align-items: stretch;
}

.app-card__thumb {
  flex: 0 0 var(--thumb-list-width); /* 80px */
  width: var(--thumb-list-width);
  height: var(--thumb-list-height); /* 120px */
  overflow: hidden;
  background: var(--color-divider);
}

.app-card__thumb img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-card__info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--spacing); /* 8px */
}

.app-card__title {
  margin: 0;
  font-size: var(--text-little-small); /* 16px */
  font-weight: 500;
  line-height: 1.35;
  color: var(--text-color-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.app-card__uploader {
  margin: 0;
  font-size: var(--text-small); /* 14px */
  color: var(--text-color-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ---------------------------------------------------------------- grid --- */
.app-card--grid {
  display: block;
}

.app-card__tile {
  position: relative;
  width: 100%;
  overflow: hidden;
  background: var(--color-divider);
}

.app-card__tile img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.app-card__triangle {
  position: absolute;
  top: 0;
  right: 0;
}

.app-card__language {
  position: absolute;
  left: 4px;
  bottom: 4px;
  font-size: 10px; /* 10sp — no dedicated token; spec value from roadmap */
  font-weight: 700;
  color: var(--color-white);
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.72);
  line-height: 1;
}
</style>
