// Mirrors of the backend DTOs (Java records). Money and ratios arrive as JSON numbers.

export interface Category {
  id: number
  name: string
  /** Icon key from the fixed list (US28); the frontend draws unknown keys as "tag". */
  icon: string
  /** Archived categories are hidden in forms and planning (US10). */
  archived: boolean
  /** The category has entries and all of them are positive (R24). */
  incomeOnly: boolean
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

/**
 * A booking (P1: only real bookings, planned items are not listed). amount is signed: positive is income, negative is
 * expense. A transfer between own accounts (R27) has no category and counts nowhere.
 */
export interface Transaction {
  id: number
  amount: number
  description: string
  date: string
  categoryId: number | null
  categoryName: string | null
  /** The bank account the entry is booked on (R26). */
  accountId: number
  accountName: string
  transfer: boolean
  /** Entered by the app from the plan (P6). */
  auto: boolean
  /** An expense that fulfils a term of a planned item (P5). */
  fix: boolean
  /** P8: paid explicitly from the reserve pot of the rare item with this key, or from a saving goal. */
  reserveItemKey?: string | null
  savingGoalId?: number | null
  /** P1: the kind Sparen. */
  saving?: boolean
  /** 27f3: the other side of a transfer pair. */
  transferPeerId?: number | null
  transferPeerAccountName?: string | null
}

/** A term of a planned item: the id of the item version and the planned date (P5). */
export interface TermRef {
  itemId: number
  date: string
}

export interface TransactionInput {
  amount: number
  description: string
  date: string
  /** Missing exactly for a transfer (R27). */
  categoryId?: number
  /** Without it the default account (R26). */
  accountId?: number
  transfer?: boolean
  /** Only when created: the term of a planned item the booking fulfils (P5, P7). */
  replaces?: TermRef
  /** P8: "aus Rücklage / Ersparnissen bezahlen": the pot of a rare item or a saving goal. */
  reserveItemKey?: string | null
  savingGoalId?: number | null
  /** P1: the kind Sparen, a transfer to a savings or depot account without category. */
  saving?: boolean
  /** 27f3: for a transfer the target account; both bookings are made. */
  targetAccountId?: number
}

/** A saving goal (concept 3.6): saved is what its saving bookings brought minus what was paid from it. */
export interface SavingGoal {
  id: number
  name: string
  targetAmount: number | null
  targetDate: string | null
  saved: number
  monthly: number
}

export interface SavingGoalInput {
  name: string
  targetAmount?: number | null
  targetDate?: string | null
}

/**
 * P8: the reserve pot of a rare item in a month. balance is what is in it before the payment, fromPot what it pays,
 * shortfall what is missing and counts into the budget.
 */
export interface ReservePot {
  itemId: number
  name: string
  categoryId: number
  amount: number
  monthly: number
  balance: number
  due: boolean
  nextDue: string | null
  paid: number
  fromPot: number
  shortfall: number
  /** Spacing of the item in months (P8). */
  months: number
}

/** Rhythm of a planned item (section 19.3): unit and count; no unit means "ohne festen Termin". */
export type RhythmUnit = 'WEEK' | 'MONTH'

/** How a term becomes a booking (decision 51). */
export type EntryMode = 'AUTO' | 'TICK' | 'MANUAL'

/** Group of a planned item (P4). */
export type PlanGroup = 'INCOME' | 'FIXED' | 'BUDGET' | 'SAVING'

/**
 * One version of a planned item. amount is the real amount per payment with its sign (saving negative);
 * monthlyShare its monthly share without sign (P2). validFrom/validTo are months ("YYYY-MM"); validTo null = open.
 */
export interface PlannedItem {
  id: number
  itemKey: string
  name: string
  amount: number
  saving: boolean
  categoryId: number | null
  categoryName: string | null
  accountId: number
  accountName: string
  rhythmUnit: RhythmUnit | null
  rhythmCount: number | null
  anchorDate: string | null
  entryMode: EntryMode
  workdaysOnly: boolean
  validFrom: string
  validTo: string | null
  group: PlanGroup
  monthlyShare: number
  savingGoalId?: number | null
  savingGoalName?: string | null
  /** P8: the savings account of the reserve pot; null for a calculated pot. */
  reserveAccountId?: number | null
}

/** Create or change a planned item; validFrom is the month from which the item or the change applies. */
export interface PlannedItemInput {
  name: string
  amount: number
  saving?: boolean
  categoryId?: number | null
  accountId?: number
  rhythmUnit: RhythmUnit | null
  rhythmCount: number | null
  anchorDate: string | null
  entryMode?: EntryMode
  workdaysOnly?: boolean
  validFrom: string
  savingGoalId?: number | null
}

/** Status of a term (P5). */
export type TermStatus = 'EXPECTED' | 'FULFILLED' | 'MISSED' | 'SKIPPED'

/**
 * A term of a planned item: date is the planned date (with itemId the key for tick-off and skip), dueDate the date
 * after the working-day shift. transactionId is the booking that fulfils it; linked when ticked off or entered.
 */
export interface Term {
  itemId: number
  itemKey: string
  name: string
  date: string
  dueDate: string
  amount: number
  categoryId: number | null
  categoryName: string | null
  accountId: number
  entryMode: EntryMode
  group: PlanGroup
  status: TermStatus
  transactionId: number | null
  linked: boolean
}

/** Sum of the items of one category in one group. */
export interface PlanCategoryTotal {
  group: PlanGroup
  categoryId: number
  categoryName: string
  total: number
}

/**
 * The plan of a month (P2–P4, P9). Sums are monthly shares without sign; unplanned is negative when overplanned.
 * averageIncome and minimumIncome are only for orientation (R23).
 */
export interface Plan {
  month: string
  income: number
  fixed: number
  budgets: number
  saving: number
  unplanned: number
  /** Percent with one decimal, null without planned income (P9). */
  savingRate: number | null
  planTotal: number
  averageIncome: number | null
  minimumIncome: number | null
  averageMonths: number
  categories: PlanCategoryTotal[]
  items: PlannedItem[]
}

/**
 * difference is the deviation from the plan (target - actual); target is the plan of the category (P3).
 * recurring is the part of the expenses that fulfils a term ("fix", P5), oneOff the rest ("variabel").
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
  /** P9: planned saving of the month (monthly shares) and the saving rate in percent, with the saving items. */
  saving: {
    planned: number
    rate: number | null
    items: { itemId: number; name: string; monthly: number; actual?: number }[]
    /** P1: what was saved in the month (bookings of the kind Sparen). */
    actual?: number
  } | null
  /** P5: terms of the month without a booking yet and their sum with sign. */
  expected: { count: number; total: number } | null
  /** P8: the totals of the budget lines with the reserve pots; null when no pot or saving payment changes them. */
  budget?: Totals | null
  /** P8: the reserve pots of the rare items valid in the month. */
  reserves?: ReservePot[]
  /** 27f4: the part of the expenses paid from reserve pots or saving goals; null when nothing. */
  fromReserves?: number | null
}

