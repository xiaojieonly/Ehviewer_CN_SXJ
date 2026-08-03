/**
 * Same-origin rewrite for Gallery Site asset URLs (R4-9).
 *
 * Gallery metadata carries site-hosted asset URLs (thumbnails, covers), e.g.
 * `https://gallery.test/t/123/cover.jpg`. The browser cannot fetch those
 * directly — the site host is not publicly resolvable in the LAN deployment
 * and cross-origin fetches run into CSP / cookie walls. Routing them through
 * the WebUI server's image proxy keeps everything same-origin:
 *
 *   https://gallery.test/t/123/cover.jpg
 *     → /api/v1/image/proxy?url=https%3A%2F%2Fgallery.test%2Ft%2F123%2Fcover.jpg
 *
 * The host family mirrors the app-side interceptors (`SiteUrl.DOMAIN_E` +
 * subdomains): `gallery.test`, `t.gallery.test`, `upld.gallery.test`, …
 */

/** The Gallery Site domain (SiteUrl.DOMAIN_E/DOMAIN_EX in the app). */
export const SITE_ASSET_DOMAIN = 'gallery.test'

/** Matches the site domain itself and any subdomain of it. */
export function isSiteAssetUrl(url: string): boolean {
  try {
    const host = new URL(url).host
    return host === SITE_ASSET_DOMAIN || host.endsWith('.' + SITE_ASSET_DOMAIN)
  } catch {
    return false
  }
}

/**
 * Rewrites a Gallery Site asset URL to the server's image proxy
 * (`GET /api/v1/image/proxy?url=…`, original URL `encodeURIComponent`-ed).
 * Non-site URLs are returned untouched; empty input stays empty so the
 * callers' placeholder logic (`hasThumb`, `v-if="gallery.thumb"`) keeps
 * working unchanged. Unparseable values pass through verbatim.
 */
export function rewriteSiteAssetUrl(url: string | null | undefined): string {
  if (!url) {
    return ''
  }
  if (!isSiteAssetUrl(url)) {
    return url
  }
  return `/api/v1/image/proxy?url=${encodeURIComponent(url)}`
}
