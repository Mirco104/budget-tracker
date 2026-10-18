import type { CategoryGroup } from './api/types'

export const GROUP_LABELS: Record<CategoryGroup, string> = {
  FIXED: 'Fixkosten',
  FLEXIBLE: 'Flexible Ausgaben',
  SAVINGS: 'Sparen & Investieren',
  REMAINDER: 'Rest',
}
