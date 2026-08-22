import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { filterSlotsApi, type FilterSlot } from '@/api/filterSlots'

const mockedGet = vi.mocked(client.get)
const mockedPut = vi.mocked(client.put)

const slot = (id: string): FilterSlot => ({ id, name: `slot-${id}`, pattern: '^a' })

beforeEach(() => {
  mockedGet.mockReset()
  mockedPut.mockReset()
})

describe('filterSlotsApi — 整体替换语义（T-F2 TH5）', () => {
  it('get unwraps data.slots', async () => {
    mockedGet.mockResolvedValue({ data: { slots: [slot('a'), slot('b')] } })
    await expect(filterSlotsApi.get()).resolves.toEqual([slot('a'), slot('b')])
    expect(mockedGet).toHaveBeenCalledWith('/download/slots')
  })

  it('get normalizes a missing/null slots list to an empty array', async () => {
    mockedGet.mockResolvedValue({ data: {} })
    await expect(filterSlotsApi.get()).resolves.toEqual([])

    mockedGet.mockResolvedValue({ data: { slots: null } })
    await expect(filterSlotsApi.get()).resolves.toEqual([])
  })

  it('put wraps the full list as {slots} and unwraps the echo', async () => {
    const next = [slot('x')]
    mockedPut.mockResolvedValue({ data: { slots: next } })
    await expect(filterSlotsApi.put(next)).resolves.toEqual(next)
    expect(mockedPut).toHaveBeenCalledWith('/download/slots', { slots: next })
  })

  it('put tolerates a null echo from older servers', async () => {
    mockedPut.mockResolvedValue({ data: { slots: undefined } })
    await expect(filterSlotsApi.put([slot('y')])).resolves.toEqual([])
  })
})
