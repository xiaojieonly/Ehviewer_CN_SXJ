import client from './client'

/**
 * `GET/POST /api/v1/site/availability` (plan-2026-08-30 §3.1) — server-side
 * EH reachability state machine. GET is public/probe-free; POST performs one
 * manual probe (auth-bearing like every /api call).
 */
export type SiteAvailabilityState = 'UP' | 'DOWN' | 'UNKNOWN'

export interface AvailabilityResponse {
  state: SiteAvailabilityState
  downAt?: number | null
  lastReason?: string | null
}

export const siteApi = {
  /** Read the current server-side EH availability state (no probe). */
  async getAvailability(): Promise<AvailabilityResponse> {
    const { data } = await client.get('/site/availability')
    return data
  },

  /** Perform one manual probe; the server returns the post-probe state. */
  async probeAvailability(): Promise<AvailabilityResponse> {
    const { data } = await client.post('/site/availability')
    return data
  },
}
