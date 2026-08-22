import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const NUL = '\u0000'

interface ParsedFrame {
  command: string
  headers: Record<string, string>
  body: string
}

let brokerAutoReply = true
let messageIdSeq = 0

function splitFrames(buffer: string): ParsedFrame[] {
  const frames: ParsedFrame[] = []
  for (const chunk of buffer.split(NUL)) {
    const text = chunk.replace(/^\n+/, '')
    if (!text.trim()) continue
    const commandEnd = text.indexOf('\n')
    const command = text.slice(0, commandEnd)
    const [headBlock, ...bodyParts] = text.slice(commandEnd + 1).split('\n\n')
    const headers = Object.fromEntries(
      headBlock
        .split('\n')
        .filter(Boolean)
        .map((line) => {
          const sep = line.indexOf(':')
          return [line.slice(0, sep), line.slice(sep + 1)]
        }),
    )
    frames.push({ command, headers, body: bodyParts.join('\n\n') })
  }
  return frames
}

function parseSingle(raw: string): ParsedFrame {
  const [frame] = splitFrames(raw)
  expect(frame).toBeDefined()
  return frame as ParsedFrame
}

function serveBroker(sock: FakeSockJS, raw: string): void {
  if (!brokerAutoReply) return
  for (const frame of splitFrames(raw)) {
    if (frame.command === 'CONNECT') {
      sock.serverSend(`CONNECTED\nversion:1.2\nheart-beat:0,0\n\n${NUL}`)
    } else if (frame.command === 'DISCONNECT' && frame.headers.receipt) {
      sock.serverSend(`RECEIPT\nreceipt-id:${frame.headers.receipt}\n\n${NUL}`)
    }
  }
}

class FakeSockJS {
  static created: FakeSockJS[] = []
  readonly url: string
  readonly createdAt: number
  readyState = 0
  binaryType = ''
  sent: string[] = []
  onopen: ((ev?: unknown) => void) | null = null
  onmessage: ((ev: { data: unknown }) => void) | null = null
  onerror: ((ev?: unknown) => void) | null = null
  onclose: ((ev?: unknown) => void) | null = null

  constructor(url: string) {
    this.url = url
    this.createdAt = Date.now()
    FakeSockJS.created.push(this)
  }

  open(): void {
    if (this.readyState !== 0) return
    this.readyState = 1
    this.onopen?.({})
  }

  serverSend(chunk: string): void {
    if (this.readyState !== 1) return
    this.onmessage?.({ data: chunk })
  }

  send(data: string | ArrayBuffer): void {
    const raw = String(data)
    this.sent.push(raw)
    serveBroker(this, raw)
  }

  close(): void {
    if (this.readyState === 3) return
    this.readyState = 3
    this.onclose?.({ code: 1000, reason: '', wasClean: true })
  }

  refuse(): void {
    if (this.readyState === 3) return
    this.readyState = 3
    this.onerror?.({})
    this.onclose?.({ code: 1006, reason: 'connection refused', wasClean: false })
  }
}

function sentOf(sock: FakeSockJS, command: string): ParsedFrame[] {
  return sock.sent.map(parseSingle).filter((f) => f.command === command)
}

function subIdFor(sock: FakeSockJS, destination: string): string {
  const id = subIdsFor(sock, destination)[0]
  expect(id, `no SUBSCRIBE for ${destination}`).toBeDefined()
  return id as string
}

function subIdsFor(sock: FakeSockJS, destination: string): string[] {
  return sentOf(sock, 'SUBSCRIBE')
    .filter((f) => f.headers.destination === destination)
    .map((f) => f.headers.id)
}

function messageFrame(destination: string, subscriptionId: string, body: unknown): string {
  const payload = typeof body === 'string' ? body : JSON.stringify(body)
  messageIdSeq += 1
  return (
    `MESSAGE\nsubscription:${subscriptionId}\ndestination:${destination}` +
    `\ncontent-type:application/json\nmessage-id:m-${messageIdSeq}\n\n${payload}${NUL}`
  )
}

function deliver(sock: FakeSockJS, destination: string, body: unknown): void {
  sock.serverSend(messageFrame(destination, subIdFor(sock, destination), body))
}

function deliverToNth(
  sock: FakeSockJS,
  destination: string,
  n: number,
  body: unknown,
): void {
  const ids = subIdsFor(sock, destination)
  const id = ids[n]
  expect(id, `no SUBSCRIBE #${n} for ${destination}`).toBeDefined()
  sock.serverSend(messageFrame(destination, id as string, body))
}

