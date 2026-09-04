import axios from 'axios'
import { markDown, EH_UNAVAILABLE_MESSAGE } from '@/stores/availability'
import { privacyMaskEnabled } from '@/utils/privacyMask'

const client = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 隐私打码（2026-09-04）：附带标记头，服务端对 JSON 响应做统一脱敏——
  // 风控读的是 API 输出，只遮前端渲染不够。App 不带此头、始终全量。
  if (privacyMaskEnabled.value) {
    config.headers['X-Privacy-Mask'] = '1'
  }
  return config
})

/** Thrown when the service worker reports offline with no cached data. */
export class OfflineError extends Error {
  constructor() {
    super('offline')
    this.name = 'OfflineError'
  }
}

/** Type guard for {@link OfflineError}; views use it to show a friendly offline tip. */
export function isOfflineError(error: unknown): error is OfflineError {
  return error instanceof OfflineError
}

/**
 * True when the service worker reported offline with nothing cached:
 * HTTP 503 and a payload carrying the `offline` marker (sw.js answers
 * `{error:'offline', message:'No cached data available'}` without a
 * JSON Content-Type, so the payload may arrive as object or string).
 */
export function isOfflinePayload(status: number | undefined, data: unknown): boolean {
  if (status !== 503) return false
  if (typeof data === 'object' && data !== null) {
    return (data as { error?: string }).error === 'offline'
  }
  return typeof data === 'string' && data.includes('offline')
}

/**
 * Error code of the server error envelope (`{error:{code,message,…}}`, plan
 * §4.1) — same extraction as the views' `errorCodeOf` helpers.
 */
export function errorCodeOf(error: unknown): string | null {
  const code = (error as { response?: { data?: { error?: { code?: unknown } } } } | undefined)
    ?.response?.data?.error?.code
  return typeof code === 'string' ? code : null
}

/**
 * The server's circuit-breaker answered "EH platform unreachable" (HTTP 404 +
 * `EH_UNAVAILABLE`, see plan-2026-08-30 §3.2). Views use this to stop auto
 * retries / switch to the local-only tip; the availability store is marked
 * DOWN by the response interceptor below.
 */
export class EhUnavailableError extends Error {
  constructor(message: string = EH_UNAVAILABLE_MESSAGE) {
    super(message)
    this.name = 'EhUnavailableError'
  }
}

/**
 * Type guard for {@link EhUnavailableError}. Also accepts the raw
 * axios-shaped error (envelope code check) so mocked/thrown list-envelope
 * errors (success:false + cause) are recognized by the same predicate.
 */
export function isEhUnavailableError(error: unknown): error is EhUnavailableError {
  return error instanceof EhUnavailableError || errorCodeOf(error) === 'EH_UNAVAILABLE'
}

client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      window.location.href = '/login'
    }
    // The service worker answers 503 + {error:'offline'} when offline and
    // nothing is cached (sw.js). Surface it as a typed error so views can
    // distinguish "offline, no cache" from a plain network/server failure.
    if (isOfflinePayload(error.response?.status, error.response?.data)) {
      return Promise.reject(new OfflineError())
    }
    // Circuit-breaker: the server short-circuited an EH upstream call
    // (404 EH_UNAVAILABLE). Mark DOWN immediately (views/banner react without
    // waiting for the next /site/availability load) and surface a typed error
    // so callers stop auto-retrying.
    if (errorCodeOf(error) === 'EH_UNAVAILABLE') {
      const message =
        typeof error.response?.data?.error?.message === 'string'
          ? error.response.data.error.message
          : EH_UNAVAILABLE_MESSAGE
      markDown(message)
      return Promise.reject(new EhUnavailableError(message))
    }
    return Promise.reject(error)
  }
)

export default client
