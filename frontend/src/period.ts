// Periods of the transaction list (US26): date arithmetic for display and requests only, no business rules.
import { formatDate, formatMonth } from './format'

export type PeriodPreset = 'month' | 'quarter' | 'year' | 'custom'

export interface Period {
  from: string
  to: string
}

export const PRESETS: { value: PeriodPreset; label: string }[] = [
  { value: 'month', label: 'Dieser Monat' },
  { value: 'quarter', label: 'Letzte 3 Monate' },
  { value: 'year', label: 'Dieses Jahr' },
  { value: 'custom', label: 'Zeitraum …' },
]

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

function lastDay(year: number, month: number): number {
  return new Date(year, month, 0).getDate()
}

/**
 * The period of a quick choice around today ("YYYY-MM-DD"): this month, the last 3 months including this one, or
 * this calendar year. "custom" has no own period.
 */
export function presetPeriod(preset: Exclude<PeriodPreset, 'custom'>, today: string): Period {
  const [year, month] = today.split('-').map(Number)
  const end = `${year}-${pad(month)}-${pad(lastDay(year, month))}`
  if (preset === 'year') {
    return { from: `${year}-01-01`, to: `${year}-12-31` }
  }
  if (preset === 'quarter') {
    const start = new Date(year, month - 3, 1)
    return { from: `${start.getFullYear()}-${pad(start.getMonth() + 1)}-01`, to: end }
  }
  return { from: `${year}-${pad(month)}-01`, to: end }
}

/** "November 2026", "September bis November 2026", "2026" or "15.10.2026 bis 04.11.2026". */
export function periodLabel(period: Period): string {
  const [fromYear, fromMonth, fromDay] = period.from.split('-')
  const [toYear, toMonth, toDay] = period.to.split('-')
  const wholeMonths = fromDay === '01' && Number(toDay) === lastDay(Number(toYear), Number(toMonth))
  if (!wholeMonths) {
    return period.from === period.to ? formatDate(period.from) : `${formatDate(period.from)} bis ${formatDate(period.to)}`
  }
  if (fromYear === toYear && fromMonth === '01' && toMonth === '12') {
    return fromYear
  }
  const first = formatMonth(`${fromYear}-${fromMonth}`)
  const last = formatMonth(`${toYear}-${toMonth}`)
  if (first === last) {
    return first
  }
  // "September bis November 2026" when both are in the same year
  return fromYear === toYear ? `${first.split(' ')[0]} bis ${last}` : `${first} bis ${last}`
}

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/** A complete date as the date field gives it; an incomplete entry is empty. */
export function isCompleteDate(value: string): boolean {
  return DATE_PATTERN.test(value)
}
