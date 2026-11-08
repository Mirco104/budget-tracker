import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import { InlineCreate } from './InlineCreate'

function renderField(onCreate = vi.fn(() => Promise.resolve()), onCancel = vi.fn(), initial?: string) {
  render(
    <InlineCreate label="Neue Kategorie" placeholder="Name der Kategorie" initial={initial} onCreate={onCreate} onCancel={onCancel} />,
  )
  return { onCreate, onCancel, input: screen.getByRole('textbox', { name: 'Neue Kategorie' }) }
}

describe('InlineCreate', () => {
  it('asks for a name before creating', () => {
    const { onCreate } = renderField()

    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Der Name ist Pflicht.')
    expect(onCreate).not.toHaveBeenCalled()
  })

  it('creates the trimmed name with Enter and stops the key from reaching a surrounding form', () => {
    const submit = vi.fn((e: Event) => e.preventDefault())
    const onCreate = vi.fn(() => Promise.resolve())
    render(
      <form onSubmit={(e) => submit(e.nativeEvent)}>
        <InlineCreate label="Neues Konto" placeholder="Name des Kontos" onCreate={onCreate} onCancel={vi.fn()} />
      </form>,
    )
    const input = screen.getByRole('textbox', { name: 'Neues Konto' })

    fireEvent.change(input, { target: { value: '  Tagesgeld  ' } })
    const event = fireEvent.keyDown(input, { key: 'Enter' })

    expect(event).toBe(false)
    expect(onCreate).toHaveBeenCalledWith('Tagesgeld')
    expect(submit).not.toHaveBeenCalled()
  })

  it('prefills the name', () => {
    const { input } = renderField(undefined, undefined, 'Sparkasse …3000')
    expect(input).toHaveValue('Sparkasse …3000')
  })

  it('cancels with Escape without passing the key on', () => {
    const outer = vi.fn()
    const onCancel = vi.fn()
    render(
      <div onKeyDown={outer}>
        <InlineCreate label="Neue Kategorie" placeholder="Name" onCreate={vi.fn()} onCancel={onCancel} />
      </div>,
    )

    fireEvent.keyDown(screen.getByRole('textbox', { name: 'Neue Kategorie' }), { key: 'Escape' })

    expect(onCancel).toHaveBeenCalled()
    expect(outer).not.toHaveBeenCalled()
  })

  it('shows the message of a failed create', async () => {
    const onCreate = vi.fn(() => Promise.reject(new ApiError({ status: 409, detail: 'Den Namen gibt es schon.' })))
    const { input } = renderField(onCreate)

    fireEvent.change(input, { target: { value: 'Wohnen' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Den Namen gibt es schon.'))
    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeEnabled()
  })
})
