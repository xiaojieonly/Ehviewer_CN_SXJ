import client from './client'

export interface FavoriteItem {
  gid: number
  token: string
  title: string
  titleJpn: string
  thumb: string
  category: number
  rating: number
  uploader: string | null
  posted: string | null
}

export interface FavoriteListResponse {
  favorites: FavoriteItem[]
  totalPages: number
  currentPage: number
}

export const favoriteApi = {
  async listFavorites(slot = 0, page = 1): Promise<FavoriteListResponse> {
    const { data } = await client.get(`/favorite/list?slot=${slot}&page=${page}`)
    return data
  },

  async addFavorite(gid: number, token: string, category = 0): Promise<{ success: boolean }> {
    const { data } = await client.post('/favorite/add', { gid, token, category })
    return data
  },

  async removeFavorite(gid: number, token = '', category = 0): Promise<{ success: boolean }> {
    const { data } = await client.delete('/favorite/remove', { data: { gid, token, category } })
    return data
  },
}
