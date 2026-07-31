import { describe, expect, it } from 'vitest'
import { isOfflineError, isOfflinePayload, OfflineError } from '@/api/client'

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
