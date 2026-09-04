import { describe, it, expect, beforeEach } from 'vitest'
import { privacyMaskEnabled, setPrivacyMaskEnabled, maskedTitle } from '../privacyMask'

describe('privacyMask (隐私打码)', () => {
  beforeEach(() => {
    localStorage.clear()
    // 模块级 ref 会跨用例保留——统一复位。
    setPrivacyMaskEnabled(false)
  })

  it('defaults to off', () => {
    expect(privacyMaskEnabled.value).toBe(false)
  })

  it('persists the switch to localStorage (theme 惯例)', () => {
    setPrivacyMaskEnabled(true)
    expect(localStorage.getItem('anotherviewer-privacy-mask')).toBe('1')
    expect(privacyMaskEnabled.value).toBe(true)

    setPrivacyMaskEnabled(false)
    expect(localStorage.getItem('anotherviewer-privacy-mask')).toBeNull()
    expect(privacyMaskEnabled.value).toBe(false)
  })

  it('同步 <html> 的 privacy-mask 类——驱动全局遮蔽样式（图片照常请求，仅渲染替代）', () => {
    setPrivacyMaskEnabled(true)
    expect(document.documentElement.classList.contains('privacy-mask')).toBe(true)

    setPrivacyMaskEnabled(false)
    expect(document.documentElement.classList.contains('privacy-mask')).toBe(false)
  })

  it('maskedTitle: 开启时返回内容序列号 #gid，关闭时原样透传', () => {
    expect(maskedTitle('Some Title', 12345)).toBe('Some Title')

    setPrivacyMaskEnabled(true)
    expect(maskedTitle('Some Title', 12345)).toBe('#12345')
    expect(maskedTitle('', 7)).toBe('#7')
  })
})