function lastSocket(): FakeSockJS {
  const sock = FakeSockJS.created[FakeSockJS.created.length - 1]
  expect(sock).toBeDefined()
  return sock
}

type WsModule = typeof import('../useWebSocket')

let mockSubSeq = 0

interface MockClientConfig {
  reconnectDelay?: number
  maxReconnectDelay?: number
  reconnectTimeMode?: string
  heartbeatIncoming?: number
  heartbeatOutgoing?: number
  webSocketFactory?: () => FakeSockJS
  connectHeaders?: Record<string, string>
  onConnect?: () => void
  [key: string]: unknown
}

class MockClient {
  static instances: MockClient[] = []
  opts: MockClientConfig
  active = false
  connected = false
  connectHeaders: Record<string, string> = {}
  activate = vi.fn(function (this: MockClient) {
    this.active = true
  })
  deactivate = vi.fn(async function (this: MockClient) {
    this.active = false
    this.connected = false
  })
  publish = vi.fn()
  subscribe = vi.fn((_destination: string, _cb: (m: unknown) => void) => {
    mockSubSeq += 1
    return { id: `sub-${mockSubSeq}`, unsubscribe: vi.fn() }
  })

  constructor(opts: Record<string, unknown>) {
    this.opts = opts
    Object.assign(this, opts)
    MockClient.instances.push(this)
  }
}

async function loadWs(mockStompClient: boolean): Promise<WsModule> {
  vi.doMock('sockjs-client/dist/sockjs', () => ({ default: FakeSockJS }))
  if (mockStompClient) {
    MockClient.instances = []
    mockSubSeq = 0
    vi.doMock('@stomp/stompjs', () => ({
      Client: MockClient,
      ReconnectionTimeMode: { LINEAR: 'LINEAR', EXPONENTIAL: 'EXPONENTIAL' },
    }))
  } else {
    vi.doUnmock('@stomp/stompjs')
  }
  vi.resetModules()
  activeWs = await import('../useWebSocket')
  return activeWs
}

let activeWs: WsModule | undefined

const flush = (): Promise<unknown> => vi.advanceTimersByTimeAsync(0)

function baseline(): number {
  return FakeSockJS.created.length
}

function nthSocket(base: number, n: number): FakeSockJS {
  expect(FakeSockJS.created.length).toBe(base + n)
  return FakeSockJS.created[base + n - 1]
}

async function startConnected(ws: WsModule): Promise<FakeSockJS> {
  const base = baseline()
  ws.connect()
  await flush()
  const sock = nthSocket(base, 1)
  sock.open()
  expect(ws.connectionState.value).toBe('connected')
  return sock
}

function setVisibility(state: 'visible' | 'hidden'): void {
  Object.defineProperty(document, 'visibilityState', {
    configurable: true,
    get: () => state,
  })
}

const fireVisibilityChange = (): boolean =>
  document.dispatchEvent(new Event('visibilitychange'))

beforeEach(() => {
  vi.useFakeTimers()
  localStorage.clear()
  localStorage.setItem('token', 'tok-abc')
  setVisibility('visible')
  FakeSockJS.created = []
  brokerAutoReply = true
  messageIdSeq = 0
})

afterEach(async () => {
  activeWs?.disconnect()
  activeWs?.disconnect()
  activeWs = undefined
  await flush()
  setVisibility('visible')
  localStorage.clear()
  vi.useRealTimers()
})

