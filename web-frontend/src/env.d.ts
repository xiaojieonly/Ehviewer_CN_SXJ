/// <reference types="vite/client" />

/** Injected by vite define from WEB_VERSION (see vite.config.ts). */
declare const __APP_VERSION__: string

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}
declare module 'sockjs-client/dist/sockjs' {
  const SockJS: any
  export default SockJS
}
