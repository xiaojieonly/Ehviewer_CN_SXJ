import { defineStore } from 'pinia'
import { ref } from 'vue'
import { preferencesApi, type Preferences, type GeneralPreferences, type ReaderPreferences, type PrivacyPreferences } from '@/api/preferences'

export const usePreferencesStore = defineStore('preferences', () => {
  const prefs = ref<Preferences | null>(null)
  const loading = ref(false)
  const loadError = ref(false)
  const saveSeq = ref(0)
  const saveError = ref<string | null>(null)

  let saveTimer: ReturnType<typeof setTimeout> | null = null

  let loadSeq = 0
  let dirty = false

  async function load() {
    const seq = ++loadSeq
    loading.value = true
    loadError.value = false
    try {
      const next = await preferencesApi.get()
      if (seq !== loadSeq || dirty) return
      prefs.value = next
    } catch (e) {
      if (seq !== loadSeq) return
      loadError.value = true
      console.error('Failed to load preferences', e)
    } finally {
      if (seq === loadSeq) loading.value = false
    }
  }

  function updateGeneral(patch: Partial<GeneralPreferences>) {
    if (!prefs.value) return
    prefs.value.general = { ...prefs.value.general, ...patch }
    dirty = true
    scheduleSave({ general: prefs.value.general })
  }

  function updateReader(patch: Partial<ReaderPreferences>) {
    if (!prefs.value) return
    prefs.value.reader = { ...prefs.value.reader, ...patch }
    dirty = true
    scheduleSave({ reader: prefs.value.reader })
  }

  function updatePrivacy(patch: Partial<PrivacyPreferences>) {
    if (!prefs.value) return
    prefs.value.privacy = { ...prefs.value.privacy, ...patch }
    dirty = true
    scheduleSave({ privacy: prefs.value.privacy })
  }

  function scheduleSave(payload: Partial<Preferences>) {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(async () => {
      try {
        await preferencesApi.update(payload)
        saveError.value = null
        saveSeq.value += 1
        dirty = false
      } catch (e) {
        saveError.value = e instanceof Error ? e.message : String(e)
        console.error('Failed to save preferences', e)
      }
    }, 600)
  }

  return { prefs, loading, loadError, saveSeq, saveError, load, updateGeneral, updateReader, updatePrivacy }
})
