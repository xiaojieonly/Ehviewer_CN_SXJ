import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() },
}))

import client from '@/api/client'
import { commentApi } from '@/api/comment'

const mockedGet = vi.mocked(client.get)
const mockedPost = vi.mocked(client.post)

beforeEach(() => {
  mockedGet.mockReset()
  mockedPost.mockReset()
})

describe('commentApi（T-F2 TH5）', () => {
  it('listComments GETs /comment/list/{gid} and resolves the envelope', async () => {
    const comments = [
      { id: 1, uploader: 'a', comment: 'hi', time: '2026-08-01', score: 0 },
    ]
    mockedGet.mockResolvedValue({ data: { comments } })
    await expect(commentApi.listComments(42)).resolves.toEqual({ comments })
    expect(mockedGet).toHaveBeenCalledWith('/comment/list/42')
  })

  it('postComment posts the gid+text body shape', async () => {
    mockedPost.mockResolvedValue({ data: { success: true } })
    await commentApi.postComment(42, 'nice')
    expect(mockedPost).toHaveBeenCalledWith('/comment/post', { gid: 42, comment: 'nice' })
  })

  it('voteComment posts gid/commentId/vote with ±1 semantics preserved', async () => {
    mockedPost.mockResolvedValue({ data: { success: true } })
    await commentApi.voteComment(42, 7, -1)
    expect(mockedPost).toHaveBeenCalledWith('/comment/vote', {
      gid: 42,
      commentId: 7,
      vote: -1,
    })

    await commentApi.voteComment(42, 7, 1)
    expect(mockedPost).toHaveBeenLastCalledWith('/comment/vote', {
      gid: 42,
      commentId: 7,
      vote: 1,
    })
  })
})
