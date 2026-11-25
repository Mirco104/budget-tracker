import { useSyncExternalStore } from 'react'

/**
 * Ticket #183 (decision 99): whether an import has a file read and not yet finished. While it has, the app keeps the
 * import page mounted (hidden on other pages), so switching pages keeps every choice. Only in memory: reloading the
 * page drops the import, nothing of the file lands in the browser's storage.
 */
let open = false
const listeners = new Set<() => void>()

export function setImportOpen(value: boolean) {
  if (open !== value) {
    open = value
    listeners.forEach((listener) => listener())
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function useImportOpen(): boolean {
  return useSyncExternalStore(subscribe, () => open)
}
