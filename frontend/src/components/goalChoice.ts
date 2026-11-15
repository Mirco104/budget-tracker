import type { SavingGoal } from '../api/types'

/** The goal of a choice without "ohne Sparziel" (month closing): the chosen one, else the first. */
export function goalOrFirst(value: string, goals: SavingGoal[]): string {
  return value !== '' ? value : goals[0] ? String(goals[0].id) : ''
}
