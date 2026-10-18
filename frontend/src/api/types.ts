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
  system: boolean
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
  difference: number | null
}

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
  changePercent: number | null
  changeLabel: string | null
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
