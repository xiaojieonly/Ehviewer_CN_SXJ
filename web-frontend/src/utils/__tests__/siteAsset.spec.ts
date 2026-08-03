import { describe, it, expect } from 'vitest'
import { rewriteSiteAssetUrl, isSiteAssetUrl, SITE_ASSET_DOMAIN } from '../siteAsset'

describe('rewriteSiteAssetUrl (R4-9 thumbnail/cover proxy rewrite)', () => {
  it('rewrites a site-host asset URL to the same-origin image proxy', () => {
    expect(rewriteSiteAssetUrl('https://gallery.test/t/123/cover.jpg')).toBe(
      '/api/v1/image/proxy?url=' + encodeURIComponent('https://gallery.test/t/123/cover.jpg'),
    )
  })

  it('rewrites subdomain hosts of the site family', () => {
    for (const url of [
      'https://t.gallery.test/t/9001/1.jpg',
      'http://upld.gallery.test/img/abc.png',
    ]) {
      expect(rewriteSiteAssetUrl(url)).toBe(
        '/api/v1/image/proxy?url=' + encodeURIComponent(url),
      )
    }
  })

  it('percent-encodes URLs with query strings and special characters', () => {
    const raw = 'https://gallery.test/t/5/x.jpg?token=a&b=c d'
    const rewritten = rewriteSiteAssetUrl(raw)
    expect(rewritten.startsWith('/api/v1/image/proxy?url=')).toBe(true)
    // The original URL must survive a decode round-trip intact.
    expect(decodeURIComponent(rewritten.slice('/api/v1/image/proxy?url='.length))).toBe(raw)
  })

  it('leaves non-site URLs untouched', () => {
    for (const url of [
      'https://example.com/thumb.jpg',
      'https://ehgt.org/t/1.jpg',
      'https://notgallery.test/t/1.jpg',
      'https://gallery.test.evil.example/t/1.jpg',
    ]) {
      expect(rewriteSiteAssetUrl(url)).toBe(url)
    }
  })

  it('returns empty string for empty input so placeholder logic keeps working', () => {
    expect(rewriteSiteAssetUrl('')).toBe('')
    expect(rewriteSiteAssetUrl(null)).toBe('')
    expect(rewriteSiteAssetUrl(undefined)).toBe('')
  })

  it('passes unparseable values through verbatim', () => {
    expect(rewriteSiteAssetUrl('not a url')).toBe('not a url')
    expect(rewriteSiteAssetUrl('/relative/path.jpg')).toBe('/relative/path.jpg')
  })
})

describe('isSiteAssetUrl', () => {
  it('matches the site domain and subdomains only', () => {
    expect(SITE_ASSET_DOMAIN).toBe('gallery.test')
    expect(isSiteAssetUrl('https://gallery.test/x')).toBe(true)
    expect(isSiteAssetUrl('https://lofi.gallery.test/x')).toBe(true)
    expect(isSiteAssetUrl('https://notgallery.test/x')).toBe(false)
    expect(isSiteAssetUrl('https://gallery.test.evil.example/x')).toBe(false)
    expect(isSiteAssetUrl('garbage')).toBe(false)
  })
})
