import type { AccountRole } from './api/types'

/** The German names of the account roles (concept 3.4). */
export const ROLE_NAMES: Record<AccountRole, string> = { EVERYDAY: 'Alltag', SAVINGS: 'Sparen', DEPOT: 'Depot' }

/**
 * Field test after 0.3.0: what a role does, in one line (decisions 54 and 60, P12, R36). Only the line between everyday
 * and savings or depot matters for the calculations; savings and depot differ in their name only.
 */
export const ROLE_HINTS: Record<AccountRole, string> = {
  EVERYDAY:
    'Girokonto für laufende Zahlungen. Am Kontostand dieser Konten sieht die App in Stufe 2, was du variabel ausgegeben hast.',
  SAVINGS: 'Tagesgeld oder Sparkonto. Geld, das von einem Alltagskonto hierhin geht, zählt als gespart.',
  DEPOT: 'Wertpapierdepot. Geld, das von einem Alltagskonto hierhin geht, zählt als gespart.',
}
