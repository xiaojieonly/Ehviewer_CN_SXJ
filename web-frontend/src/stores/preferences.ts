import { defineStore } from 'pinia'
import { ref } from 'vue'
import { preferencesApi, type Preferences, type GeneralPreferences, type ReaderPreferences, type PrivacyPreferences } from '@/api/preferences'

export const usePreferencesStore = defineStore('preferences', () => {
  const prefs = ref<Preferences | null>(null)
  const loading = ref(false)

  let saveTimer: ReturnType<typeof setTimeout> | null = null

  async function load() {
    loading.value = true
    try {
      prefs.value = await preferencesApi.get()
    } finally {
      loading.value = false
    }
  }

  function updateGeneral(patch: Partial<GeneralPreferences>) {
    if (!prefs.value) return
    prefs.value.general = { ...prefs.value.general, ...patch }
    scheduleSave({ general: prefs.value.general })
  }

  function updateReader(patch: Partial<ReaderPreferences>) {
    if (!prefs.value) return
    prefs.value.reader = { ...prefs.value.reader, ...patch }
    scheduleSave({ reader: prefs.value.reader })
  }

  function updatePrivacy(patch: Partial<PrivacyPreferences>) {
    if (!prefs.value) return
    prefs.value.privacy = { ...prefs.value.privacy, ...patch }
    scheduleSave({ privacy: prefs.value.privacy })
  }

  function scheduleSave(payload: Partial<Preferences>) {
    if (saveTimer) clearTimeout(saveTimer)
    saveTimer = setTimeout(async () => {
      try {
        await preferencesApi.update(payload)
      } catch (e) {
        console.error('Failed to save preferences', e)
      }
    }, 600)
  }

  return { prefs, loading, load, updateGeneral, updateReader, updatePrivacy }
})
