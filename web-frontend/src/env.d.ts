/// <reference types="vite/client" />
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
declare module 'sockjs-client/dist/sockjs' {
  const SockJS: any
  export default SockJS
}
