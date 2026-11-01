// Mirrors of the backend DTOs (Java records). Money and ratios arrive as JSON numbers.

export interface Category {
  id: number
  name: string
  sortOrder: number
  targetAmount: number | null
  targetPercent: number | null
  /** Month ("YYYY-MM") the target in effect was set for; null without a target entry. */
  targetValidFrom: string | null
  /** Archived categories are hidden in forms and planning (US10). */
  archived: boolean
}

export interface CategoryInput {
  name: string
}

/** A sortOrder that another category has swaps the two categories (US10). */
export interface CategoryUpdate {
  name: string
  sortOrder: number
  archived: boolean
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

/**
 * difference is the deviation from the target (target - actual).
 * recurring is the part of the expenses from recurring transactions, oneOff the rest; together they are actual.
 */
export interface Totals extends Shares {
  target: number
  actual: number
  difference: number
  recurring: number
  oneOff: number
  recurringShare: number | null
  oneOffShare: number | null
}

/** actual is the expense of the category (positive). */
export interface CategoryLine extends Shares {
  id: number
  name: string
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

/** A rare expense in the reserve pot. amount is the cost (positive); nextDate is its next occurrence. */
export interface ReserveLine {
  recurringId: number
  description: string
  categoryName: string
  amount: number
  interval: RecurringInterval
  nextDate: string
  /** To put aside each month. */
  monthly: number
  /** What should be saved by now; the full amount in the month it is due. */
  saved: number
}

/** A single expense (kind SINGLE, id) or an occurrence of a series (kind RECURRING, recurringId). amount is positive. */
export interface ForecastEntry {
  kind: 'SINGLE' | 'RECURRING'
  id: number | null
  recurringId: number | null
  description: string
  date: string
  amount: number
}

export interface ForecastMonth {
  month: string
  total: number
  entries: ForecastEntry[]
}

export interface CategoryAmount {
  name: string
  amount: number
  /** Share of the income of the month (R14); null when it is 0. */
  share: number | null
}

/**
 * A past month: income, expenses and surplus (income - expenses, may be negative), with the amounts per category.
 * The shares are parts of the income of the month, null when it is 0.
 */
export interface HistoryMonth {
  month: string
  income: number
  expenses: number
  surplus: number
  incomeShare: number | null
  expensesShare: number | null
  surplusShare: number | null
  incomeCategories: CategoryAmount[]
  expenseCategories: CategoryAmount[]
}

/** Analysis of a month (R14). */
export interface ReserveData {
  month: string
  /** Averages of the completed months counted; null when averageMonths is 0. */
  averageIncome: number | null
  averageExpenses: number | null
  averageSurplus: number | null
  /** Months in the average: the 12 before the month, fewer if the first entry is more recent. */
  averageMonths: number
  /** The months of the average, oldest first. */
  history: HistoryMonth[]
  /** Sums over the history; null when averageMonths is 0. */
  totalIncome: number | null
  totalExpenses: number | null
  totalSurplus: number | null
  /** Shares of the totals (and the averages) in totalIncome; null when it is 0 or missing. */
  incomeShare: number | null
  expensesShare: number | null
  surplusShare: number | null
  monthlyReserve: number
  reserveBalance: number
  reserves: ReserveLine[]
  /** The month and the following 11 months. */
  forecast: ForecastMonth[]
}

/** The signed-in user (US14). */
export interface Me {
  id: number
  email: string
  displayName: string
  role: 'USER' | 'ADMIN'
}

export interface LoginInput {
  email: string
  password: string
}
