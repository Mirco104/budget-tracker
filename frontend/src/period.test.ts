import { describe, expect, it } from 'vitest'
import { isCompleteDate, periodLabel, presetPeriod } from './period'

describe('presetPeriod', () => {
  it('is this month from the first to the last day', () => {
    expect(presetPeriod('month', '2026-02-14')).toEqual({ from: '2026-02-01', to: '2026-02-28' })
    expect(presetPeriod('month', '2028-02-29')).toEqual({ from: '2028-02-01', to: '2028-02-29' })
  })

  it('is the last 3 months including this one, across the turn of the year', () => {
    expect(presetPeriod('quarter', '2026-11-05')).toEqual({ from: '2026-09-01', to: '2026-11-30' })
    expect(presetPeriod('quarter', '2027-01-31')).toEqual({ from: '2026-11-01', to: '2027-01-31' })
  })

  it('is this calendar year', () => {
    expect(presetPeriod('year', '2026-11-05')).toEqual({ from: '2026-01-01', to: '2026-12-31' })
  })
})

describe('periodLabel', () => {
  it.each([
    [{ from: '2026-11-01', to: '2026-11-30' }, 'November 2026'],
    [{ from: '2026-09-01', to: '2026-11-30' }, 'September bis November 2026'],
    [{ from: '2026-11-01', to: '2027-01-31' }, 'November 2026 bis Januar 2027'],
    [{ from: '2026-01-01', to: '2026-12-31' }, '2026'],
    [{ from: '2026-10-15', to: '2026-11-04' }, '15.10.2026 bis 04.11.2026'],
    [{ from: '2026-10-15', to: '2026-10-15' }, '15.10.2026'],
  ])('names %o as %s', (period, label) => {
    expect(periodLabel(period)).toBe(label)
  })
})

describe('isCompleteDate', () => {
  it('accepts only a full date', () => {
    expect(isCompleteDate('2026-10-15')).toBe(true)
    expect(isCompleteDate('')).toBe(false)
    expect(isCompleteDate('2026-10')).toBe(false)
  })
})
