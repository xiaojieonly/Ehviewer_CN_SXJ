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

  /**
   * `slot` is the optional target folder (Android favoriteSlot semantics:
   * -1 default folder, 0-9 custom; the backend DTO defaults to -1 when
   * omitted). Callers that don't target a folder leave it out — existing
   * call sites are unaffected (F-UX6 card quick action passes the
   * `defaultFavoriteSlot` preference).
   */
  async addFavorite(
    gid: number,
    token: string,
    category = 0,
    slot?: number,
  ): Promise<{ success: boolean }> {
    const body: { gid: number; token: string; category: number; slot?: number } = {
      gid,
      token,
      category,
    }
    if (slot !== undefined) body.slot = slot
    const { data } = await client.post('/favorite/add', body)
    return data
  },

  async removeFavorite(gid: number, token = '', category = 0): Promise<{ success: boolean }> {
    const { data } = await client.delete('/favorite/remove', { data: { gid, token, category } })
    return data
  },
}
