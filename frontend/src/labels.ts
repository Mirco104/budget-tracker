import type { CategoryGroup } from './api/types'

export const GROUP_LABELS: Record<CategoryGroup, string> = {
  FIXED: 'Fixkosten',
  FLEXIBLE: 'Flexible Ausgaben',
  SAVINGS: 'Sparen & Investieren',
}

export const UNGROUPED_LABEL = 'Ohne Gruppe'

/** Recommended share of the income per group (R10), display only. */
export const GROUP_RECOMMENDATIONS: Record<CategoryGroup, string> = {
  FIXED: 'ca. 50 %',
  FLEXIBLE: 'ca. 30 %',
  SAVINGS: 'ca. 20 %',
}
