import { describe, expect, it } from 'vitest'
import { byName } from './sort'

describe('byName', () => {
  it('sorts alphabetically ignoring case and umlauts, without changing the input', () => {
    const input = [{ name: 'Wohnen' }, { name: 'gehalt' }, { name: 'Übrige' }, { name: 'Auto' }, { name: 'Urlaub' }]

    expect(byName(input).map((item) => item.name)).toEqual(['Auto', 'gehalt', 'Übrige', 'Urlaub', 'Wohnen'])
    expect(input[0].name).toBe('Wohnen')
  })
})
