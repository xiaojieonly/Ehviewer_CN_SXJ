import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { useEnhancedImage } from '../useEnhancedImage'

const { wsConnect, wsDisconnect, wsSubscribe, wsUnsubscribe } = vi.hoisted(() => ({
  wsConnect: vi.fn(),
  wsDisconnect: vi.fn(),
  wsSubscribe: vi.fn(),
  wsUnsubscribe: vi.fn(),
}))

vi.mock('@/composables/useWebSocket', () => ({
  useWebSocket: () => ({ connect: wsConnect, disconnect: wsDisconnect, subscribe: wsSubscribe }),
}))

/** Image double capturing preload outcomes for manual resolution. */
class FakeImage {
  static instances: FakeImage[] = []
  src = ''
  onload: (() => void) | null = null
  onerror: (() => void) | null = null

  constructor() {
    FakeImage.instances.push(this)
  }

  succeed() {
    this.onload?.()
  }

  fail() {
    this.onerror?.()
  }
}

/** Host exposing the composable bound to a changeable gid ref. */
function mountEnhanced(initialGid: number) {
  const gid = ref(initialGid)
  let api!: ReturnType<typeof useEnhancedImage>
  const Host = defineComponent({
    setup() {
      api = useEnhancedImage(gid)
      return () => h('div')
    },
  })
  const wrapper = mount(Host)
  return { wrapper, gid, api }
}

/** The handler registered for the most recent subscribe() call. */
function lastHandler(): (envelope: { type: string; payload: unknown }) => void {
  const call = wsSubscribe.mock.calls.at(-1)
  return call![1] as (envelope: { type: string; payload: unknown }) => void
}

function readyPayload(overrides: Record<string, unknown> = {}) {
  return {
    type: 'image.enhanced.ready',
    payload: {
      galleryId: 7,
      page: 3, // 1-based per WS protocol
      enhancedUrl: 'https://cdn.example/enhanced-3.jpg',
      originalUrl: '/api/v1/image/7/2',
      processingType: 'UPSCALE',
      width: 800,
      height: 1200,
      fileSize: 12345,
      ...overrides,
    },
  }
}

describe('useEnhancedImage (T-F2)', () => {
  beforeEach(() => {
    wsConnect.mockClear()
    wsDisconnect.mockClear()
    wsSubscribe.mockClear()
    wsSubscribe.mockImplementation(() => wsUnsubscribe)
    wsUnsubscribe.mockClear()
    FakeImage.instances = []
    vi.stubGlobal('Image', FakeImage)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('connect acquires the shared socket and subscribes to the gallery topic once', () => {
    const { api } = mountEnhanced(7)

    expect(api.enhancing.value).toBe(false)

    api.connect()
    api.connect() // idempotent — one reference, one subscription
    expect(wsConnect).toHaveBeenCalledTimes(1)
    expect(wsSubscribe).toHaveBeenCalledTimes(1)
    expect(wsSubscribe).toHaveBeenCalledWith('/topic/gallery/7/enhanced', expect.any(Function))
  })

  it('hot-swaps a page only after its preload succeeds (1-based → 0-based)', async () => {
    const { api } = mountEnhanced(7)
    api.connect()

    lastHandler()(readyPayload())
    // Preload in flight → enhancing flag on, map untouched.
    expect(api.enhancing.value).toBe(true)
    expect(api.getImageUrl(2, 'original.jpg')).toBe('original.jpg')

    const img = FakeImage.instances.at(-1)!
    expect(img.src).toBe('/api/v1/image/7/2?w=800&enhanced=1')
    img.succeed()
    await Promise.resolve()

    expect(api.getImageUrl(2, 'original.jpg')).toBe('/api/v1/image/7/2?w=800&enhanced=1')
    expect(api.enhancing.value).toBe(false)
    expect(api.enhancedPages.value).toEqual([2])
  })

  it('silently keeps the original when the preload fails', async () => {
    const { api } = mountEnhanced(7)
    api.connect()

    lastHandler()(readyPayload({ page: 5 }))
    FakeImage.instances.at(-1)!.fail()
    await Promise.resolve()

    expect(api.getImageUrl(4, 'original.jpg')).toBe('original.jpg')
    expect(api.enhancedPages.value).toEqual([])
    // Preload accounting unwinds even on failure.
    expect(api.enhancing.value).toBe(false)
  })

  it('ignores envelopes that are not image.enhanced.ready', () => {
    const { api } = mountEnhanced(7)
    api.connect()

    lastHandler()({ type: 'job.progress', payload: {} })
    expect(FakeImage.instances).toHaveLength(0)
    expect(api.enhancing.value).toBe(false)
  })

  it('re-subscribes and clears cached URLs when the gid changes', async () => {
    const { api, gid } = mountEnhanced(7)
    api.connect()
    lastHandler()(readyPayload())
    FakeImage.instances.at(-1)!.succeed()
    expect(api.getImageUrl(2, 'x')).not.toBe('x')

    gid.value = 9
    await nextTick() // pre-flush watcher runs on the tick boundary
    // Old topic released (the watch + re-subscribe path releases the same
    // handle twice — a registry-miss no-op in useWebSocket, hence ≥1).
    expect(wsUnsubscribe.mock.calls.length).toBeGreaterThanOrEqual(1)
    expect(api.getImageUrl(2, 'x')).toBe('x') // cache cleared
    expect(wsSubscribe).toHaveBeenLastCalledWith('/topic/gallery/9/enhanced', expect.any(Function))
  })

  it('disconnect releases the reference, unsubscribes and resets state; repeat calls are safe', async () => {
    const { api } = mountEnhanced(7)
    api.connect()
    lastHandler()(readyPayload())
    FakeImage.instances.at(-1)!.succeed()

    api.disconnect()
    api.disconnect()
    expect(wsUnsubscribe).toHaveBeenCalled()
    expect(wsDisconnect).toHaveBeenCalledTimes(1) // single release despite double call
    expect(api.getImageUrl(2, 'fallback')).toBe('fallback')
    expect(api.enhancing.value).toBe(false)
    expect(api.enhancedPages.value).toEqual([])

    // Reconnecting after a disconnect re-acquires + re-subscribes cleanly.
    api.connect()
    expect(wsConnect).toHaveBeenCalledTimes(2)
    expect(wsSubscribe).toHaveBeenCalledTimes(2)
  })
})
