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

/** Sub pages of the Auswertung (decision 29); "#/auswertung" alone shows the look back. */
export const EVALUATION_ROUTES = {
  history: '#/auswertung/rueckblick',
  outlook: '#/auswertung/vorausschau',
} as const

export type EvaluationPage = keyof typeof EVALUATION_ROUTES

// A page also owns the addresses below it, e.g. "#/auswertung/rueckblick"
function pageOf(hash: string): Page {
  const match = (Object.keys(ROUTES) as Page[]).find(
    (page) => ROUTES[page] === hash || hash.startsWith(`${ROUTES[page]}/`),
  )
  return match ?? 'overview'
}

function subscribe(onChange: () => void) {
  window.addEventListener('hashchange', onChange)
  return () => window.removeEventListener('hashchange', onChange)
}

function useHash(): string {
  return useSyncExternalStore(subscribe, () => window.location.hash)
}

/** The page in the address bar; an empty or unknown address shows the overview. */
export function usePage(): Page {
  return pageOf(useHash())
}

/** Sub pages of the settings (US10); "#/einstellungen" alone shows the categories. */
export const SETTINGS_ROUTES = {
  categories: '#/einstellungen/kategorien',
  bankAccounts: '#/einstellungen/bankkonten',
  account: '#/einstellungen/konto',
  invitations: '#/einstellungen/einladungen',
  users: '#/einstellungen/nutzer',
} as const

export type SettingsPage = keyof typeof SETTINGS_ROUTES

/** The sub page of the settings in the address bar; the categories when none is chosen. */
export function useSettingsPage(): SettingsPage {
  const hash = useHash()
  const match = (Object.keys(SETTINGS_ROUTES) as SettingsPage[]).find((page) => SETTINGS_ROUTES[page] === hash)
  return match ?? 'categories'
}

/** The sub page of the Auswertung in the address bar; the look back unless the outlook is chosen. */
export function useEvaluationPage(): EvaluationPage {
  return useHash() === EVALUATION_ROUTES.outlook ? 'outlook' : 'history'
}
