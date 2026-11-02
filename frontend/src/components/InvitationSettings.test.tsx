import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { InvitationSettings } from './InvitationSettings'

const LINK = 'http://192.168.178.5:5173/?einladung=token-abc'

function setClipboard(clipboard: unknown) {
  Object.defineProperty(navigator, 'clipboard', { value: clipboard, configurable: true })
}

function setExecCommand(result: boolean) {
  const execCommand = vi.fn(() => result)
  Object.defineProperty(document, 'execCommand', { value: execCommand, configurable: true })
  return execCommand
}

async function renderWithLink() {
  vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(
    new Response(JSON.stringify({ link: LINK, expiresAt: '2026-11-08T17:00:00Z' }), { status: 201 }),
  )))
  render(
    <QueryClientProvider client={new QueryClient()}>
      <InvitationSettings senderName="Person A" />
    </QueryClientProvider>,
  )
  fireEvent.click(screen.getByRole('button', { name: 'Einladungslink erzeugen' }))
  await screen.findByLabelText('Einladungslink')
}

describe('InvitationSettings copy button', () => {
  afterEach(() => {
    setClipboard(undefined)
  })

  it('uses the clipboard API when it is allowed', async () => {
    const writeText = vi.fn(() => Promise.resolve())
    setClipboard({ writeText })
    const execCommand = setExecCommand(true)
    await renderWithLink()

    fireEvent.click(screen.getByRole('button', { name: 'Kopieren' }))

    expect(await screen.findByRole('button', { name: 'Kopiert' })).toBeInTheDocument()
    expect(writeText).toHaveBeenCalledWith(LINK)
    expect(execCommand).not.toHaveBeenCalled()
  })

  // Plain HTTP via the IP address: no clipboard API at all
  it('copies from the selected field without the clipboard API', async () => {
    setClipboard(undefined)
    const execCommand = setExecCommand(true)
    await renderWithLink()

    fireEvent.click(screen.getByRole('button', { name: 'Kopieren' }))

    expect(await screen.findByRole('button', { name: 'Kopiert' })).toBeInTheDocument()
    expect(execCommand).toHaveBeenCalledWith('copy')
  })

  // Embedded browsers may deny the permission
  it('falls back to the field when the clipboard API is denied', async () => {
    setClipboard({ writeText: vi.fn(() => Promise.reject(new DOMException('denied', 'NotAllowedError'))) })
    const execCommand = setExecCommand(true)
    await renderWithLink()

    fireEvent.click(screen.getByRole('button', { name: 'Kopieren' }))

    expect(await screen.findByRole('button', { name: 'Kopiert' })).toBeInTheDocument()
    expect(execCommand).toHaveBeenCalledWith('copy')
  })

  it('leaves the link selected with a hint when nothing can copy', async () => {
    setClipboard(undefined)
    setExecCommand(false)
    await renderWithLink()

    fireEvent.click(screen.getByRole('button', { name: 'Kopieren' }))

    expect(await screen.findByText(/Der Link ist markiert/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Kopieren' })).toBeInTheDocument()
    const field = screen.getByLabelText<HTMLInputElement>('Einladungslink')
    expect(field.selectionEnd! - field.selectionStart!).toBe(LINK.length)
  })
})
