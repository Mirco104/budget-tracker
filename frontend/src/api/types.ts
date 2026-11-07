// Mirrors of the backend DTOs (Java records). Money and ratios arrive as JSON numbers.

export interface Category {
  id: number
  name: string
  /** Icon key from the fixed list (US28); the frontend draws unknown keys as "tag". */
  icon: string
  /** The target in euro (R3); percent is only an input helper on the planning page (decision 36). */
  targetAmount: number | null
  /** Month ("YYYY-MM") the target in effect was set for; null without a target entry. */
  targetValidFrom: string | null
  /** Archived categories are hidden in forms and planning (US10). */
  archived: boolean
  /** The category has entries and all of them are positive (R24). */
  incomeOnly: boolean
  /** Fixed costs F(k, M) of the requested month (R25); null without a recurring expense. */
  fixedCosts: number | null
  fixedCostItems: FixedCostItem[]
}

/** One recurring expense behind the fixed costs: amount without sign, interval in months, monthly share (R25). */
export interface FixedCostItem {
  recurringId: number
  description: string
  amount: number
  interval: number
  monthly: number
}

/** Without an icon the category gets "tag" (US28). */
export interface CategoryInput {
  name: string
  icon?: string
}

/** Rename and archive (US10); categories have no own order (decision 38). Without an icon it stays (US28). */
export interface CategoryUpdate {
  name: string
  archived: boolean
  icon?: string
}

/** The target in euro; null means no target from the month on. */
export interface TargetInput {
  targetAmount: number | null
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

/**
 * difference is the deviation from the target (target - actual).
 * recurring is the part of the expenses from recurring transactions, oneOff the rest; together they are actual.
 */
export interface Totals {
  target: number
  actual: number
  difference: number
  recurring: number
  oneOff: number
}

/** actual is the expense of the category (positive). */
export interface CategoryLine {
  id: number
  name: string
  target: number | null
  actual: number
  difference: number | null
}

/** actual is the net of the category (positive). */
export interface IncomeLine {
  id: number
  name: string
  actual: number
}

export interface DashboardData {
  month: string
  /** The month compared with today (R7). */
  monthState: 'PAST' | 'CURRENT' | 'FUTURE'
  incomeTotal: number
  /**
   * Ø income and Ø expenses of the months before (R23) from averageIncomeFrom to averageIncomeTo;
   * null when no month counts.
   */
  averageIncome: number | null
  /** Minimum income Emin(M) of the same months (R23, decision 36). */
  minimumIncome: number | null
  averageExpenses: number | null
  averageIncomeMonths: number
  averageIncomeFrom: string | null
  averageIncomeTo: string | null
  expenses: Totals
  /** Ø income minus the target total (R4); negative when over-planned, null without Ø income. */
  unplanned: number | null
  /** "Nicht verplant" or "Überplant"; null without Ø income. */
  unplannedLabel: string | null
  /** The same with the minimum income as base (R4, decision 36). */
  unplannedMinimum: number | null
  unplannedMinimumLabel: string | null
  surplus: number
  /** "Monatlicher Überschuss"/"Monatlicher Verlust" for a past month, "Noch frei"/"Überschritten" otherwise (R7). */
  surplusLabel: string
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
  /** Monthly rates still to put aside until it is due; 0 in the month it is due (decision 37). */
  ratesLeft: number
}

/** A single expense (kind SINGLE, id) or an occurrence of a series (kind RECURRING, recurringId). amount is positive. */
export interface ForecastEntry {
  kind: 'SINGLE' | 'RECURRING'
  id: number | null
  recurringId: number | null
  description: string
  date: string
  amount: number
  /** An occurrence of a rare expense, paid from the reserve pot (decision 37). */
  fromReserves: boolean
}

export interface ForecastMonth {
  month: string
  total: number
  /** The part of total paid from the reserve pot (decision 37). */
  fromReserves: number
  entries: ForecastEntry[]
}

/** Income or expenses of a category in a past month, positive, with its icon (US28). */
export interface CategoryAmount {
  id: number
  name: string
  icon: string
  amount: number
}

/** A past month: income, expenses and surplus (income - expenses, may be negative), with the amounts per category. */
export interface HistoryMonth {
  month: string
  income: number
  expenses: number
  surplus: number
  incomeCategories: CategoryAmount[]
  expenseCategories: CategoryAmount[]
}

/** Look back from month to month (R14). from and to are the requested period. */
export interface HistoryData {
  from: string
  to: string
  /** Months counted, from the first entry on; history lists exactly these. */
  months: number
  /** null when no month counts. */
  averageIncome: number | null
  averageExpenses: number | null
  averageSurplus: number | null
  total: { income: number; expenses: number; surplus: number } | null
  history: HistoryMonth[]
}

/** Outlook from the current month on (R14, decision 29). */
export interface ReserveData {
  month: string
  monthlyReserve: number
  reserveBalance: number
  reserves: ReserveLine[]
  /** The month and the following 11 months. */
  forecast: ForecastMonth[]
}

/** SYSTEM_ADMIN is the admin created on the first start; nobody can demote it (US25). */
export type Role = 'USER' | 'ADMIN' | 'SYSTEM_ADMIN'

/** The signed-in user (US14). */
export interface Me {
  id: number
  email: string
  displayName: string
  role: Role
}

/** One account in the user overview (US25); instants as ISO strings, lastLoginAt null without a sign-in. */
export interface UserSummary {
  id: number
  email: string
  displayName: string
  role: Role
  createdAt: string
  lastLoginAt: string | null
}

/** US16 */
export interface ChangeEmailInput {
  email: string
  password: string
}

export interface LoginInput {
  email: string
  password: string
}

/** US15: the link the admin passes on; expiresAt is an ISO instant. */
export interface Invitation {
  link: string
  expiresAt: string
}

export interface AcceptInvitationInput {
  email: string
  displayName: string
  password: string
}

/** US16 */
export interface ChangePasswordInput {
  currentPassword: string
  newPassword: string
}
