import type { Transaction } from './api/types'

/** Sum in whole cents, so adding many amounts shows no float noise (display only). Transfers count nowhere (R27). */
export function sumOf(entries: Transaction[]): number {
  return entries.reduce((sum, entry) => (entry.transfer ? sum : sum + Math.round(entry.amount * 100)), 0) / 100
}

/** Income and expenses of the given entries without transfers (R27), added in whole cents (display only). */
export function totals(entries: Transaction[]): { income: number; expenses: number } {
  let income = 0
  let expenses = 0
  for (const entry of entries.filter((e) => !e.transfer)) {
    const cents = Math.round(entry.amount * 100)
    if (cents > 0) {
      income += cents
    } else {
      expenses -= cents
    }
  }
  return { income: income / 100, expenses: expenses / 100 }
}
