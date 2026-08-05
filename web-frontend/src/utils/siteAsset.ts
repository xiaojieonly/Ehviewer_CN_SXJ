/**
 * Same-origin rewrite for Gallery Site asset URLs (R4-9).
 *
 * Gallery metadata carries site-hosted asset URLs (thumbnails, covers), e.g.
 * `https://e-hentai.org/t/123/cover.jpg`. The browser cannot fetch those
 * directly — cross-origin fetches run into CSP / cookie walls. Routing them
 * through the WebUI server's image proxy keeps everything same-origin:
 *
 *   https://e-hentai.org/t/123/cover.jpg
 *     → /api/v1/image/proxy?url=https%3A%2F%2Fe-hentai.org%2Ft%2F123%2Fcover.jpg
 *
 * The host family mirrors the backend SSRF predicate
 * (`SiteProxyController.isGallerySiteHost`): `e-hentai.org`, `exhentai.org`,
 * `lofi.e-hentai.org`, `ehgt.org` and their subdomains.
 */

/** The Gallery Site primary domain (SiteUrl.DOMAIN_E in the app). */
export const SITE_ASSET_DOMAIN = 'e-hentai.org'

const SITE_ASSET_HOSTS = [
  'e-hentai.org',
  'exhentai.org',
  'lofi.e-hentai.org',
  'ehgt.org',
]

/**
 * Matches the Gallery Site host family itself and any subdomain of each —
 * the same set as the backend SSRF predicate
 * (`SiteProxyController.isGallerySiteHost`).
 */
export function isSiteAssetUrl(url: string): boolean {
  try {
    const host = new URL(url).host
    return SITE_ASSET_HOSTS.some(
      (domain) => host === domain || host.endsWith('.' + domain),
    )
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
