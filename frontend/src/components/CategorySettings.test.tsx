import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { CategorySettings } from './CategorySettings'

// Fictional categories, shaped like the backend response.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', sortOrder: 10, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false },
  { id: 2, name: 'Freizeit', sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false },
  { id: 3, name: 'Kredite', sortOrder: 30, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: true },
]

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderSettings(onWrite: (url: string, init: RequestInit) => Promise<Response> = () => json(categories[0])) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) =>
    init?.method && init.method !== 'GET' ? onWrite(url, init) : json(categories),
  )
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <CategorySettings />
    </QueryClientProvider>,
  )
  return fetchMock
}

function writeCalls(fetchMock: ReturnType<typeof renderSettings>) {
  return fetchMock.mock.calls.filter(([, init]) => init?.method && init.method !== 'GET')
}

describe('CategorySettings', () => {
  it('lists active categories in order and archived ones separately', async () => {
    renderSettings()

    await screen.findByRole('form', { name: 'Wohnen' })
    expect(screen.getAllByRole('form', { name: /./ }).map((form) => form.getAttribute('aria-label'))).toEqual([
      'Wohnen',
      'Freizeit',
      'Kredite',
    ])
    expect(screen.getByRole('heading', { name: 'Archiviert' })).toBeInTheDocument()
    const kredite = screen.getByRole('form', { name: 'Kredite' })
    expect(within(kredite).getByRole('button', { name: 'Reaktivieren' })).toBeInTheDocument()
    expect(within(kredite).queryByRole('button', { name: 'Nach oben' })).not.toBeInTheDocument()
    expect(within(screen.getByRole('form', { name: 'Wohnen' })).getByRole('button', { name: 'Nach oben' })).toBeDisabled()
  })

  it('creates a category', async () => {
    const fetchMock = renderSettings(() => json({ ...categories[0], id: 4, name: 'Haustier' }, 201))
    await screen.findByRole('form', { name: 'Wohnen' })

    fireEvent.change(screen.getByLabelText('Neue Kategorie'), { target: { value: ' Haustier ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    const [url, init] = writeCalls(fetchMock)[0]
    expect(url).toBe('/api/categories')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(init?.body as string)).toEqual({ name: 'Haustier' })
    await waitFor(() => expect(screen.getByLabelText('Neue Kategorie')).toHaveValue(''))
  })

  it('renames on Enter and moves down by swapping with the next sort order', async () => {
    const fetchMock = renderSettings()
    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })

    fireEvent.change(within(wohnen).getByLabelText('Name'), { target: { value: 'Miete' } })
    fireEvent.submit(wohnen)
    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    expect(writeCalls(fetchMock)[0][0]).toBe('/api/categories/1')
    expect(JSON.parse(writeCalls(fetchMock)[0][1]?.body as string)).toEqual({
      name: 'Miete',
      sortOrder: 10,
      archived: false,
    })

    fireEvent.click(within(screen.getByRole('form', { name: 'Wohnen' })).getByRole('button', { name: 'Nach unten' }))
    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(2))
    // The mock still answers with the old name; the row sends the name it shows
    expect(JSON.parse(writeCalls(fetchMock)[1][1]?.body as string)).toMatchObject({ name: 'Miete', sortOrder: 20 })
  })

  it('sends a typed name and the move in one request when an arrow is clicked without Enter', async () => {
    let answer: (response: Response) => void = () => {}
    const fetchMock = renderSettings(() => new Promise((resolve) => (answer = resolve)))
    const wohnen = await screen.findByRole('form', { name: 'Wohnen' })

    fireEvent.change(within(wohnen).getByLabelText('Name'), { target: { value: 'Miete' } })
    fireEvent.click(within(wohnen).getByRole('button', { name: 'Nach unten' }))
    // The clicked button is disabled while saving, so the focus leaves the row
    await waitFor(() => expect(within(wohnen).getByRole('button', { name: 'Nach unten' })).toBeDisabled())
    fireEvent.blur(wohnen, { relatedTarget: null })
    answer(new Response(JSON.stringify({ ...categories[0], name: 'Miete', sortOrder: 20 })))

    await waitFor(() => expect(within(wohnen).getByRole('button', { name: 'Nach unten' })).toBeEnabled())
    expect(writeCalls(fetchMock)).toHaveLength(1)
    expect(JSON.parse(writeCalls(fetchMock)[0][1]?.body as string)).toEqual({
      name: 'Miete',
      sortOrder: 20,
      archived: false,
    })
  })

  it('shows the conflict when a used category is deleted', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderSettings(() =>
      json({ status: 409, title: 'Konflikt', detail: 'Es gibt Einträge unter dieser Kategorie. Die Kategorie kann nur archiviert werden.' }, 409),
    )
    const freizeit = await screen.findByRole('form', { name: 'Freizeit' })

    fireEvent.click(within(freizeit).getByRole('button', { name: 'Löschen' }))

    expect(
      await within(freizeit).findByText('Es gibt Einträge unter dieser Kategorie. Die Kategorie kann nur archiviert werden.'),
    ).toBeInTheDocument()
  })
})
