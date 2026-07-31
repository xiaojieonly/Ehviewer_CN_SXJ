import axios from 'axios'

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
    return Promise.reject(error)
  }
)

export default client
