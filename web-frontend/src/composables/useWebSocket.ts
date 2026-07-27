import { ref, onUnmounted } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs'

export interface DownloadProgress {
  gid: number
  state: number
  downloaded: number
  total: number
  speed: number
  label: number
}

export function useWebSocket() {
  const client = ref<Client | null>(null)
  const connected = ref(false)

  function connect() {
    client.value = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      onConnect: () => {
        connected.value = true
      },
      onDisconnect: () => {
        connected.value = false
      },
    })
    client.value.activate()
  }

  function disconnect() {
    client.value?.deactivate()
    connected.value = false
  }

  function subscribeDownload(gid: number, callback: (progress: DownloadProgress) => void) {
    return client.value?.subscribe(`/topic/download/${gid}`, (message) => {
      const progress = JSON.parse(message.body) as DownloadProgress
      callback(progress)
    })
  }

  function subscribeAll(callback: (progress: DownloadProgress) => void) {
    return client.value?.subscribe('/topic/download/all', (message) => {
      const progress = JSON.parse(message.body) as DownloadProgress
      callback(progress)
    })
  }

  onUnmounted(() => {
    disconnect()
  })

  return { client, connected, connect, disconnect, subscribeDownload, subscribeAll }
}
