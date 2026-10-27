import type { RecurringInterval } from './api/types'

export const INTERVAL_LABELS: Record<RecurringInterval, string> = {
  MONTHLY: 'monatlich',
  QUARTERLY: 'vierteljährlich',
  HALF_YEARLY: 'halbjährlich',
  YEARLY: 'jährlich',
}
