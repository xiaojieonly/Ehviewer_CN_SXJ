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
  /**
   * F-UX5: 条目真实收藏槽位（Android 契约：-2 未收藏 / -1 默认夹 / 0-9 自定义夹）。
   * 后端 FavoriteItem 随行下发，♥ 徽章据此渲染真值。旧服务器不下发该字段时
   * 为 undefined——调用方以 `?? <页签号>` 兜底。
   */
  favoriteSlot?: number
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