/** A bank account (R26); used means that entries are booked on it, so it can only be archived. */
export interface BankAccount {
  id: number
  name: string
  iban: string | null
  defaultAccount: boolean
  archived: boolean
  used: boolean
  /** Concept 3.4: only Alltag counts in income and expenses (P1). */
  role?: AccountRole
}

export type AccountRole = 'EVERYDAY' | 'SAVINGS' | 'DEPOT'

/** One account on the page Konten: app balance today (P11) and the last balance from the bank. */
export interface AccountLine {
  id: number
  name: string
  iban: string | null
  role: AccountRole
  defaultAccount: boolean
  archived: boolean
  balance: number
  statementDate: string | null
  statementAmount: number | null
  /** 27f4: optional; belowComfort is true when the balance lies below it */
  comfortAmount: number | null
  belowComfort: boolean
}

export interface AccountGroup {
  role: AccountRole
  total: number
  accounts: AccountLine[]
}

/**
 * 27f2: the reserves of one savings account (accountId null: without account). saved and target sum the pots,
 * missing is what the account balance lacks to cover saved (0 when covered, null without account).
 */
export interface ReserveGroup {
  accountId: number | null
  accountName: string | null
  saved: number
  target: number
  accountBalance: number | null
  missing: number | null
  pots: ReservePot[]
}

/** P11: the app balance on the day of the bank balance and the difference (not booked). */
export interface ReconcileResult {
  appBalance: number
  bankBalance: number
  difference: number
}

export interface BankAccountInput {
  name: string
  iban?: string | null
  /** 27f3: Alltag without it */
  role?: AccountRole
}

/** defaultAccount true makes this the default account; the previous one becomes a normal account. */
export interface BankAccountUpdate {
  name: string
  iban: string | null
  archived: boolean
  defaultAccount: boolean
  role?: AccountRole
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

/** A rare expense in the reserve pot. amount is the cost (positive); nextDate is its next term. */
export interface ReserveLine {
  itemId: number
  description: string
  categoryName: string
  amount: number
  rhythmUnit: RhythmUnit
  rhythmCount: number
  nextDate: string
  /** To put aside each month. */
  monthly: number
  /** What should be saved by now; the full amount in the month it is due. */
  saved: number
  /** Monthly rates still to put aside until it is due; 0 in the month it is due (decision 37). */
  ratesLeft: number
}

/** A booked expense (kind SINGLE, id) or a term of a planned item (kind PLANNED, itemId). amount is positive. */
export interface ForecastEntry {
  kind: 'SINGLE' | 'PLANNED'
  id: number | null
  itemId: number | null
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

/** US36: a link that sets a new password, usable once until expiresAt. */
export interface PasswordResetLink {
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
