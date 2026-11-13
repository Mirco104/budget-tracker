import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Category } from '../api/types'
import { CategorySettings } from './CategorySettings'
import { categoryIconLabel } from './ui/categoryIcons'

// Fictional categories, shaped like the backend response.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', icon: 'home', archived: false, incomeOnly: false },
  { id: 2, name: 'Freizeit', icon: 'music', archived: false, incomeOnly: false },
  { id: 4, name: 'Urlaub', icon: 'suitcase', archived: false, incomeOnly: false },
  { id: 3, name: 'Kredite', icon: 'card', archived: true, incomeOnly: false },
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
  // Decision 38: the backend sends them alphabetically; the page keeps that order and offers no sorting
  it('lists active categories in the order of the backend, with icon, and archived ones apart', async () => {
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
    // No buttons per row except icon and menu (decision: everything else is in "⋯"), and no drag handle
    expect(within(row('Wohnen')).getAllByRole('button')).toHaveLength(2)
    expect(row('Wohnen').querySelector('[draggable="true"]')).toBeNull()
  })

  it('offers rename, icon, archive and delete in the menu "⋯", no moving', async () => {
    renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })

    const menu = openMenu('Wohnen')

    expect(menu.getAllByRole('button').map((b) => b.textContent)).toEqual([
      'Umbenennen',
      'Icon ändern',
      'Archivieren',
      'Löschen',
    ])
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
    // Without choosing, the new category gets the default icon (US28)
    expect(JSON.parse(String(init?.body))).toEqual({ name: 'Haustier', icon: 'tag' })
  })

  // A new category can get its icon right away (US28)
  it('creates a category with a chosen icon', async () => {
    const fetchMock = renderSettings(() => json({ ...categories[0], id: 5, name: 'Haustier', icon: 'paw' }, 201))
    await screen.findByRole('listitem', { name: 'Wohnen' })
    fireEvent.click(screen.getByRole('button', { name: 'Kategorie' }))

    const iconButton = screen.getByRole('button', { name: 'Icon' })
    expect(iconButton.querySelector('.category-icon')).toHaveAttribute('data-icon', 'tag')
    fireEvent.click(iconButton)
    const picker = screen.getByRole('group', { name: 'Icon wählen' })
    const chosen = within(picker).getAllByRole('button', { pressed: false })[0]
    const key = chosen.querySelector('.category-icon')?.getAttribute('data-icon')
    fireEvent.click(chosen)

    expect(screen.queryByRole('group', { name: 'Icon wählen' })).not.toBeInTheDocument()
    expect(iconButton.querySelector('.category-icon')).toHaveAttribute('data-icon', key)
    fireEvent.change(screen.getByLabelText('Neue Kategorie'), { target: { value: 'Haustier' } })
    fireEvent.click(screen.getByRole('button', { name: 'Hinzufügen' }))

    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(1))
    expect(JSON.parse(String(writeCalls(fetchMock)[0][1]?.body))).toEqual({ name: 'Haustier', icon: key })
  })

  it('starts again with the default icon after cancelling', async () => {
    renderSettings()
    await screen.findByRole('listitem', { name: 'Wohnen' })
    fireEvent.click(screen.getByRole('button', { name: 'Kategorie' }))
    fireEvent.click(screen.getByRole('button', { name: 'Icon' }))
    fireEvent.click(within(screen.getByRole('group', { name: 'Icon wählen' })).getAllByRole('button', { pressed: false })[0])

    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Kategorie' }))

    expect(screen.getByRole('button', { name: 'Icon' }).querySelector('.category-icon')).toHaveAttribute('data-icon', 'tag')
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
    expect(JSON.parse(String(init?.body))).toEqual({ name: 'Miete und Wohnen', archived: false, icon: 'home' })
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
    expect(lastBody(fetchMock)).toEqual({ name: 'Freizeit', archived: true, icon: 'music' })

    fireEvent.click(within(row('Kredite')).getByRole('button', { name: 'Wiederherstellen' }))
    await waitFor(() => expect(writeCalls(fetchMock)).toHaveLength(2))
    expect(lastBody(fetchMock)).toEqual({ name: 'Kredite', archived: false, icon: 'card' })
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
