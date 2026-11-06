import { describe, expect, it } from 'vitest'
import { euroToPercent, percentToEuro } from './planning'

// Decision 36: percent is an input helper on the planning page; the backend only stores euro
describe('percentToEuro', () => {
  it.each([
    [50, 2000, 1000],
    [46.51, 2150, 999.97],
    // 33.33 % of 300 is 99.99, without float noise
    [33.33, 300, 99.99],
    [0, 2850, 0],
    [100, 2812.5, 2812.5],
    // Half a cent rounds up: 0.5 % of 1.01 is 0.00505
    [0.5, 1.01, 0.01],
  ])('%s %% of %s € is %s €', (percent, base, expected) => {
    expect(percentToEuro(percent, base)).toBe(expected)
  })

  it('has no value without a base', () => {
    expect(percentToEuro(10, null)).toBeNull()
    expect(percentToEuro(10, 0)).toBeNull()
  })
})

describe('euroToPercent', () => {
  it.each([
    [1000, 2000, 50],
    [950, 2812.5, 33.78],
    [180, 2812.5, 6.4],
    [0, 3000, 0],
    // More than the base is possible, the field shows it
    [3000, 2000, 150],
  ])('%s € of %s € is %s %%', (euro, base, expected) => {
    expect(euroToPercent(euro, base)).toBe(expected)
  })

  it('has no value without a base', () => {
    expect(euroToPercent(100, null)).toBeNull()
    expect(euroToPercent(100, 0)).toBeNull()
  })

  it('gives back the euro value it came from for whole percents', () => {
    const percent = euroToPercent(1000, 2000)
    expect(percent).not.toBeNull()
    expect(percentToEuro(percent as number, 2000)).toBe(1000)
  })
})
