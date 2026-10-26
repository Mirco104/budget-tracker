// Mirrors of the backend DTOs (Java records). Money and ratios arrive as JSON numbers.

export type CategoryGroup = 'FIXED' | 'FLEXIBLE' | 'SAVINGS'

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

/** Amounts with their shares of the income; a share is null without income or without amount. */
interface Shares {
  targetShare: number | null
  actualShare: number | null
  differenceShare: number | null
}

/** difference is the deviation from the target (target - actual). */
export interface Totals extends Shares {
  target: number
  actual: number
  difference: number
}

/**
 * Sums of the expense lines in one group; a missing target counts as 0.
 * Only groups with category lines are present; group null sums the ungrouped categories with expenses.
 */
export interface GroupLine extends Shares {
  group: CategoryGroup | null
  target: number
  actual: number
  difference: number
}

/** actual is the expense of the category (positive). group is null for an ungrouped category with expenses. */
export interface CategoryLine extends Shares {
  id: number
  name: string
  group: CategoryGroup | null
  target: number | null
  actual: number
  difference: number | null
}

/** actual is the net of the category (positive). share is null without income. */
export interface IncomeLine {
  id: number
  name: string
  actual: number
  share: number | null
}

export interface DashboardData {
  month: string
  incomeTotal: number
  incomeTotalShare: number | null
  expenses: Totals
  /** Income without a target: income minus the target total; negative when over-planned. */
  unplanned: number
  /** "Nicht verplant" or "Überplant" (negative unplanned). */
  unplannedLabel: string
  surplus: number
  surplusLabel: string
  surplusShare: number | null
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

/** An expense series active in the month. amount is signed (negative); monthlyEquivalent is |amount| per month. */
export interface ReserveSeries {
  id: number
  description: string
  categoryName: string
  amount: number
  interval: RecurringInterval
  monthlyEquivalent: number
}

/** amount is a cost, so positive. */
export interface ForecastOccurrence {
  recurringId: number
  description: string
  date: string
  amount: number
}

export interface ForecastMonth {
  month: string
  total: number
  occurrences: ForecastOccurrence[]
}

/** Reserve analysis of a month (R14): only recurring expenses count, single transactions do not. */
export interface ReserveData {
  month: string
  averageMonthlyCosts: number
  /** Part of averageMonthlyCosts from series with an interval longer than one month. */
  reserve: number
  series: ReserveSeries[]
  /** The month and the following 11 months. */
  forecast: ForecastMonth[]
}
