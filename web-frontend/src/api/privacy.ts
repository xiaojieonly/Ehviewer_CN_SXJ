import client from './client'

/**
 * 内容打码模式的服务端状态（管理面板-高级页读写）。这是唯一开关：
 * 开启时后端 PrivacyMaskFilter 对内容类 JSON 响应统一脱敏（Agent 等
 * 无头客户端同样生效），前端展示层（CSS 遮蔽/序列号）随此状态走。
 */
export interface PrivacyMaskState {
  enabled: boolean
}

export const privacyApi = {
  async getMask(): Promise<PrivacyMaskState> {
    const { data } = await client.get('/privacy/mask')
    return data
  },

  async setMask(enabled: boolean): Promise<PrivacyMaskState> {
    const { data } = await client.post('/privacy/mask', { enabled })
    return data
  },
}
