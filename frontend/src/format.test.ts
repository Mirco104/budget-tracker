import { describe, expect, it } from 'vitest'
import {
  formatIban,
  formatSigned,
  formatAmount,
  formatDate,
  formatEuro,
  formatMonth,
  formatMonthShort,
  parseAmountInput,
  shiftMonth,
} from './format'

describe('parseAmountInput', () => {
  it.each([
    ['12,50', 12.5],
    ['12', 12],
    ['1.234,5', 1234.5],
    ['12.345.678,90', 12345678.9],
    [' 7,05 ', 7.05],
    ['-5', -5],
  ])('parses %s', (input, expected) => {
    expect(parseAmountInput(input)).toBe(expected)
  })

  it.each(['', 'abc', '12,345', '12.50', '1.23,5', '1234.567', ',5'])('rejects %s', (input) => {
    expect(parseAmountInput(input)).toBeNull()
  })
})

describe('formatting', () => {
  it('formats euro amounts in German style and null as a dash', () => {
    // Intl puts a non-breaking space before the symbol.
    expect(formatEuro(1234.5)).toBe('1.234,50 €')
    expect(formatEuro(null)).toBe('–')
  })

  it('formats table amounts without unit', () => {
    expect(formatAmount(-1234.5)).toBe('-1.234,50')
    expect(formatAmount(null)).toBe('–')
  })

  it('formats ISO dates as TT.MM.JJJJ', () => {
    expect(formatDate('2026-10-05')).toBe('05.10.2026')
  })

  it('formats months with the German month name', () => {
    expect(formatMonth('2026-11')).toBe('November 2026')
    expect(formatMonth('2027-03')).toBe('März 2027')
  })

describe('formatMonthShort', () => {
  it('formats a month short for narrow columns', () => {
    expect(formatMonthShort('2026-09')).toBe('Sept. 2026')
    expect(formatMonthShort('2027-03')).toBe('März 2027')
  })
})
})

describe('shiftMonth', () => {
  it.each([
    ['2026-10', 1, '2026-11'],
    ['2026-10', -1, '2026-09'],
    ['2026-12', 1, '2027-01'],
    ['2026-01', -1, '2025-12'],
  ])('moves %s by %i to %s', (month, delta, expected) => {
    expect(shiftMonth(month, delta)).toBe(expected)
  })
})

describe('formatSigned and formatIban', () => {
  it('signs income and expenses and shows 0 without a sign', () => {
    expect(formatSigned(12)).toBe('+12,00 €')
    expect(formatSigned(-3.5)).toBe('−3,50 €')
    expect(formatSigned(0)).toBe('0,00 €')
  })

  it('groups an IBAN in fours', () => {
    expect(formatIban('DE89370400440532013000')).toBe('DE89 3704 0044 0532 0130 00')
  })
})
