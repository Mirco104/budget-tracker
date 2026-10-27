// Display formatting and input parsing only. No business rules here (see CLAUDE.md).

const EMPTY = '–'

const euro = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })

// Ratios come with scale 4 from DashboardCalculator, so 2 percent decimals show them exactly.
const percent = new Intl.NumberFormat('de-DE', {
  style: 'percent',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatEuro(value: number | null): string {
  return value === null ? EMPTY : euro.format(value)
}

export function formatPercent(value: number | null): string {
  return value === null ? EMPTY : percent.format(value)
}

const decimal = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** For table cells: the unit (€ or %) is in the column header, which saves width on narrow screens. */
export function formatAmount(value: number | null): string {
  return value === null ? EMPTY : decimal.format(value)
}

/** Ratio as percent number without the % sign, see formatAmount. */
export function formatShare(value: number | null): string {
  return value === null ? EMPTY : decimal.format(value * 100)
}

const monthName = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric' })

/** "2026-11" -> "November 2026" */
export function formatMonth(month: string): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return monthName.format(new Date(year, monthNumber - 1, 1))
}

const shortMonthName = new Intl.DateTimeFormat('de-DE', { month: 'short', year: 'numeric' })

/** "2026-09" -> "Sept. 2026", for narrow table columns */
export function formatMonthShort(month: string): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return shortMonthName.format(new Date(year, monthNumber - 1, 1))
}

export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${day}.${month}.${year}`
}

/** Parses German amount input ("12,50", "1.234,5") into a number, or null if the format is invalid. */
export function parseAmountInput(input: string): number | null {
  const trimmed = input.trim()
  // Dots are only accepted as thousands separators in groups of three, so "12.50" is rejected, not read as 1250.
  if (!/^-?(\d+|\d{1,3}(\.\d{3})+)(,\d{1,2})?$/.test(trimmed)) {
    return null
  }
  return Number(trimmed.replace(/\./g, '').replace(',', '.'))
}

function toMonthString(date: Date): string {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`
}

export function currentMonth(): string {
  return toMonthString(new Date())
}

/** Moves a "YYYY-MM" month by delta months, across year boundaries. */
export function shiftMonth(month: string, delta: number): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return toMonthString(new Date(year, monthNumber - 1 + delta, 1))
}
