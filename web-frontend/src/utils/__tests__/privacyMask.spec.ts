import { describe, it, expect, beforeEach } from 'vitest'
import {
  privacyMaskEnabled,
  setPrivacyMaskEnabled,
  maskedTitle,
  maskedImageSrc,
  PRIVACY_PLACEHOLDER_SRC,
} from '../privacyMask'

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

  it('maskedTitle: 开启时返回内容序列号 #gid，关闭时原样透传', () => {
    expect(maskedTitle('Some Title', 12345)).toBe('Some Title')

    setPrivacyMaskEnabled(true)
    expect(maskedTitle('Some Title', 12345)).toBe('#12345')
    expect(maskedTitle('', 7)).toBe('#7')
  })

  it('maskedImageSrc: 开启时一律占位图，关闭时透传（null → 空）', () => {
    const real = '/api/v1/image/1/0?w=800'
    expect(maskedImageSrc(real)).toBe(real)
    expect(maskedImageSrc(null)).toBe('')

    setPrivacyMaskEnabled(true)
    expect(maskedImageSrc(real)).toBe(PRIVACY_PLACEHOLDER_SRC)
    expect(maskedImageSrc('https://e-hentai.org/t/a.jpg')).toBe(PRIVACY_PLACEHOLDER_SRC)
    expect(maskedImageSrc(null)).toBe(PRIVACY_PLACEHOLDER_SRC)
  })

  it('占位图是内联 SVG data URI（不发真实图片请求）', () => {
    expect(PRIVACY_PLACEHOLDER_SRC.startsWith('data:image/svg+xml,')).toBe(true)
  })
})
