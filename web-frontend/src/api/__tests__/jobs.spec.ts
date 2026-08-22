import { describe, expect, it, vi, beforeEach } from 'vitest'
import type { Job } from '@/api/jobs'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { jobsApi } from '@/api/jobs'

const mockedGet = vi.mocked(client.get)

function jobFixture(overrides: Partial<Job> = {}): Job {
  return {
    jobId: 'job-1',
    type: 'EXPORT',
    state: 'PENDING',
    stage: null,
    percent: 0,
    processed: 0,
    total: 0,
    startedAt: null,
    completedAt: null,
    error: null,
    result: null,
    ...overrides,
  }
}

beforeEach(() => {
  mockedGet.mockReset()
})

describe('jobsApi — Job 查询（T-F2 TH5）', () => {
  it('getJob interpolates the id into the path', async () => {
    const job = jobFixture({ state: 'RUNNING', percent: 40 })
    mockedGet.mockResolvedValue({ data: job })
    await expect(jobsApi.getJob('job-1')).resolves.toBe(job)
    expect(mockedGet).toHaveBeenCalledWith('/jobs/job-1')
  })

  it('getActiveJob sends the type as a query param', async () => {
    const job = jobFixture({ type: 'RESTORE' })
    mockedGet.mockResolvedValue({ data: job })
    await expect(jobsApi.getActiveJob('RESTORE')).resolves.toBe(job)
    expect(mockedGet).toHaveBeenCalledWith('/jobs/active', { params: { type: 'RESTORE' } })
  })
})
