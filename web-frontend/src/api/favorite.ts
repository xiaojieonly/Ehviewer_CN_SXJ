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
  /**
   * 阅读进度（0 起页索引，来自同 gid 历史行）。旧服务器不下发该字段时为
   * undefined——卡片角标按 `readProgress > 0` 门控，缺省自然隐藏。
   */
  readProgress?: number
}

export interface FavoriteListResponse {
  favorites: FavoriteItem[]
  totalPages: number
  currentPage: number
}

export const favoriteApi = {
  async listFavorites(
    slot = 0,
    page = 1,
    q?: string | null,
    regex?: boolean,
  ): Promise<FavoriteListResponse> {
    const params: Record<string, string> = { slot: String(slot), page: String(page) }
    if (q !== undefined && q !== null && q !== '') params.q = q
    if (regex) params.regex = 'true'
    const { data } = await client.get('/favorite/list', { params })
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
