import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { InfoTip } from './InfoTip'

function renderTip() {
  render(
    <div>
      <InfoTip label="Rücklagen">Seltene Ausgaben kommen auf einmal.</InfoTip>
      <p>Daneben</p>
    </div>,
  )
  return screen.getByRole('button', { name: 'Erklärung: Rücklagen' })
}

// Decision 37: an explanation on demand, by mouse, keyboard and touch
describe('InfoTip', () => {
  it('shows the explanation while the mouse is over the symbol', () => {
    const button = renderTip()
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    fireEvent.mouseEnter(button.parentElement!)
    expect(screen.getByRole('tooltip')).toHaveTextContent('Seltene Ausgaben kommen auf einmal.')
    expect(button).toHaveAttribute('aria-describedby', screen.getByRole('tooltip').id)

    fireEvent.mouseLeave(button.parentElement!)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it('shows the explanation while the symbol has the focus', () => {
    const button = renderTip()

    fireEvent.focus(button)
    expect(screen.getByRole('tooltip')).toBeInTheDocument()
    fireEvent.blur(button)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it('keeps the explanation open after a click until a click next to it', () => {
    const button = renderTip()

    fireEvent.click(button)
    fireEvent.mouseLeave(button.parentElement!)
    expect(screen.getByRole('tooltip')).toBeInTheDocument()
    expect(button).toHaveAttribute('aria-expanded', 'true')

    fireEvent.pointerDown(screen.getByText('Daneben'))
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    expect(button).toHaveAttribute('aria-expanded', 'false')
  })

  it('closes with a second click and with Escape', () => {
    const button = renderTip()

    fireEvent.click(button)
    fireEvent.click(button)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    fireEvent.click(button)
    fireEvent.keyDown(button, { key: 'Escape' })
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })
})
