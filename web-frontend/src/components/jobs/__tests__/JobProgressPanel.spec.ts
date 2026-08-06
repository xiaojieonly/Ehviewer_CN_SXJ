import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import JobProgressPanel from '../JobProgressPanel.vue'
import type { Job } from '@/api/jobs'

function makeJob(overrides: Partial<Job> = {}): Job {
  return {
    jobId: 'job-1',
    type: 'EXPORT',
    state: 'RUNNING',
    stage: '压缩分片 1/4',
    percent: 25,
    processed: 1,
    total: 4,
    startedAt: 0,
    completedAt: null,
    error: null,
    result: null,
    ...overrides,
  }
}

describe('JobProgressPanel', () => {
  it('renders nothing when job is null', () => {
    const wrapper = mount(JobProgressPanel, { props: { job: null } })
    expect(wrapper.find('.job-panel').exists()).toBe(false)
  })

  it('defaults the title to 任务', () => {
    const wrapper = mount(JobProgressPanel, { props: { job: makeJob() } })
    expect(wrapper.text()).toContain('任务')
  })

  it('honors a custom title prop', () => {
    const wrapper = mount(JobProgressPanel, {
      props: { job: makeJob(), title: '清除缓存' },
    })
    expect(wrapper.text()).toContain('清除缓存')
  })

  it('exposes status semantics on the panel', () => {
    const wrapper = mount(JobProgressPanel, { props: { job: makeJob() } })
    expect(wrapper.find('.job-panel').attributes('role')).toBe('status')
  })

  it('renders determinate spinner + bar + stage + counts while PENDING', () => {
    const wrapper = mount(JobProgressPanel, {
      props: { job: makeJob({ state: 'PENDING', percent: 0, processed: 0, total: 100, stage: '解析备份文件' }) },
    })
    // Determinate spinner arc driven by percent / 100.
    const spinner = wrapper.findComponent({ name: 'ProgressSpinner' })
    expect(spinner.exists()).toBe(true)
    expect(spinner.props('indeterminate')).toBe(false)
    expect(spinner.props('progress')).toBe(0)

    // Progress bar with progressbar semantics.
    const bar = wrapper.find('.job-bar')
    expect(bar.attributes('role')).toBe('progressbar')
    expect(bar.attributes('aria-valuenow')).toBe('0')
    expect(bar.attributes('aria-valuemax')).toBe('100')

    expect(wrapper.text()).toContain('解析备份文件')
    expect(wrapper.text()).toContain('0 / 100')
  })

  it('renders stage, percent and processed/total while RUNNING', () => {
    const wrapper = mount(JobProgressPanel, { props: { job: makeJob() } })
    const spinner = wrapper.findComponent({ name: 'ProgressSpinner' })
    expect(spinner.props('progress')).toBe(0.25)

    const bar = wrapper.find('.job-bar')
    expect(bar.attributes('aria-valuenow')).toBe('25')
    expect((bar.find('.job-bar__fill').element as HTMLElement).style.width).toBe('25%')

    expect(wrapper.text()).toContain('压缩分片 1/4')
    expect(wrapper.text()).toContain('1 / 4')
    expect(wrapper.text()).toContain('25%')
  })

  it('clamps percent into [0, 100]', () => {
    const over = mount(JobProgressPanel, {
      props: { job: makeJob({ percent: 150 }) },
    })
    expect(over.find('.job-bar').attributes('aria-valuenow')).toBe('100')
    expect(over.text()).toContain('100%')

    const under = mount(JobProgressPanel, {
      props: { job: makeJob({ percent: -5 }) },
    })
    expect(under.find('.job-bar').attributes('aria-valuenow')).toBe('0')
  })

  it('renders the completed state with a check mark', () => {
    const wrapper = mount(JobProgressPanel, {
      props: { job: makeJob({ state: 'COMPLETED' }) },
    })
    expect(wrapper.find('.job-done').exists()).toBe(true)
    expect(wrapper.find('.job-done').text()).toContain('完成')
    // Terminal states drop the progress UI.
    expect(wrapper.find('.job-bar').exists()).toBe(false)
    expect(wrapper.findComponent({ name: 'AppIcon' }).exists()).toBe(true)
  })

  it('renders the failed state with the error message', () => {
    const wrapper = mount(JobProgressPanel, {
      props: { job: makeJob({ state: 'FAILED', error: '磁盘空间不足' }) },
    })
    const fail = wrapper.find('.job-fail')
    expect(fail.exists()).toBe(true)
    expect(fail.text()).toContain('失败')
    expect(fail.text()).toContain('磁盘空间不足')
  })

  it('falls back to a generic message when failed without an error', () => {
    const wrapper = mount(JobProgressPanel, {
      props: { job: makeJob({ state: 'FAILED', error: null }) },
    })
    expect(wrapper.find('.job-fail').text()).toContain('未知错误')
  })
})
