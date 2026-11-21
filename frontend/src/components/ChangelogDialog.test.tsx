import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ChangelogDialog } from './ChangelogDialog'

describe('ChangelogDialog', () => {
  it('shows bold and italic text without the stars', () => {
    render(
      <ChangelogDialog
        onClose={() => {}}
        changelog={['## 0.3.0-alpha', '### Neu', '- **Auswertung:** die Unterseite *Kategorien* zeigt mehr'].join('\n')}
      />,
    )

    expect(screen.getByText('Auswertung:').tagName).toBe('STRONG')
    expect(screen.getByText('Kategorien').tagName).toBe('EM')
    expect(screen.getByRole('listitem')).toHaveTextContent('Auswertung: die Unterseite Kategorien zeigt mehr')
    expect(screen.getByRole('heading', { name: 'Neu' })).toBeInTheDocument()
  })
})