describe('useWebSocket — 真实 @stomp/stompjs + 假 SockJS 传输', () => {
  it('首连握手：状态机 disconnected→connecting→connected，CONNECT 帧携带令牌与心跳配置', async () => {
    const ws = await loadWs(false)
    expect(ws.connectionState.value).toBe('disconnected')

    const base = baseline()
    ws.connect()
    expect(ws.connectionState.value).toBe('connecting')
    await flush()

    const sock = nthSocket(base, 1)
    sock.open()
    expect(ws.connectionState.value).toBe('connected')
    const { connected } = ws.useWebSocket()
    expect(connected.value).toBe(true)

    const connect = sentOf(sock, 'CONNECT')[0]
    expect(connect.headers['accept-version']).toContain('1.2')
    expect(connect.headers['heart-beat']).toBe('10000,10000')
    expect(connect.headers.login).toBe('tok-abc')
    expect(connect.headers.Authorization).toBe('Bearer tok-abc')
    expect(connect.headers.passcode).toBe('')

    expect(ws.authError.value).toBeNull()
    expect(ws.lastError.value).toBeNull()
  })

  it('引用计数：并发 connect 共享单例 socket，最后一个 disconnect 才关闭且计数不穿透零', async () => {
    const ws = await loadWs(false)
    const base = baseline()

    ws.connect()
    await flush()
    const sock = nthSocket(base, 1)
    sock.open()

    ws.connect()
    await flush()
    expect(FakeSockJS.created.length).toBe(base + 1)
    expect(ws.connectionState.value).toBe('connected')

    ws.disconnect()
    expect(ws.connectionState.value).toBe('connected')
    expect(sock.readyState).not.toBe(3)

    ws.disconnect()
    expect(ws.connectionState.value).toBe('disconnected')
    expect(sock.readyState).toBe(3)
    expect(sentOf(sock, 'DISCONNECT')).toHaveLength(1)

    expect(() => ws.disconnect()).not.toThrow()
    expect(FakeSockJS.created.length).toBe(base + 1)
  })

  it('指数退避重连间隔序列：成功会话后连续掉线按 1s→2s→4s→8s→16s→30s(封顶) 重试，不触发放弃', async () => {
    const ws = await loadWs(false)
    const base = baseline()

    const first = await startConnected(ws)
    const stamps = [first.createdAt]

    first.refuse()
    for (const gap of [1000, 2000, 4000, 8000, 16000, 30000, 30000]) {
      await vi.advanceTimersByTimeAsync(gap)
      const retry = nthSocket(base, stamps.length + 1)
      retry.refuse()
      stamps.push(retry.createdAt)
      expect(ws.connectionState.value).toBe('connecting')
    }

    const actualGaps = stamps.map((t, i) => (i === 0 ? t - stamps[0] : t - stamps[i - 1]))
    expect(actualGaps).toEqual([0, 1000, 2000, 4000, 8000, 16000, 30000, 30000])
    expect(ws.authError.value).toBeNull()
    expect(ws.connectionState.value).not.toBe('error')
  })

  it('冷启动连续 3 次连接失败后放弃并表面化 authError，重连循环终止', async () => {
    const ws = await loadWs(false)
    const base = baseline()

    ws.connect()
    await flush()
    nthSocket(base, 1).refuse()
    expect(ws.authError.value).toBeNull()

    await vi.advanceTimersByTimeAsync(1000)
    nthSocket(base, 2).refuse()
    expect(ws.authError.value).toBeNull()

    await vi.advanceTimersByTimeAsync(2000)
    nthSocket(base, 3).refuse()

    expect(ws.connectionState.value).toBe('error')
    expect(ws.authError.value).toContain('Connection failed 3 times in a row')
    expect(ws.authError.value).toContain('check your API token')
    expect(ws.lastError.value).toBe(ws.authError.value)

    await vi.advanceTimersByTimeAsync(120000)
    expect(FakeSockJS.created.length).toBe(base + 3)
  })

  it('失败计数器被成功连接重置：先败两次→连上→此后掉线永不累计放弃', async () => {
    const ws = await loadWs(false)
    const base = baseline()

    ws.connect()
    await flush()
    nthSocket(base, 1).refuse()
    await vi.advanceTimersByTimeAsync(1000)
    nthSocket(base, 2).refuse()

    await vi.advanceTimersByTimeAsync(2000)
    const third = nthSocket(base, 3)
    third.open()
    expect(ws.connectionState.value).toBe('connected')
    expect(ws.authError.value).toBeNull()

    third.refuse()
    await vi.advanceTimersByTimeAsync(1000)
    nthSocket(base, 4).refuse()
    await vi.advanceTimersByTimeAsync(2000)
    nthSocket(base, 5).refuse()
    await vi.advanceTimersByTimeAsync(4000)
    nthSocket(base, 6).refuse()

    expect(ws.authError.value).toBeNull()
    expect(ws.connectionState.value).toBe('connecting')
    expect(FakeSockJS.created.length).toBe(base + 6)
  })

  it('registry 断线自动重订阅：同回调跨会话续收帧；退订后不再恢复', async () => {
    const ws = await loadWs(false)
    const destination = '/topic/download/progress'
    const seen: Array<{ gid: number; state: number }> = []

    const sock1 = await startConnected(ws)
    const unsubscribe = ws.subscribe<{ payload: { gid: number; state: number } }>(
      destination,
      (envelope) => seen.push(envelope.payload),
    )
    expect(subIdFor(sock1, destination)).toBe('sub-0')

    deliver(sock1, destination, {
      type: 'download.progress',
      timestamp: 1,
      version: '1.1.0',
      payload: { gid: 7, state: 3 },
    })
    expect(seen).toEqual([{ gid: 7, state: 3 }])
    expect(ws.connectionState.value).toBe('connected')

    sock1.refuse()
    expect(ws.connectionState.value).toBe('connecting')

    await vi.advanceTimersByTimeAsync(1000)
    const sock2 = lastSocket()
    sock2.open()
    expect(ws.connectionState.value).toBe('connected')
    expect(subIdFor(sock2, destination)).toBe('sub-0')

    deliver(sock2, destination, {
      type: 'download.progress',
      timestamp: 2,
      version: '1.1.0',
      payload: { gid: 7, state: 4 },
    })
    expect(seen).toEqual([
      { gid: 7, state: 3 },
      { gid: 7, state: 4 },
    ])

    unsubscribe()
    expect(sentOf(sock2, 'UNSUBSCRIBE')).toHaveLength(1)

    sock2.refuse()
    await vi.advanceTimersByTimeAsync(1000)
    const sock3 = lastSocket()
    sock3.open()
    expect(sentOf(sock3, 'SUBSCRIBE')).toHaveLength(0)
    expect(ws.connectionState.value).toBe('connected')
  })

  it('tab 隐藏暂停：立即断开且隐藏期零重连；恢复可见自动重连并重建注册表订阅', async () => {
    const ws = await loadWs(false)
    const destination = '/topic/jobs/all'
    const events: string[] = []
    ws.subscribe<{ type: string }>(destination, (e) => events.push(e.type))

    const sock1 = await startConnected(ws)
    subIdFor(sock1, destination)

    setVisibility('hidden')
    fireVisibilityChange()
    expect(ws.connectionState.value).toBe('disconnected')
    expect(sock1.readyState).toBe(3)
    expect(sentOf(sock1, 'DISCONNECT')).toHaveLength(1)

    await vi.advanceTimersByTimeAsync(60000)
    expect(FakeSockJS.created.length).toBe(baseline())

    setVisibility('visible')
    fireVisibilityChange()
    await flush()
    const sock2 = lastSocket()
    sock2.open()
    expect(ws.connectionState.value).toBe('connected')
    expect(subIdFor(sock2, destination)).toBe('sub-0')

    deliver(sock2, destination, {
      type: 'job.completed',
      timestamp: 3,
      version: '1.1.0',
      payload: { jobId: 'j-9', type: 'backup' },
    })
    expect(events).toEqual(['job.completed'])
  })

  it('信封 version 字段原样透传（当前与未来版本），类型/jobId 过滤与版本无关，malformed JSON 丢弃并告警', async () => {
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
    try {
      const ws = await loadWs(false)
      const topic = '/topic/jobs/all'
      const generic: unknown[] = []
      const jobEvents: unknown[] = []
      const plainTopic: unknown[] = []

      const sock = await startConnected(ws)
      ws.subscribe(topic, (e) => generic.push(e))
      ws.subscribeJob('j-1', (e) => jobEvents.push(e))
      ws.subscribe('/topic/plain', (e) => generic.push(e))
      void plainTopic

      const jobProgressEnvelope = {
        type: 'job.progress',
        timestamp: 1724400000000,
        version: '1.1.0',
        payload: { jobId: 'j-1', type: 'backup', percent: 42 },
      }
      deliverToNth(sock, topic, 0, jobProgressEnvelope)
      deliverToNth(sock, topic, 1, jobProgressEnvelope)
      expect(jobEvents).toHaveLength(1)
      const delivered = jobEvents[0] as { version: string; payload: { percent: number } }
      expect(delivered.version).toBe('1.1.0')
      expect(delivered.payload).toEqual({ jobId: 'j-1', type: 'backup', percent: 42 })
      expect(generic.some((e) => (e as { version: string }).version === '1.1.0')).toBe(true)

      deliverToNth(sock, topic, 0, {
        type: 'download.progress',
        timestamp: 1724400000001,
        version: '999.0.0-future+exp',
        payload: { gid: 2, state: 1 },
      })
      deliverToNth(sock, topic, 1, {
        type: 'download.progress',
        timestamp: 1724400000001,
        version: '999.0.0-future+exp',
        payload: { gid: 2, state: 1 },
      })
      const future = generic.find(
        (e) => (e as { version: string }).version === '999.0.0-future+exp',
      ) as { type: string; payload: { gid: number } } | undefined
      expect(future).toBeDefined()
      expect(future?.type).toBe('download.progress')
      expect(future?.payload).toEqual({ gid: 2, state: 1 })
      expect(jobEvents).toHaveLength(1)

      deliverToNth(sock, topic, 1, {
        type: 'job.completed',
        timestamp: 1724400000002,
        version: '1.1.0',
        payload: { jobId: 'j-other', type: 'backup' },
      })
      expect(jobEvents).toHaveLength(1)

      deliver(sock, '/topic/plain', '{broken json')
      expect(warnSpy).toHaveBeenCalledWith(
        expect.stringContaining('[ws] discarding malformed message on /topic/plain'),
        expect.anything(),
      )
      expect(
        generic.filter((e) => typeof e !== 'object' || e === null),
      ).toHaveLength(0)
    } finally {
      warnSpy.mockRestore()
    }
  })
})

