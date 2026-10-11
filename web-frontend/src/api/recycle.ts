import client from './client'

/**
 * 回收站实体（WP8 服务端契约）：favorites | history | downloads | bookmarks |
 * filters | quickSearches | downloadLabels。
 */
export type RecycleEntity =
  | 'favorites'
  | 'history'
  | 'downloads'
  | 'bookmarks'
  | 'filters'
  | 'quickSearches'
  | 'downloadLabels'

/** 回收站条目（GET /recycle/list 的 items 元素）。 */
export interface RecycleItem {
  /** 所属实体（随行下发，恢复时原样回传）。 */
  entity: RecycleEntity
  /**
   * 条目主键。服务端各实体主键类型不一（favorites=gid 数字、
   * quickSearches=id 字符串……），契约以 number | string 透传——展示层
   * 不解读该值，只原样回传给 restore/purge。
   */
  key: number | string
  title: string
  /** 删除时刻（epoch 毫秒或 ISO 字符串，服务端口径未定——两种都能格式化）。 */
  deletedAt: number | string
  /** 距永久清除的剩余天数。 */
  remainingDays: number
}

/** GET /recycle/list 响应信封。 */
export interface RecycleListResponse {
  items: RecycleItem[]
  total: number
  page: number
  pageSize: number
}

/** restore / purge 的定位单元（entity + key 原样回传）。 */
export interface RecycleTarget {
  entity: RecycleEntity
  key: number | string
}

export const recycleApi = {
  /** 分页拉取回收站条目（page 1 起；pageSize 钳制 1..200，默认 50）。 */
  async list(page = 1, pageSize = 50): Promise<RecycleListResponse> {
    const params: Record<string, string> = {
      page: String(page),
      pageSize: String(pageSize),
    }
    const { data } = await client.get('/recycle/list', { params })
    return data
  },

  /** 批量恢复（body `{items:[{entity,key}]}` → `{restored,failed}`）。 */
  async restore(items: RecycleTarget[]): Promise<{ restored: number; failed: number }> {
    const { data } = await client.post('/recycle/restore', { items })
    return data
  },

  /**
   * 批量永久清除。`items` 缺省 = 清空全部到期条目（body 为 `{}`）；
   * 传入时逐项定位（body `{items:[...]}`）→ `{purged}`。
   */
  async purge(items?: RecycleTarget[]): Promise<{ purged: number }> {
    const body: { items?: RecycleTarget[] } = {}
    if (items !== undefined) body.items = items
    const { data } = await client.post('/recycle/purge', body)
    return data
  },
}
