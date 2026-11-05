import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createEvent, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { CategorySettings } from './CategorySettings'
import { categoryIconLabel } from './ui/categoryIcons'

// Fictional categories, shaped like the backend response.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', icon: 'home', sortOrder: 10, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 2, name: 'Freizeit', icon: 'music', sortOrder: 20, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 4, name: 'Urlaub', icon: 'suitcase', sortOrder: 40, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 3, name: 'Kredite', icon: 'card', sortOrder: 30, targetAmount: null, targetPercent: null, targetValidFrom: null, archived: true, incomeOnly: false },
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

function lastBody(fetchMock: ReturnType<typeof renderSettings>) {
  const calls = writeCalls(fetchMock)
  return JSON.parse(String(calls[calls.length - 1][1]?.body))
}

function row(name: string) {
  return screen.getByRole('listitem', { name })
}

function openMenu(name: string) {
  fireEvent.click(within(row(name)).getByRole('button', { name: `Aktionen für ${name}` }))
  return within(screen.getByRole('group', { name: `Aktionen für ${name}` }))
}

describe('CategorySettings', () => {
  it('lists active categories in order with icon and archived ones in their own section', async () => {
    renderSettings()

    await screen.findByRole('listitem', { name: 'Wohnen' })
    const [active, archived] = screen.getAllByRole('list')
    expect(within(active).getAllByRole('listitem').map((li) => li.getAttribute('aria-label'))).toEqual([
      'Wohnen',
      'Freizeit',
      'Urlaub',
    ])
    expect(row('Wohnen').querySelector('.category-icon')).toHaveAttribute('data-icon', 'home')
    expect(screen.getByRole('heading', { name: 'Archiviert (1)' })).toBeInTheDocument()
    expect(within(archived).getByRole('button', { name: 'Wiederherstellen' })).toBeInTheDocument()
    // No buttons per row except icon and menu (decision: everything else is in "⋯")
    expect(within(row('Wohnen')).getAllByRole('button')).toHaveLength(2)
  })

  it('offers rename, icon, move, archive and delete in the menu "⋯"', async () => {
    renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })

    const menu = openMenu('Wohnen')

    expect(menu.getAllByRole('button').map((b) => b.textContent)).toEqual([
      'Umbenennen',
      'Icon ändern',
      'Nach oben',
      'Nach unten',
      'Archivieren',
      'Löschen',
    ])
    // The first category cannot move up
    expect(menu.getByRole('button', { name: 'Nach oben' })).toBeDisabled()
    expect(menu.getByRole('button', { name: 'Umbenennen' })).toHaveFocus()
  })

  it('closes the menu with Escape and gives the focus back to its button', async () => {
    renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })

    const menu = openMenu('Wohnen')
    fireEvent.keyDown(menu.getByRole('button', { name: 'Umbenennen' }), { key: 'Escape' })

    expect(screen.queryByRole('group', { name: 'Aktionen für Wohnen' })).not.toBeInTheDocument()
    expect(within(row('Wohnen')).getByRole('button', { name: 'Aktionen für Wohnen' })).toHaveFocus()
  })

  it('creates a category from "+ Kategorie"', async () => {
    const fetchMock = renderSettings(() => json({ ...categories[0], id: 5, name: 'Haustier' }, 201))
    await screen.findByRole('listitem', { name: 'Wohnen' })

    fireEvent.click(screen.getByRole('button', { name: 'Kategorie' }))
    fireEvent.change(screen.getByLabelText('Neue Kategorie'), { target: { value: ' Haustier ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    const [url, init] = writeCalls(fetchMock)[0]
    expect(url).toBe('/api/categories')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({ name: 'Haustier' })
  })

  it('does not create a category without a name', async () => {
    const fetchMock = renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })

    fireEvent.click(screen.getByRole('button', { name: 'Kategorie' }))
    fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

    expect(screen.getByText('Der Name ist Pflicht.')).toBeInTheDocument()
    expect(writeCalls(fetchMock)).toHaveLength(0)
  })

  it('renames from the menu on Enter, only once', async () => {
    const fetchMock = renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })

    openMenu('Wohnen').getByRole('button', { name: 'Umbenennen' }).click()
    const input = await screen.findByLabelText('Neuer Name für Wohnen')
    expect(input).toHaveValue('Wohnen')
    fireEvent.change(input, { target: { value: 'Miete und Wohnen' } })
    fireEvent.submit(input)
    fireEvent.blur(input)

    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    const [url, init] = writeCalls(fetchMock)[0]
    expect(url).toBe('/api/categories/1')
    expect(JSON.parse(String(init?.body))).toEqual({ name: 'Miete und Wohnen', sortOrder: 10, archived: false, icon: 'home' })
  })

  it('cancels renaming with Escape', async () => {
    const fetchMock = renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })

    openMenu('Wohnen').getByRole('button', { name: 'Umbenennen' }).click()
    const input = await screen.findByLabelText('Neuer Name für Wohnen')
    fireEvent.change(input, { target: { value: 'Etwas anderes' } })
    fireEvent.keyDown(input, { key: 'Escape' })

    expect(screen.queryByLabelText('Neuer Name für Wohnen')).not.toBeInTheDocument()
    expect(writeCalls(fetchMock)).toHaveLength(0)
  })

  it('shows a taken name at the row', async () => {
    renderSettings(() => json({ status: 409, detail: 'Den Namen „Freizeit“ gibt es schon.' }, 409))
    await screen.findByRole('listitem', { name: 'Wohnen' })

    openMenu('Wohnen').getByRole('button', { name: 'Umbenennen' }).click()
    const input = await screen.findByLabelText('Neuer Name für Wohnen')
    fireEvent.change(input, { target: { value: 'Freizeit' } })
    fireEvent.submit(input)

    expect(await within(row('Wohnen')).findByRole('alert')).toHaveTextContent('Den Namen „Freizeit“ gibt es schon.')
  })

  // US10: "Nach oben" / "Nach unten" also work with the keyboard; the backend swaps neighbours
  it('moves down and up from the menu', async () => {
    const fetchMock = renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })

    openMenu('Wohnen').getByRole('button', { name: 'Nach unten' }).click()
    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    expect(lastBody(fetchMock)).toEqual({ name: 'Wohnen', sortOrder: 20, archived: false, icon: 'home' })

    openMenu('Urlaub').getByRole('button', { name: 'Nach oben' }).click()
    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(2))
    expect(lastBody(fetchMock)).toEqual({ name: 'Urlaub', sortOrder: 20, archived: false, icon: 'suitcase' })
  })

  it('cannot move the last category down', async () => {
    renderSettings()
    await screen.findByRole('listitem', { name: 'Urlaub' })

    expect(openMenu('Urlaub').getByRole('button', { name: 'Nach unten' })).toBeDisabled()
  })

  // US10: sort by dragging; the dropped category takes the place of the target in one request
  it('moves a category by dragging it onto another one', async () => {
    const fetchMock = renderSettings()
    await screen.findByRole('listitem', { name: 'Urlaub' })

    const handle = row('Urlaub').querySelector('.drag-handle') as HTMLElement
    const dataTransfer = { setData: vi.fn(), effectAllowed: '' }
    fireEvent(handle, Object.assign(createEvent.dragStart(handle), { dataTransfer }))
    fireEvent.dragOver(row('Wohnen'))
    expect(row('Wohnen')).toHaveClass('drop-target')
    fireEvent.drop(row('Wohnen'))

    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    expect(writeCalls(fetchMock)[0][0]).toBe('/api/categories/4')
    expect(lastBody(fetchMock)).toEqual({ name: 'Urlaub', sortOrder: 10, archived: false, icon: 'suitcase' })
  })

  it('does nothing when a category is dropped onto itself', async () => {
    const fetchMock = renderSettings()
    await screen.findByRole('listitem', { name: 'Urlaub' })

    const handle = row('Urlaub').querySelector('.drag-handle') as HTMLElement
    fireEvent(handle, Object.assign(createEvent.dragStart(handle), { dataTransfer: { setData: vi.fn() } }))
    fireEvent.drop(row('Urlaub'))

    expect(writeCalls(fetchMock)).toHaveLength(0)
  })

  it('changes the icon from the picker, by the icon button or the menu', async () => {
    const fetchMock = renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })

    fireEvent.click(within(row('Freizeit')).getByRole('button', { name: /Icon ändern/ }))
    const picker = screen.getByRole('group', { name: 'Icon wählen' })
    expect(within(picker).getByRole('button', { pressed: true })).toHaveAttribute('aria-label', categoryIconLabel('music'))
    fireEvent.keyDown(picker, { key: 'Escape' })
    expect(screen.queryByRole('group', { name: 'Icon wählen' })).not.toBeInTheDocument()

    openMenu('Wohnen').getByRole('button', { name: 'Icon ändern' }).click()
    const second = await screen.findByRole('group', { name: 'Icon wählen' })
    fireEvent.click(within(second).getAllByRole('button', { pressed: false })[0])

    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    expect(lastBody(fetchMock).icon).not.toBe('home')
    expect(lastBody(fetchMock).name).toBe('Wohnen')
  })

  it('archives from the menu and restores from the archived section', async () => {
    const fetchMock = renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })

    openMenu('Freizeit').getByRole('button', { name: 'Archivieren' }).click()
    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    expect(lastBody(fetchMock)).toEqual({ name: 'Freizeit', sortOrder: 20, archived: true, icon: 'music' })

    fireEvent.click(within(row('Kredite')).getByRole('button', { name: 'Wiederherstellen' }))
    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(2))
    expect(lastBody(fetchMock)).toEqual({ name: 'Kredite', sortOrder: 30, archived: false, icon: 'card' })
  })

  it('deletes only after asking once more', async () => {
    const fetchMock = renderSettings(() => Promise.resolve(new Response(null, { status: 204 })))
    await screen.findByRole('listitem', { name: 'Wohnen' })

    openMenu('Urlaub').getByRole('button', { name: 'Löschen' }).click()
    const confirm = await within(row('Urlaub')).findByRole('group', { name: 'Löschen bestätigen' })
    expect(writeCalls(fetchMock)).toHaveLength(0)
    fireEvent.click(within(confirm).getByRole('button', { name: 'Endgültig löschen' }))

    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    expect(writeCalls(fetchMock)[0][0]).toBe('/api/categories/4')
    expect(writeCalls(fetchMock)[0][1]?.method).toBe('DELETE')
  })

  it('shows the conflict when a used category is deleted', async () => {
    renderSettings(() =>
      json({ status: 409, detail: 'Die Kategorie wird noch verwendet. Archiviere sie stattdessen.' }, 409),
    )
    await screen.findByRole('listitem', { name: 'Kredite' })

    fireEvent.click(within(row('Kredite')).getByRole('button', { name: 'Löschen' }))
    fireEvent.click(within(row('Kredite')).getByRole('button', { name: 'Endgültig löschen' }))

    expect(await within(row('Kredite')).findByRole('alert')).toHaveTextContent(
      'Die Kategorie wird noch verwendet. Archiviere sie stattdessen.',
    )
  })
})
