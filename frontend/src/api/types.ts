// Mirrors of the backend DTOs (Java records). Money and ratios arrive as JSON numbers.

export type CategoryGroup = 'FIXED' | 'FLEXIBLE' | 'SAVINGS' | 'REMAINDER'

export interface Category {
  id: number
  name: string
  /** null means "ohne Gruppe" (typically income). */
  group: CategoryGroup | null
  sortOrder: number
  targetAmount: number | null
  targetPercent: number | null
  /** Month ("YYYY-MM") the target in effect was set for; null without a target entry. */
  targetValidFrom: string | null
  /** The calculated remainder category; not bookable. */
  system: boolean
}

/** targetPercent is a fraction of income (0..1). null clears the value. */
export interface TargetInput {
  targetAmount: number | null
  targetPercent: number | null
}

/**
 * An entry of the transaction list: a single transaction (kind SINGLE, id) or a calculated occurrence of a
 * recurring transaction (kind RECURRING, recurringId, occurrenceDate). amount is signed: positive is income,
 * negative is expense.
 */
export interface Transaction {
  kind: 'SINGLE' | 'RECURRING'
  id: number | null
  recurringId: number | null
  occurrenceDate: string | null
  amount: number
  description: string
  date: string
  categoryId: number
  categoryName: string
}

export interface TransactionInput {
  amount: number
  description: string
  date: string
  categoryId: number
}

export type RecurringInterval = 'MONTHLY' | 'QUARTERLY' | 'HALF_YEARLY' | 'YEARLY'

/** One series of a recurring transaction. amount is signed per occurrence; endDate is inclusive. */
export interface RecurringTransaction {
  id: number
  seriesId: string
  amount: number
  description: string
  categoryId: number
  categoryName: string
  interval: RecurringInterval
  startDate: string
  endDate: string | null
  /** |amount| per month of the interval, calculated by the backend. */
  monthlyEquivalent: number
}

export interface RecurringTransactionInput {
  amount: number
  description: string
  categoryId: number
  interval: RecurringInterval
  startDate: string
  endDate: string | null
}

/** "Nur dieser Eintrag" (THIS) or "Dieser und alle folgenden" (FOLLOWING). */
export type OccurrenceScope = 'THIS' | 'FOLLOWING'

export interface Totals {
  target: number | null
  actual: number
  difference: number | null
}

/** Sums of the expense lines in one group; a missing target counts as 0. */
export interface GroupLine {
  group: CategoryGroup
  target: number
  actual: number
  difference: number
}

/** actual is the expense of the category (positive). group is null for an ungrouped category with expenses. */
export interface CategoryLine {
  id: number
  name: string
  group: CategoryGroup | null
  target: number | null
  actual: number
  difference: number | null
}

/** actual is the net of the category and may be negative for ungrouped categories. */
export interface IncomeLine {
  id: number
  name: string
  actual: number
  share: number
}

export interface DashboardData {
  month: string
  incomeTotal: number
  expenses: Totals
  surplus: number
  surplusLabel: string
  savingsRate: number | null
  groups: GroupLine[]
  categories: CategoryLine[]
  income: IncomeLine[]
}

export interface FieldError {
  field: string
  message: string
}

export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  errors?: FieldError[]
}
