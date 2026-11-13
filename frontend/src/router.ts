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
    (page) => ROUTES[page] === hash || hash.startsWith(`${ROUTES[page]}/`) || hash.startsWith(`${ROUTES[page]}?`),
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

/** The import of bank files below Transaktionen (US33). */
export const IMPORT_ROUTE = '#/transaktionen/import'

/** The section "Erwartet" of the transaction list (concept 4.2). */
export const EXPECTED_ROUTE = '#/transaktionen/erwartet'

/** "#/transaktionen?von=2026-11-01&bis=2026-11-30": the transaction list with a period, e.g. after an import. */
export function transactionsPeriodHref(from: string, to: string): string {
  return `${ROUTES.transactions}?von=${from}&bis=${to}`
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

/** The import page or the transaction list, with the period from the address bar when it has a valid one. */
export function useTransactionsRoute(): {
  hash: string
  importPage: boolean
  expected: boolean
  period: { from: string; to: string } | null
} {
  const hash = useHash()
  if (hash === IMPORT_ROUTE) {
    return { hash, importPage: true, expected: false, period: null }
  }
  if (hash === EXPECTED_ROUTE) {
    return { hash, importPage: false, expected: true, period: null }
  }
  const query = hash.split('?')[1]
  if (!query) {
    return { hash, importPage: false, expected: false, period: null }
  }
  const params = new URLSearchParams(query)
  const from = params.get('von') ?? ''
  const to = params.get('bis') ?? ''
  const period = ISO_DATE.test(from) && ISO_DATE.test(to) && from <= to ? { from, to } : null
  return { hash, importPage: false, expected: false, period }
}

/** The sub page of the Auswertung in the address bar; the look back unless the outlook is chosen. */
export function useEvaluationPage(): EvaluationPage {
  return useHash() === EVALUATION_ROUTES.outlook ? 'outlook' : 'history'
}