describe('useWebSocket — mock Client：构造配置接线与过期会话帧守卫', () => {
  it('Client 配置接线：指数退避参数、心跳、SockJS 端点 /ws、CONNECT 头随 token 轮换刷新', async () => {
    const ws = await loadWs(true)
    ws.connect()

    expect(MockClient.instances).toHaveLength(1)
    const client = MockClient.instances[0]
    expect(client.opts.reconnectDelay).toBe(1000)
    expect(client.opts.maxReconnectDelay).toBe(30000)
    expect(client.opts.reconnectTimeMode).toBe('EXPONENTIAL')
    expect(client.opts.heartbeatIncoming).toBe(10000)
    expect(client.opts.heartbeatOutgoing).toBe(10000)

    const factory = client.opts.webSocketFactory
    expect(typeof factory).toBe('function')
    const socket = (factory as () => FakeSockJS)()
    expect(socket).toBeInstanceOf(FakeSockJS)
    expect(socket.url).toBe('/ws')
    expect(client.connectHeaders).toEqual({
      passcode: '',
      login: 'tok-abc',
      Authorization: 'Bearer tok-abc',
    })

    localStorage.setItem('token', 'tok-rotated')
    ws.disconnect()
    ws.connect()
    expect(MockClient.instances).toHaveLength(1)
    expect(MockClient.instances[0]).toBe(client)
    expect(client.connectHeaders.login).toBe('tok-rotated')
    expect(client.connectHeaders.Authorization).toBe('Bearer tok-rotated')
    expect(client.activate).toHaveBeenCalledTimes(2)
  })

  it('无 token 时 CONNECT 头仅含空 passcode', async () => {
    localStorage.removeItem('token')
    const ws = await loadWs(true)
    ws.connect()
    expect(MockClient.instances[0].connectHeaders).toEqual({ passcode: '' })
  })

  it('过期会话帧守卫：client 非 active 时 CONNECTED 被丢弃——不置 connected、不重放订阅；恢复 active 后正常接入', async () => {
    const ws = await loadWs(true)
    const received: unknown[] = []
    ws.subscribe('/topic/stale-check', (m) => received.push(m))

    ws.connect()
    const client = MockClient.instances[0]
    expect(client.active).toBe(true)
    expect(ws.connectionState.value).toBe('connecting')

    client.active = false
    client.opts.onConnect?.()

    const api = ws.useWebSocket()
    expect(ws.connectionState.value).toBe('connecting')
    expect(api.connected.value).toBe(false)
    expect(ws.authError.value).toBeNull()
    expect(ws.lastError.value).toBeNull()
    expect(client.subscribe).not.toHaveBeenCalled()

    client.active = true
    client.opts.onConnect?.()

    expect(ws.connectionState.value).toBe('connected')
    expect(api.connected.value).toBe(true)
    expect(client.subscribe).toHaveBeenCalledTimes(1)
    expect(client.subscribe).toHaveBeenCalledWith('/topic/stale-check', expect.any(Function))

    const handler = client.subscribe.mock.calls[0]?.[1] as (m: unknown) => void
    handler({ body: '{"value":1}' })
    expect(received).toEqual([{ value: 1 }])
  })
})
