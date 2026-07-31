/**
 * Vitest global shim.
 *
 * Node 22+ ships an experimental global `localStorage` getter that logs a
 * warning and returns `undefined` unless `--localstorage-file` is passed.
 * Vitest's happy-dom environment copies `localStorage`/`sessionStorage` from
 * its DOM window only for keys that are not already present on the global
 * object, so on those Node versions tests see `undefined` instead of the
 * window storage. Install happy-dom's in-memory `Storage` so tests behave
 * like a browser (isolated per worker, cleared between tests by the specs).
 */
import { Storage } from 'happy-dom'

function installStorage(name: 'localStorage' | 'sessionStorage'): void {
  const existing = (globalThis as Record<string, unknown>)[name]
  if (existing instanceof Storage) return
  Object.defineProperty(globalThis, name, {
    value: new Storage(),
    configurable: true,
    writable: true,
  })
}

installStorage('localStorage')
installStorage('sessionStorage')
