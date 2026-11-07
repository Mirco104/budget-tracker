// Display formatting and input parsing only. No business rules here (see CLAUDE.md).

const EMPTY = '–'

const euro = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })


export function formatEuro(value: number | null): string {
  return value === null ? EMPTY : euro.format(value)
}


/** "+12,00 €" for income, "−12,00 €" for expenses, "0,00 €" without a sign (e.g. a day of transfers, R27). */
export function formatSigned(value: number): string {
  if (value === 0) {
    return euro.format(0)
  }
  return `${value > 0 ? '+' : '−'}${euro.format(Math.abs(value))}`
}

const decimal = new Intl.NumberFormat('de-DE', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** For table cells: the unit (€) is in the column header, which saves width on narrow screens. */
export function formatAmount(value: number | null): string {
  return value === null ? EMPTY : decimal.format(value)
}

const wholeNumber = new Intl.NumberFormat('de-DE', { maximumFractionDigits: 0 })

/** For chart axes: whole numbers without unit ("1.914"). */
export function formatWholeNumber(value: number): string {
  return wholeNumber.format(value)
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

const monthOnly = new Intl.DateTimeFormat('de-DE', { month: 'long' })
const shortMonthOnly = new Intl.DateTimeFormat('de-DE', { month: 'short' })

/** "2026-10" -> "Oktober", or "Okt." with short, where the year is clear from the page */
export function formatMonthName(month: string, short = false): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return (short ? shortMonthOnly : monthOnly).format(new Date(year, monthNumber - 1, 1))
}

export function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-')
  return `${day}.${month}.${year}`
}

/** An ISO instant in local time, e.g. "08.11.2026, 18:12". */
export function formatDateTime(isoInstant: string): string {
  return new Intl.DateTimeFormat('de-DE', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(isoInstant))
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

/** Date for a new booking: today in the current month, otherwise the first day of the month. */
export function bookingDateFor(month: string): string {
  const today = todayIso()
  return today.startsWith(month) ? today : `${month}-01`
}

/** Today as "YYYY-MM-DD" in local time. */
export function todayIso(): string {
  const today = new Date()
  return `${toMonthString(today)}-${String(today.getDate()).padStart(2, '0')}`
}

/** Moves a "YYYY-MM" month by delta months, across year boundaries. */
export function shiftMonth(month: string, delta: number): string {
  const [year, monthNumber] = month.split('-').map(Number)
  return toMonthString(new Date(year, monthNumber - 1 + delta, 1))
}

/** "DE89 3704 0044 0532 0130 00": an IBAN in groups of four, for display only. */
export function formatIban(iban: string): string {
  return iban.replace(/(.{4})(?=.)/g, '$1 ')
}
