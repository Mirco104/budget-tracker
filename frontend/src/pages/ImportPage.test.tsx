import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ImportBatch, ImportEntry, ImportPreview } from '../api/imports'
import type { BankAccount, Category } from '../api/types'
import { ImportPage } from './ImportPage'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', icon: 'home', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 8, name: 'Freizeit', icon: 'music', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 9, name: 'Alt', icon: 'tag', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: true, incomeOnly: false },
]
const accounts: BankAccount[] = [
  { id: 1, name: 'Girokonto', iban: null, defaultAccount: true, archived: false, used: true },
  { id: 2, name: 'C24 Ausgabenkonto', iban: null, defaultAccount: false, archived: false, used: true },
]

function entry(values: Partial<ImportEntry>): ImportEntry {
  return {
    line: 2, status: 'NEW', reason: null, fingerprint: 'f'.repeat(64), source: 'C24:Ausgabenkonto', date: '2026-11-04',
    amount: -2.99, description: 'Spiele Shop', counterparty: 'Spiele Shop', bankCategory: null, suggestionKey: 'spiele shop',
    categoryId: null, certain: false, transfer: false, transferCertain: false, transferPeerId: null, replaces: null, duplicateOf: null,
    ...values,
  }
}

const preview: ImportPreview = {
  profile: 'C24',
  profileLabel: 'C24',
  rows: 7,
  newRows: 5,
  duplicates: 1,
  skipped: 1,
  sources: [
    { key: 'C24:Ausgabenkonto', name: 'Ausgabenkonto', accountId: null, suggestedName: 'C24 Ausgabenkonto' },
    { key: 'C24:Pocket Urlaub', name: 'Pocket Urlaub', accountId: null, suggestedName: 'C24 Pocket Urlaub' },
  ],
  entries: [
    entry({ line: 2, fingerprint: 'a'.repeat(64), description: 'Kino Nord', suggestionKey: 'kino nord', categoryId: 8, certain: true, replaces: { recurringId: 3, date: '2026-11-03', description: 'Kino-Abo', amount: 2.99, categoryId: 8 } }),
    entry({ line: 3, fingerprint: 'b'.repeat(64), description: 'Spiele Shop', categoryId: 8, certain: false, bankCategory: 'Freizeit & Unterhaltung' }),
    entry({ line: 4, fingerprint: 'c'.repeat(64), description: 'Spiele Shop', amount: -4.99, categoryId: 8, certain: false }),
    entry({ line: 5, fingerprint: 'd'.repeat(64), description: 'Auf Pocket', amount: -100, source: 'C24:Pocket Urlaub', suggestionKey: 'auf pocket', transfer: true, transferPeerId: 77 }),
    entry({ line: 6, fingerprint: 'e'.repeat(64), status: 'POSSIBLE_DUPLICATE', description: 'Bäcker', suggestionKey: 'bäcker', amount: -3.1, duplicateOf: { id: 5, date: '2026-11-03', description: 'Brötchen' } }),
    entry({ line: 7, fingerprint: '1'.repeat(64), status: 'DUPLICATE', reason: 'schon importiert', description: 'Alt' }),
    { ...entry({ line: 8 }), status: 'SKIPPED', reason: 'vorgemerkt (noch nicht gebucht)', fingerprint: null, description: null, amount: null, date: null },
  ],
}

const batches: ImportBatch[] = [
  { id: 4, profile: 'N26', profileLabel: 'N26', createdAt: '2026-11-07T20:15:00Z', firstDate: '2026-10-01', lastDate: '2026-10-31', count: 12 },
]

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderPage(previewAnswer: () => Promise<Response> = () => json(preview)) {
  let currentCategories = categories
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (init?.method === 'DELETE') {
      return Promise.resolve(new Response(null, { status: 204 }))
    }
    if (url === '/api/imports/preview') {
      return previewAnswer()
    }
    if (url === '/api/imports' && init?.method === 'POST') {
      return json({ batch: { ...batches[0], id: 5, profile: 'C24', profileLabel: 'C24', firstDate: '2026-11-04', lastDate: '2026-11-04', count: 4 }, created: 4, skippedDuplicates: 0 }, 201)
    }
    if (url.startsWith('/api/imports')) {
      return json(batches)
    }
    if (url.startsWith('/api/bank-accounts')) {
      return json(accounts)
    }
    if (url === '/api/categories' && init?.method === 'POST') {
      const { name } = JSON.parse(String(init.body)) as { name: string }
      if (name.toLowerCase() === 'wohnen') {
        return json({ status: 409, detail: 'Eine Kategorie mit dem Namen "Wohnen" gibt es schon.' }, 409)
      }
      const created: Category = { ...categories[0], id: 50, name, icon: 'tag', sortOrder: 100 }
      currentCategories = [...currentCategories, created]
      return json(created, 201)
    }
    return url.startsWith('/api/categories') ? json(currentCategories) : json([])
  })
  vi.stubGlobal('fetch', fetchMock)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <ImportPage />
    </QueryClientProvider>,
  )
  return fetchMock
}

