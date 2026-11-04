import { useSyncExternalStore } from 'react'

/** Pages with their address (US29); the hash keeps the app a single static page. */
export const ROUTES = {
  overview: '#/uebersicht',
  transactions: '#/transaktionen',
  planning: '#/planung',
  evaluation: '#/auswertung',
  settings: '#/einstellungen',
} as const

export type Page = keyof typeof ROUTES

function pageOf(hash: string): Page {
  const match = (Object.keys(ROUTES) as Page[]).find((page) => ROUTES[page] === hash)
  return match ?? 'overview'
}

function subscribe(onChange: () => void) {
  window.addEventListener('hashchange', onChange)
  return () => window.removeEventListener('hashchange', onChange)
}

/** The page in the address bar; an empty or unknown address shows the overview. */
export function usePage(): Page {
  const hash = useSyncExternalStore(subscribe, () => window.location.hash)
  return pageOf(hash)
}
