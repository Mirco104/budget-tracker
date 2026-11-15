import { describe, expect, it } from 'vitest'
import { anchorFromDay, dayOfAnchor } from './planning'

describe('anchorFromDay', () => {
  it('puts a monthly term into the month "Gilt ab"', () => {
    expect(anchorFromDay('2026-11', 15, null)).toBe('2026-11-15')
    expect(anchorFromDay('2026-11', 1, null)).toBe('2026-11-01')
  })

  it('uses the last day of a shorter month', () => {
    expect(anchorFromDay('2026-02', 31, null)).toBe('2026-02-28')
    expect(anchorFromDay('2028-02', 30, null)).toBe('2028-02-29')
    expect(anchorFromDay('2026-11', 31, null)).toBe('2026-11-30')
  })

  it('puts a rare term into its due month in the year of "Gilt ab"', () => {
    expect(anchorFromDay('2026-11', 15, 3)).toBe('2026-03-15')
    expect(anchorFromDay('2026-01', 31, 4)).toBe('2026-04-30')
  })
})

describe('dayOfAnchor', () => {
  it('reads day and month of the first term', () => {
    expect(dayOfAnchor('2026-03-15')).toEqual({ day: 15, month: 3 })
  })
})
