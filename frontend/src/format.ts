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
  const normalized = input.trim().replace(/\./g, '')
  if (!/^-?\d+(,\d{1,2})?$/.test(normalized)) {
    return null
  }
  return Number(normalized.replace(',', '.'))
}

export function currentMonth(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}
