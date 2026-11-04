import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { CategoryIcon } from './CategoryIcon'
import { CATEGORY_ICON_KEYS, categoryIconLabel } from './categoryIcons'

describe('CategoryIcon', () => {
  it('has the 21 keys of the spec, ending with the default tag', () => {
    expect(CATEGORY_ICON_KEYS).toHaveLength(21)
    expect(CATEGORY_ICON_KEYS.at(-1)).toBe('tag')
  })

  it('draws a decorative line icon for every key', () => {
    for (const key of CATEGORY_ICON_KEYS) {
      const { container, unmount } = render(<CategoryIcon icon={key} />)
      const svg = container.querySelector('svg')
      expect(svg).toHaveAttribute('aria-hidden', 'true')
      expect(svg?.childElementCount).toBeGreaterThan(0)
      expect(container.querySelector('.category-icon')).toHaveAttribute('data-icon', key)
      unmount()
    }
  })

  it('draws an unknown key as tag', () => {
    const { container } = render(<CategoryIcon icon="rocket" />)

    expect(container.querySelector('.category-icon')).toHaveAttribute('data-icon', 'tag')
    expect(categoryIconLabel('rocket')).toBe('Etikett')
  })
})
