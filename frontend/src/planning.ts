// Input helper for the planning page (decision 36, CLAUDE.md): a target is entered in euro or in percent of a
// base from /dashboard (Ø or minimum income) and always saved in euro. This is not a business rule; the backend
// only knows euro. Both directions calculate in whole cents and hundredths of a percent to avoid float noise.

function hasBase(base: number | null): base is number {
  return base !== null && base > 0
}

/** Percent of the base in euro, rounded half up to cents; null without a base. */
export function percentToEuro(percent: number, base: number | null): number | null {
  if (!hasBase(base)) {
    return null
  }
  const baseCents = Math.round(base * 100)
  const hundredths = Math.round(percent * 100)
  return Math.round((baseCents * hundredths) / 10000) / 100
}

/** Euro as percent of the base, rounded half up to 2 decimals; null without a base. */
export function euroToPercent(euro: number, base: number | null): number | null {
  if (!hasBase(base)) {
    return null
  }
  const euroCents = Math.round(euro * 100)
  const baseCents = Math.round(base * 100)
  return Math.round((euroCents * 10000) / baseCents) / 100
}
