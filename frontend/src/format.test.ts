import { describe, expect, it } from 'vitest'
import { formatDate, formatEuro, formatPercent, parseAmountInput } from './format'

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

  it('formats ratios with two decimals', () => {
    expect(formatPercent(0.2232)).toBe('22,32 %')
    expect(formatPercent(null)).toBe('–')
  })

  it('formats ISO dates as TT.MM.JJJJ', () => {
    expect(formatDate('2026-10-05')).toBe('05.10.2026')
  })
})