async function upload() {
  const file = new File(['Transaktionstyp,Buchungsdatum'], 'export.csv', { type: 'text/csv' })
  fireEvent.change(screen.getByLabelText('CSV-Datei wählen'), { target: { files: [file] } })
  await screen.findByRole('region', { name: 'Datei' })
}

function row(name: string) {
  return within(screen.getByRole('list', { name: 'Neue Zeilen' })).getAllByRole('listitem', { name })
}

function sent(fetchMock: ReturnType<typeof vi.fn>, url: string) {
  const call = fetchMock.mock.calls.find(([u, init]) => u === url && (init as RequestInit | undefined)?.method === 'POST')
  return call ? (call[1] as RequestInit) : undefined
}

describe('ImportPage', () => {
  it('uploads the file as multipart and shows the summary and the accounts of the file', async () => {
    const fetchMock = renderPage()
    await upload()

    const init = sent(fetchMock, '/api/imports/preview')
    expect(init?.body).toBeInstanceOf(FormData)
    expect(new Headers(init?.headers).has('Content-Type')).toBe(false)
    const summary = screen.getByRole('region', { name: 'Datei' })
    expect(summary).toHaveTextContent('7 Zeilen: 5 neu, 1 schon importiert, 1 nicht importierbar')
    expect(within(summary).getByLabelText('Ausgabenkonto in der Datei')).toHaveValue('new')
    expect(within(summary).getByRole('option', { name: 'Neues Konto anlegen: C24 Ausgabenkonto' })).toBeInTheDocument()
  })

  // US33: certain suggestions and transfers are preset, uncertain ones are offered, possible duplicates unticked
  it('presets certain suggestions and transfers and leaves the uncertain rows to the user', async () => {
    renderPage()
    await upload()

    expect(within(row('Kino Nord')[0]).getByLabelText('Kategorie für Kino Nord')).toHaveValue('8')
    const pocket = row('Auf Pocket')[0]
    expect(within(pocket).getByLabelText('Kategorie für Auf Pocket')).toHaveValue('transfer')
    expect(pocket).toHaveTextContent('Umbuchung erkannt')
    expect(pocket).toHaveTextContent('Gegenbuchung wird ebenfalls zur Umbuchung')
    const [shop] = row('Spiele Shop')
    expect(within(shop).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('')
    expect(within(shop).getByRole('button', { name: /Vorschlag: Freizeit/ })).toBeInTheDocument()
    const baker = row('Bäcker')[0]
    expect(within(baker).getByLabelText('Bäcker importieren')).not.toBeChecked()
    expect(baker).toHaveTextContent('Schon von Hand gebucht? „Brötchen“ vom 03.11.2026')
    // Archived categories are not offered
    expect(within(shop).queryByRole('option', { name: 'Alt' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '4 Buchungen übernehmen' })).toBeDisabled()
    expect(screen.getByText('2 Zeilen fehlt noch die Kategorie.')).toBeInTheDocument()
  })

  // R28, US35: a row that replaces a planned occurrence says so and can be unticked
  it('replaces the found occurrence unless the user unticks it', async () => {
    const fetchMock = renderPage()
    await upload()
    const kino = row('Kino Nord')[0]
    const replace = within(kino).getByRole('checkbox', { name: /ersetzt „Kino-Abo“ vom 03.11.2026/ })
    expect(replace).toBeChecked()
    fireEvent.click(within(row('Spiele Shop')[0]).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.click(screen.getByRole('button', { name: '4 Buchungen übernehmen' }))
    await screen.findByRole('status')
    let body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.rows[0].replaces).toEqual({ recurringId: 3, date: '2026-11-03' })

    fetchMock.mockClear()
    await upload()
    fireEvent.click(within(row('Kino Nord')[0]).getByRole('checkbox', { name: /ersetzt „Kino-Abo“/ }))
    fireEvent.click(within(row('Spiele Shop')[0]).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.click(screen.getByRole('button', { name: '4 Buchungen übernehmen' }))
    await screen.findByRole('status')
    body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.rows[0]).not.toHaveProperty('replaces')
  })

  // After the first test (07.11.2026): alphabetical categories and a new category right from the row
  it('lists the categories alphabetically with a choice for a new one at the end', async () => {
    renderPage()
    await upload()

    const select = within(row('Kino Nord')[0]).getByLabelText('Kategorie für Kino Nord')
    expect(within(select).getAllByRole('option').map((o) => o.textContent)).toEqual([
      'Kategorie wählen …',
      'Umbuchung (zählt nicht)',
      'Freizeit',
      'Wohnen',
      '+ Neue Kategorie …',
    ])
  })

  it('creates a new category from a row and chooses it for the rows with the same key', async () => {
    const fetchMock = renderPage()
    await upload()
    const [first, second] = row('Spiele Shop')

    fireEvent.change(within(first).getByLabelText('Kategorie für Spiele Shop'), { target: { value: 'new-category' } })
    fireEvent.change(within(first).getByLabelText('Neue Kategorie für Spiele Shop'), { target: { value: 'Spiele' } })
    fireEvent.click(within(first).getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(within(first).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('50'))
    expect(within(second).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('50')
    expect(within(first).queryByLabelText('Neue Kategorie für Spiele Shop')).not.toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/categories' && init?.method === 'POST')
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ name: 'Spiele' })
  })

  it('shows why a new category cannot be created and keeps the row open', async () => {
    renderPage()
    await upload()
    const [first] = row('Spiele Shop')

    fireEvent.change(within(first).getByLabelText('Kategorie für Spiele Shop'), { target: { value: 'new-category' } })
    fireEvent.click(within(first).getByRole('button', { name: 'Anlegen' }))
    expect(within(first).getByRole('alert')).toHaveTextContent('Der Name ist Pflicht.')

    fireEvent.change(within(first).getByLabelText('Neue Kategorie für Spiele Shop'), { target: { value: 'Wohnen' } })
    fireEvent.click(within(first).getByRole('button', { name: 'Anlegen' }))
    expect(await within(first).findByText(/gibt es schon/)).toBeInTheDocument()
    expect(within(first).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('')
  })

  it('takes a chosen category over to the open rows with the same key', async () => {
    renderPage()
    await upload()

    const [first, second] = row('Spiele Shop')
    fireEvent.click(within(first).getByRole('button', { name: /Vorschlag: Freizeit/ }))

    expect(within(second).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('8')
    expect(screen.getByRole('button', { name: '4 Buchungen übernehmen' })).toBeEnabled()
  })

  it('shows only the rows without category in the view Ohne Kategorie', async () => {
    renderPage()
    await upload()

    fireEvent.click(screen.getByRole('button', { name: 'Ohne Kategorie (2)' }))
    expect(within(screen.getByRole('list', { name: 'Neue Zeilen' })).getAllByRole('listitem')).toHaveLength(2)
    fireEvent.click(screen.getByRole('button', { name: 'Nicht importiert (2)' }))
    const skipped = screen.getByRole('list', { name: 'Nicht importierte Zeilen' })
    expect(skipped).toHaveTextContent('schon importiert')
    expect(skipped).toHaveTextContent('vorgemerkt (noch nicht gebucht)')
  })

  it('sends the chosen rows with their accounts and shows the result', async () => {
    const fetchMock = renderPage()
    await upload()
    fireEvent.click(within(row('Spiele Shop')[0]).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.change(screen.getByLabelText('Ausgabenkonto in der Datei'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: '4 Buchungen übernehmen' }))

    expect(await screen.findByRole('status')).toHaveTextContent('4 Buchungen übernommen.')
    expect(screen.getByRole('link', { name: 'In den Transaktionen ansehen' })).toHaveAttribute(
      'href',
      '#/transaktionen?von=2026-11-04&bis=2026-11-04',
    )
    const body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.profile).toBe('C24')
    expect(body.sources).toEqual([
      { key: 'C24:Ausgabenkonto', accountId: 2 },
      { key: 'C24:Pocket Urlaub', newAccountName: 'C24 Pocket Urlaub' },
    ])
    expect(body.rows).toHaveLength(4)
    expect(body.rows[0]).toEqual({
      fingerprint: 'a'.repeat(64), source: 'C24:Ausgabenkonto', date: '2026-11-04', amount: -2.99,
      description: 'Kino Nord', counterparty: 'Spiele Shop', bankCategory: null, categoryId: 8, transfer: false,
      replaces: { recurringId: 3, date: '2026-11-03' },
    })
    expect(body.rows[3]).toMatchObject({ description: 'Auf Pocket', categoryId: null, transfer: true, transferPeerId: 77 })
    expect(body.rows.map((r: { description: string }) => r.description)).not.toContain('Bäcker')
  })

  it('shows why a file cannot be read', async () => {
    renderPage(() =>
      json({ status: 400, errors: [{ field: 'file', message: 'Das Format der Datei ist unbekannt.' }] }, 400),
    )
    const file = new File(['x'], 'x.csv', { type: 'text/csv' })
    fireEvent.change(screen.getByLabelText('CSV-Datei wählen'), { target: { files: [file] } })

    expect(await screen.findByRole('alert')).toHaveTextContent('Das Format der Datei ist unbekannt.')
  })

  // US34
  it('lists earlier imports and undoes one after asking', async () => {
    const fetchMock = renderPage()

    const batch = await screen.findByRole('listitem', { name: /Import vom/ })
    expect(batch).toHaveTextContent('N26 · 12 Buchungen')
    expect(within(batch).getByRole('link', { name: '01.10.2026 bis 31.10.2026' })).toHaveAttribute(
      'href',
      '#/transaktionen?von=2026-10-01&bis=2026-10-31',
    )
    fireEvent.click(within(batch).getByRole('button', { name: 'Rückgängig' }))
    fireEvent.click(within(batch).getByRole('button', { name: 'Endgültig löschen' }))

    await waitFor(() =>
      expect(fetchMock.mock.calls.some(([url, init]) => url === '/api/imports/4' && init?.method === 'DELETE')).toBe(true),
    )
  })
})
