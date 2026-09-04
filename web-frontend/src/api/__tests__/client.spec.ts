import { describe, expect, it } from 'vitest'
import {
  EhUnavailableError,
  isEhUnavailableError,
  isOfflineError,
  isOfflinePayload,
  OfflineError,
} from '@/api/client'

describe('isOfflinePayload', () => {
  it('recognizes the SW offline marker as an object payload', () => {
    expect(isOfflinePayload(503, { error: 'offline', message: 'No cached data available' })).toBe(true)
  })

  it('recognizes the marker when the payload arrives as a string', () => {
    expect(isOfflinePayload(503, '{"error":"offline"}')).toBe(true)
  })

  it('rejects other status codes', () => {
    expect(isOfflinePayload(500, { error: 'offline' })).toBe(false)
    expect(isOfflinePayload(200, { error: 'offline' })).toBe(false)
  })

  it('rejects 503 without the offline marker', () => {
    expect(isOfflinePayload(503, { error: 'boom' })).toBe(false)
    expect(isOfflinePayload(503, 'server error')).toBe(false)
    expect(isOfflinePayload(503, undefined)).toBe(false)
  })
})

describe('OfflineError', () => {
  it('is detected by the isOfflineError type guard', () => {
    expect(isOfflineError(new OfflineError())).toBe(true)
    expect(isOfflineError(new Error('offline'))).toBe(false)
    expect(isOfflineError(null)).toBe(false)
  })
})

describe('EhUnavailableError — EH 熔断（plan-2026-08-30 §3.2/§4.1）', () => {
  it('carries the default local-only message when constructed bare', () => {
    expect(new EhUnavailableError().message).toBe('EH 平台当前不可达，仅显示本地内容')
  })

  it('is detected by the isEhUnavailableError type guard', () => {
    expect(isEhUnavailableError(new EhUnavailableError('x'))).toBe(true)
    expect(isEhUnavailableError(new Error('x'))).toBe(false)
    expect(isEhUnavailableError(null)).toBe(false)
  })

  it('recognizes the raw axios-shaped 404 envelope (interceptor wrapper form)', () => {
    const axiosError = {
      response: {
        status: 404,
        data: { error: { code: 'EH_UNAVAILABLE', message: 'EH 平台当前不可达，仅显示本地内容' } },
      },
    }
    expect(isEhUnavailableError(axiosError)).toBe(true)
    const other = { response: { status: 404, data: { error: { code: 'NOT_FOUND' } } } }
    expect(isEhUnavailableError(other)).toBe(false)
    const noEnvelope = { response: { status: 404, data: 'plain text' } }
    expect(isEhUnavailableError(noEnvelope)).toBe(false)
  })
})

describe('request interceptor — 隐私打码标记头（2026-09-04）', () => {
  it('打码开：请求携带 X-Privacy-Mask: 1（服务端据此脱敏响应）；关：不携带', async () => {
    const client = (await import('@/api/client')).default
    const { setPrivacyMaskEnabled } = await import('@/utils/privacyMask')
    const original = client.defaults.adapter
    let seenHeaders: any = null
    client.defaults.adapter = async (config) => {
      seenHeaders = config.headers
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config } as any
    }
    try {
      setPrivacyMaskEnabled(true)
      await client.get('/probe')
      expect(seenHeaders.get('X-Privacy-Mask')).toBe('1')

      setPrivacyMaskEnabled(false)
      await client.get('/probe')
      expect(seenHeaders.get('X-Privacy-Mask')).toBeUndefined()
    } finally {
      client.defaults.adapter = original
      setPrivacyMaskEnabled(false)
    }
  })
})
