import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { routes } from '../index'

function makeRouter() {
  return createRouter({ history: createMemoryHistory(), routes })
}

describe('router catch-all (UX-05)', () => {
  it('resolves unknown paths to the NotFound route', async () => {
    const router = makeRouter()
    await router.push('/nonexistent')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('NotFound')
  })

  it('resolves nested unknown paths to the NotFound route', async () => {
    const router = makeRouter()
    await router.push('/settings/does-not-exist')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('NotFound')
  })

  it('still matches known routes normally', async () => {
    const router = makeRouter()
    await router.push('/login')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('Login')
  })

  it('keeps the catch-all as the last route', () => {
    const last = routes[routes.length - 1]
    expect(last.name).toBe('NotFound')
    expect(last.path).toBe('/:pathMatch(.*)*')
  })
})
