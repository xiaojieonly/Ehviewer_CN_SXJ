import client from './client'

export interface CommentItem {
  id: number
  uploader: string
  comment: string
  time: string
  score: number
}

export interface CommentListResponse {
  comments: CommentItem[]
}

export const commentApi = {
  async listComments(gid: number): Promise<CommentListResponse> {
    const { data } = await client.get(`/comment/list/${gid}`)
    return data
  },

  async postComment(gid: number, comment: string): Promise<{ success: boolean }> {
    const { data } = await client.post('/comment/post', { gid, comment })
    return data
  },

  async voteComment(gid: number, commentId: number, vote: number): Promise<{ success: boolean }> {
    const { data } = await client.post('/comment/vote', { gid, commentId, vote })
    return data
  },
}
