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

/** amount is signed: positive is income, negative is expense. */
export interface Transaction {
  id: number
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

/** difference is the deviation from the target (target - actual). */
export interface Totals {
  target: number
  actual: number
  difference: number
}

/**
 * Sums of the expense lines in one group; a missing target counts as 0.
 * group null sums the ungrouped categories with expenses and is only present if there are any.
 */
export interface GroupLine {
  group: CategoryGroup | null
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
  /** Income without a target: income minus the target total; negative when over-planned. */
  unplanned: number
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
