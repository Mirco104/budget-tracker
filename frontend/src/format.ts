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
