// Input helpers for the planning page (CLAUDE.md: input helpers are allowed). The backend stores the rhythm as unit,
// count and the date of the first term (section 19.3); the dialog asks for "Am Tag" and "Fällig im" instead.

/** Days in a month ("YYYY-MM"). */
function daysIn(month: string): number {
  const [year, m] = month.split('-').map(Number)
  return new Date(year, m, 0).getDate()
}

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/**
 * The first term from "Am Tag" (1–31) and, for a rhythm over one month, "Fällig im" (1–12), in the year of the month
 * "Gilt ab". A day after the end of the month becomes its last day, as the backend does for the 31st (R12).
 */
export function anchorFromDay(validFrom: string, day: number, dueMonth: number | null): string {
  const year = Number(validFrom.slice(0, 4))
  const month = dueMonth === null ? validFrom : `${year}-${pad(dueMonth)}`
  return `${month}-${pad(Math.min(day, daysIn(month)))}`
}

/** "Am Tag" and "Fällig im" of a stored first term, for the dialog. */
export function dayOfAnchor(anchor: string): { day: number; month: number } {
  return { day: Number(anchor.slice(8, 10)), month: Number(anchor.slice(5, 7)) }
}
