// Mirrors of the backend DTOs (Java records). Money and ratios arrive as JSON numbers.

export type CategoryType = 'EXPENSE' | 'INCOME'
export type CategoryGroup = 'FIXED' | 'FLEXIBLE' | 'SAVINGS' | 'REMAINDER'

export interface Category {
  id: number
  name: string
  type: CategoryType
  group: CategoryGroup | null
  sortOrder: number
  targetAmount: number | null
  targetPercent: number | null
  /** Month ("YYYY-MM") the target in effect was set for; null without a target entry. */
  targetValidFrom: string | null
  system: boolean
}

/** targetPercent is a fraction of income (0..1). null clears the value. */
export interface TargetInput {
  targetAmount: number | null
  targetPercent: number | null
}

export interface Transaction {
  id: number
  amount: number
  description: string
  date: string
  categoryId: number
  categoryName: string
  categoryType: CategoryType
}

export interface TransactionInput {
  amount: number
  description: string
  date: string
  categoryId: number
}

/** Counted as amountPerYear / 12 in every month from validFrom to validTo ("YYYY-MM", inclusive). */
export interface YearlyTransaction {
  id: number
  amountPerYear: number
  /** Share counted per month (amountPerYear / 12, rounded by the backend). */
  amountPerMonth: number
  description: string
  /** ISO date, optional info ("Abbuchung regulär am"). */
  dueDate: string | null
  validFrom: string
  /** null means open-ended. */
  validTo: string | null
  categoryId: number
  categoryName: string
  categoryType: CategoryType
}

export interface YearlyTransactionInput {
  amountPerYear: number
  description: string
  dueDate: string | null
  validFrom: string
  validTo: string | null
  categoryId: number
}

export interface Totals {
  target: number | null
  actual: number
  difference: number | null
}

export interface CategoryLine {
  id: number
  name: string
  group: CategoryGroup | null
  target: number | null
  actual: number
  /** Part of actual that comes from yearly transactions. */
  actualYearly: number
  difference: number | null
}

export interface IncomeLine {
  id: number
  name: string
  actual: number
  /** Part of actual that comes from yearly transactions. */
  actualYearly: number
  share: number
}

/** Yearly debit due in the month, with its full yearly amount. */
export interface DueLine {
  id: number
  description: string
  categoryName: string
  amountPerYear: number
  dueDate: string
}

export interface DashboardData {
  month: string
  incomeTotal: number
  expenses: Totals
  surplus: number
  surplusLabel: string
  changePercent: number | null
  changeLabel: string | null
  categories: CategoryLine[]
  income: IncomeLine[]
  /** Sum of the yearly shares of all expense categories. */
  yearlyExpensesShare: number
  dueYearly: DueLine[]
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
