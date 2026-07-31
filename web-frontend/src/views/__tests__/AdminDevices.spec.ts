import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import AdminDevices from '../admin/AdminDevices.vue'
import { devicesApi } from '@/api/devices'

vi.mock('@/api/devices', () => ({
  devicesApi: {
    generatePairCode: vi.fn(),
    list: vi.fn(),
    revoke: vi.fn(),
  },
}))

const PAIR_CODE = 'AB2CD4'

describe('AdminDevices (配对模块)', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.mocked(devicesApi.list).mockResolvedValue([
      {
        deviceId: 'android-1234',
        deviceName: 'Pixel 8 Pro',
        platform: 'android',
        pairedAt: Date.now() - 86400000,
        lastSeen: Date.now() - 3600000,
      },
    ])
    vi.mocked(devicesApi.revoke).mockResolvedValue(undefined)
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  afterEach(() => {
    wrapper?.unmount()
    vi.clearAllMocks()
    vi.restoreAllMocks()
  })

  async function mountView() {
    wrapper = mount(AdminDevices)
    await flushPromises()
    return wrapper
  }

  it('renders the paired device list from the server', async () => {
    const w = await mountView()
    expect(w.text()).toContain('Pixel 8 Pro')
    expect(w.text()).toContain('Android')
  })

  it('generates a pairing code and shows it with expiry', async () => {
    vi.mocked(devicesApi.generatePairCode).mockResolvedValue({
      code: PAIR_CODE,
      expiresAt: Date.now() + 10 * 60 * 1000,
    })
    const w = await mountView()
    await w.find('.btn-primary').trigger('click')
    await flushPromises()
    expect(w.find('[data-testid="pair-code"]').text()).toBe(PAIR_CODE)
    expect(w.text()).toContain('有效期至')
  })

  it('copies the pairing code to the clipboard', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', {
      value: { writeText },
      configurable: true,
    })
    vi.mocked(devicesApi.generatePairCode).mockResolvedValue({
      code: PAIR_CODE,
      expiresAt: Date.now() + 10 * 60 * 1000,
    })
    const w = await mountView()
    await w.find('.btn-primary').trigger('click')
    await flushPromises()
    await w.find('[data-testid="copy-pair-code"]').trigger('click')
    expect(writeText).toHaveBeenCalledWith(PAIR_CODE)
  })

  it('revokes a device after confirmation', async () => {
    const w = await mountView()
    await w.find('.device-row__revoke').trigger('click')
    await flushPromises()
    expect(devicesApi.revoke).toHaveBeenCalledWith('android-1234')
    expect(w.text()).not.toContain('Pixel 8 Pro')
  })
})
