import client from './client'
import type { ProxySettings } from './settings'

/**
 * Result of `POST /proxy/test` (contracts/openapi.yaml `ProxyTestResponse`).
 */
export interface ProxyTestResult {
  /** True when the proxy returned a successful (or sub-500) response. */
  success: boolean
  latencyMs: number
  /** Error message (empty string on success). */
  error: string
}

/**
 * Test connectivity to the Gallery Site through a proxy —
 * `POST /api/v1/proxy/test` (contracts/openapi.yaml `testProxy`). With an
 * empty object the currently saved proxy settings are used; with fields set
 * the given values are tested (merged over the saved settings), so the admin
 * UI can validate a form before saving it.
 */
export async function testProxy(settings: Partial<ProxySettings>): Promise<ProxyTestResult> {
  const { data } = await client.post('/proxy/test', settings)
  return data
}
