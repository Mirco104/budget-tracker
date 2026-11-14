import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { ImportBatch, ImportEntry, ImportPreview } from '../api/imports'
import type { BankAccount, Category } from '../api/types'
import { ImportPage } from './ImportPage'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  // Alphabetical, as the backend sends them (decision 38)
  { id: 9, name: 'Alt', icon: 'tag', archived: true, incomeOnly: false, parentId: null, path: 'Alt' },
  { id: 8, name: 'Freizeit', icon: 'music', archived: false, incomeOnly: false, parentId: null, path: 'Freizeit' },
  { id: 81, name: 'Kino', icon: 'music', archived: false, incomeOnly: false, parentId: 8, path: 'Freizeit › Kino' },
  { id: 1, name: 'Wohnen', icon: 'home', archived: false, incomeOnly: false, parentId: null, path: 'Wohnen' },
]
const accounts: BankAccount[] = [
  { id: 1, name: 'Girokonto', iban: null, defaultAccount: true, archived: false, used: true },
  { id: 2, name: 'C24 Ausgabenkonto', iban: null, defaultAccount: false, archived: false, used: true },
]

function entry(values: Partial<ImportEntry>): ImportEntry {
  return {
    line: 2, status: 'NEW', reason: null, fingerprint: 'f'.repeat(64), source: 'C24:Ausgabenkonto', date: '2026-11-04',
    amount: -2.99, description: 'Spiele Shop', counterparty: 'Spiele Shop', bankCategory: null, suggestionKey: 'spiele shop',
    categoryId: null, certain: false, transfer: false, transferCertain: false, transferPeerId: null, transferPeer: null, replaces: null, duplicateOf: null, details: null, mappedCategoryId: null,
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
    entry({ line: 5, fingerprint: 'd'.repeat(64), description: 'Auf Pocket', amount: -100, source: 'C24:Pocket Urlaub', suggestionKey: 'auf pocket', transfer: true, transferPeerId: 77, transferPeer: { line: null, date: '2026-11-04', description: 'Von Ausgabenkonto', amount: 100, accountName: 'Girokonto' } }),
    entry({ line: 6, fingerprint: 'e'.repeat(64), status: 'POSSIBLE_DUPLICATE', description: 'Bäcker', suggestionKey: 'bäcker', amount: -3.1, duplicateOf: { id: 5, date: '2026-11-03', description: 'Brötchen' } }),
    entry({ line: 7, fingerprint: '1'.repeat(64), status: 'DUPLICATE', reason: 'schon importiert', description: 'Alt' }),
    { ...entry({ line: 8 }), status: 'SKIPPED', reason: 'vorgemerkt (noch nicht gebucht)', fingerprint: null, description: null, amount: null, date: null },
  ],
  series: [],
  bankCategories: [],
}

const batches: ImportBatch[] = [
  { id: 4, profile: 'N26', profileLabel: 'N26', createdAt: '2026-11-07T20:15:00Z', firstDate: '2026-10-01', lastDate: '2026-10-31', count: 12 },
]

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderPage(previewAnswer: () => Promise<Response> = () => json(preview)) {
  let currentCategories = categories
  let currentAccounts = accounts
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
    if (url === '/api/bank-accounts' && init?.method === 'POST') {
      const { name } = JSON.parse(String(init.body)) as { name: string }
      if (currentAccounts.some((a) => a.name.toLowerCase() === name.toLowerCase())) {
        return json({ status: 409, detail: `Ein Konto mit dem Namen "${name}" gibt es schon.` }, 409)
      }
      const created: BankAccount = { id: 7, name, iban: null, defaultAccount: false, archived: false, used: false }
      currentAccounts = [...currentAccounts, created]
      return json(created, 201)
    }
    if (url.startsWith('/api/bank-accounts')) {
      return json(currentAccounts)
    }
    if (url === '/api/recurring-transactions' && init?.method === 'POST') {
      return json({ id: 9 }, 201)
    }
    if (url === '/api/bank-category-mappings' && init?.method === 'PUT') {
      const body = JSON.parse(String(init.body)) as { mappings: { bankCategory: string; categoryId?: number }[] }
      return json(body.mappings.map((m) => ({ bankCategory: m.bankCategory, categoryId: m.categoryId ?? 50 })))
    }
    if (url === '/api/categories' && init?.method === 'POST') {
      const { name, parentId = null } = JSON.parse(String(init.body)) as { name: string; parentId?: number | null }
      if (name.toLowerCase() === 'wohnen') {
        return json({ status: 409, detail: 'Eine Kategorie mit dem Namen "Wohnen" gibt es schon.' }, 409)
      }
      const parent = currentCategories.find((c) => c.id === parentId)
      const created: Category = {
        ...categories[2],
        id: 50,
        name,
        path: parent ? `${parent.name} › ${name}` : name,
        parentId,
        icon: 'tag',
      }
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

// The import starts with the kind filter Einmalig (ticket #84); most tests look at every row, so they choose Alle Arten
async function upload({ allKinds = true }: { allKinds?: boolean } = {}) {
  const file = new File(['Transaktionstyp,Buchungsdatum'], 'export.csv', { type: 'text/csv' })
  fireEvent.change(screen.getByLabelText('CSV-Datei wählen'), { target: { files: [file] } })
  await screen.findByRole('region', { name: 'Datei' })
  const kind = screen.queryByLabelText('Art filtern')
  if (allKinds && kind) {
    fireEvent.change(kind, { target: { value: '' } })
  }
}

function row(name: string) {
  return within(screen.getByRole('list', { name: 'Neue Zeilen' })).getAllByRole('listitem', { name })
}

function sent(fetchMock: ReturnType<typeof vi.fn>, url: string) {
  const call = fetchMock.mock.calls.find(([u, init]) => u === url && (init as RequestInit | undefined)?.method === 'POST')
  return call ? (call[1] as RequestInit) : undefined
}

describe('ImportPage', () => {
  it('names the supported banks, the Sparkasse included (decision 47)', () => {
    renderPage()

    expect(screen.getByRole('region', { name: 'Datei wählen' })).toHaveTextContent(
      'Unterstützt werden C24, Trade Republic, N26 und die Sparkasse (alle vier CSV-Exporte).',
    )
  })

  it('uploads the file as multipart and shows the summary and the accounts of the file', async () => {
    const fetchMock = renderPage()
    await upload()

    const init = sent(fetchMock, '/api/imports/preview')
    expect(init?.body).toBeInstanceOf(FormData)
    expect(new Headers(init?.headers).has('Content-Type')).toBe(false)
    const summary = screen.getByRole('region', { name: 'Datei' })
    expect(summary).toHaveTextContent('7 Zeilen: 5 neu, 1 schon importiert, 1 nicht importierbar')
    expect(within(summary).getByLabelText('„Ausgabenkonto“ in der Datei')).toHaveValue('new')
    expect(within(summary).getByRole('option', { name: '+ Neues Konto anlegen: C24 Ausgabenkonto' })).toBeInTheDocument()
  })

  // US33: certain suggestions and transfers are preset, uncertain ones are offered, possible duplicates unticked
  // Decision 46: nothing is preset; every suggestion, also a transfer, is a button next to the field
  it('presets nothing and offers each suggestion as a button', async () => {
    renderPage()
    await upload()

    const kino = row('Kino Nord')[0]
    expect(within(kino).getByLabelText('Kategorie für Kino Nord')).toHaveValue('')
    expect(within(kino).getByRole('button', { name: /Vorschlag: Freizeit/ })).toBeInTheDocument()
    const pocket = row('Auf Pocket')[0]
    expect(within(pocket).getByLabelText('Kategorie für Auf Pocket')).toHaveValue('')
    expect(pocket).toHaveTextContent('Umbuchung erkannt')
    fireEvent.click(within(pocket).getByRole('button', { name: 'Vorschlag: Umbuchung' }))
    expect(within(pocket).getByLabelText('Kategorie für Auf Pocket')).toHaveValue('transfer')
    expect(pocket).toHaveTextContent('Gegenbuchung wird ebenfalls zur Umbuchung')
    const baker = row('Bäcker')[0]
    expect(within(baker).getByLabelText('Bäcker importieren')).not.toBeChecked()
    expect(baker).toHaveTextContent('Schon von Hand gebucht? „Brötchen“ vom 03.11.2026')
    expect(within(row('Spiele Shop')[0]).queryByRole('option', { name: 'Alt' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '4 Buchungen übernehmen' })).toBeDisabled()
    expect(screen.getByText('3 Zeilen fehlt noch die Kategorie.')).toBeInTheDocument()
  })

  it('takes over every shown suggestion with one button', async () => {
    renderPage()
    await upload()

    fireEvent.click(screen.getByRole('button', { name: 'Alle Vorschläge übernehmen (3)' }))

    expect(within(row('Kino Nord')[0]).getByLabelText('Kategorie für Kino Nord')).toHaveValue('8')
    for (const shop of row('Spiele Shop')) {
      expect(within(shop).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('8')
    }
    expect(screen.getByRole('button', { name: 'Alle Vorschläge übernehmen (0)' })).toBeDisabled()
    // Code review 08.11.2026: an uncertain transfer is only set by its own button
    const pocket = row('Auf Pocket')[0]
    expect(within(pocket).getByLabelText('Kategorie für Auf Pocket')).toHaveValue('')
    expect(screen.getByRole('button', { name: '4 Buchungen übernehmen' })).toBeDisabled()
    fireEvent.click(within(pocket).getByRole('button', { name: 'Vorschlag: Umbuchung' }))
    expect(screen.getByRole('button', { name: '4 Buchungen übernehmen' })).toBeEnabled()
  })

  it('names the counterpart of an uncertain transfer', async () => {
    renderPage()
    await upload()

    expect(row('Auf Pocket')[0]).toHaveTextContent('Gegenbuchung: „Von Ausgabenkonto“, +100,00 € vom 04.11.2026 (Girokonto)')
    // A stored booking cannot be jumped to
    expect(within(row('Auf Pocket')[0]).queryByRole('button', { name: /Gegenbuchung/ })).not.toBeInTheDocument()
  })

  // A counterpart in the same file is named like a booking and can be jumped to (no line numbers on the page)
  function previewWithPeerInFile(): ImportPreview {
    return {
      ...preview,
      entries: preview.entries.map((e) =>
        e.line === 5
          ? { ...e, transferPeer: { line: 2, date: '2026-11-04', description: 'Kino Nord', amount: -2.99, accountName: 'Ausgabenkonto' } }
          : e,
      ),
    }
  }

  it('names a counterpart in the same file and jumps to it', async () => {
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    renderPage(() => json(previewWithPeerInFile()))
    await upload()

    const hint = within(row('Auf Pocket')[0]).getByRole('button', {
      // The amount has a non-breaking space before €
      name: /^Gegenbuchung: „Kino Nord“, −2,99\s€ vom 04\.11\.2026 \(Ausgabenkonto, in dieser Datei\)$/,
    })
    fireEvent.click(hint)

    expect(row('Kino Nord')[0]).toHaveClass('highlight')
    expect(scrollIntoView).toHaveBeenCalled()
  })

  it('shows a counterpart that has its category with the filter Ohne Kategorie kept', async () => {
    Element.prototype.scrollIntoView = vi.fn()
    renderPage(() => json(previewWithPeerInFile()))
    await upload()
    fireEvent.click(within(row('Kino Nord')[0]).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.change(screen.getByLabelText('Kategorie filtern'), { target: { value: 'open' } })
    expect(screen.queryByRole('listitem', { name: 'Kino Nord' })).not.toBeInTheDocument()

    fireEvent.click(within(row('Auf Pocket')[0]).getByRole('button', { name: /Gegenbuchung/ }))

    expect(screen.getByLabelText('Kategorie filtern')).toHaveValue('open')
    expect(row('Kino Nord')[0]).toHaveClass('highlight')
  })

  // R28, US35: a row that replaces a planned occurrence says so and can be unticked
  it('replaces the found occurrence unless the user unticks it', async () => {
    const fetchMock = renderPage()
    await upload()
    const replace = within(row('Kino Nord')[0]).getByRole('checkbox', { name: /erfüllt „Kino-Abo“ vom 03.11.2026/ })
    expect(replace).toBeChecked()
    fireEvent.click(screen.getByRole('button', { name: /Alle Vorschläge übernehmen/ }))
    fireEvent.click(within(row('Auf Pocket')[0]).getByRole('button', { name: 'Vorschlag: Umbuchung' }))
    fireEvent.click(screen.getByRole('button', { name: '4 Buchungen übernehmen' }))
    await screen.findByRole('status')
    let body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.rows[0].replaces).toEqual({ recurringId: 3, date: '2026-11-03' })

    fetchMock.mockClear()
    await upload()
    fireEvent.click(within(row('Kino Nord')[0]).getByRole('checkbox', { name: /erfüllt „Kino-Abo“/ }))
    fireEvent.click(screen.getByRole('button', { name: /Alle Vorschläge übernehmen/ }))
    fireEvent.click(within(row('Auf Pocket')[0]).getByRole('button', { name: 'Vorschlag: Umbuchung' }))
    fireEvent.click(screen.getByRole('button', { name: '4 Buchungen übernehmen' }))
    await screen.findByRole('status')
    body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.rows[0]).not.toHaveProperty('replaces')
  })

  // R28, US33: rows that replace occurrences of the same series are taken over with one click (08.11.2026)
  function rent(line: number, date: string, values: Partial<ImportEntry> = {}): ImportEntry {
    return entry({
      line, fingerprint: String(line).repeat(64), date, amount: -500, description: `Miete ${date}`, suggestionKey: 'miete',
      categoryId: 1, certain: true, replaces: { recurringId: 5, date, description: 'Miete', amount: 500, categoryId: 1 },
      ...values,
    })
  }

  const previewWithRent: ImportPreview = {
    ...preview,
    entries: [
      rent(2, '2026-01-01'),
      rent(3, '2026-02-01'),
      rent(4, '2026-03-01'),
      rent(5, '2026-04-01', { status: 'POSSIBLE_DUPLICATE', duplicateOf: { id: 5, date: '2026-04-01', description: 'Miete' } }),
      { ...preview.entries[0], line: 9 },
    ],
  }

  function matchCard() {
    return screen.getByRole('region', { name: 'Treffer für „Miete“' })
  }

  it('takes over every row that replaces an occurrence of the same series', async () => {
    const fetchMock = renderPage(() => json(previewWithRent))
    await upload({ allKinds: false })

    expect(matchCard()).toHaveTextContent(
      '3 Buchungen erfüllen die geplanten Fixkosten „Miete“ (Wohnen) vom 01.01.2026 bis 01.03.2026.',
    )
    // A series with a single match has no card
    expect(screen.queryByRole('region', { name: /Kino-Abo/ })).not.toBeInTheDocument()
    // Rows that replace an occurrence are Wiederkehrend, so the start filter Einmalig does not show them
    expect(screen.queryByRole('listitem', { name: 'Miete 2026-01-01' })).not.toBeInTheDocument()

    fireEvent.click(within(matchCard()).getByRole('button', { name: 'Alle übernehmen (3)' }))

    expect(matchCard()).toHaveTextContent('Alle 3 erfüllen ihren Termin (Wohnen).')
    expect(within(matchCard()).queryByRole('button', { name: /Alle übernehmen/ })).not.toBeInTheDocument()
    fireEvent.click(within(matchCard()).getByRole('button', { name: 'In der Liste zeigen' }))
    expect(screen.getByLabelText('Art filtern')).toHaveValue('RECURRING')
    for (const name of ['Miete 2026-01-01', 'Miete 2026-02-01', 'Miete 2026-03-01']) {
      expect(within(row(name)[0]).getByRole('combobox', { name: `Kategorie für ${name}` })).toHaveValue('1')
    }
    // The possible duplicate stays unticked, the other series stays open
    expect(within(row('Miete 2026-04-01')[0]).getByRole('checkbox', { name: 'Miete 2026-04-01 importieren' })).not.toBeChecked()
    expect(within(row('Kino Nord')[0]).getByRole('combobox', { name: 'Kategorie für Kino Nord' })).toHaveValue('')

    fireEvent.click(within(row('Kino Nord')[0]).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.click(screen.getByRole('button', { name: '4 Buchungen übernehmen' }))
    await screen.findByRole('status')
    const body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.rows.slice(0, 3)).toEqual([
      expect.objectContaining({ categoryId: 1, replaces: { recurringId: 5, date: '2026-01-01' } }),
      expect.objectContaining({ categoryId: 1, replaces: { recurringId: 5, date: '2026-02-01' } }),
      expect.objectContaining({ categoryId: 1, replaces: { recurringId: 5, date: '2026-03-01' } }),
    ])
  })

  it('opens the rows of a match again with Zurücknehmen', async () => {
    renderPage(() => json(previewWithRent))
    await upload()
    fireEvent.click(within(matchCard()).getByRole('button', { name: 'Alle übernehmen (3)' }))

    fireEvent.click(within(matchCard()).getByRole('button', { name: 'Zurücknehmen' }))

    for (const name of ['Miete 2026-01-01', 'Miete 2026-02-01', 'Miete 2026-03-01']) {
      expect(within(row(name)[0]).getByRole('combobox', { name: `Kategorie für ${name}` })).toHaveValue('')
      expect(within(row(name)[0]).getByRole('checkbox', { name: /erfüllt „Miete“/ })).toBeChecked()
    }
    expect(within(matchCard()).getByRole('button', { name: 'Alle übernehmen (3)' })).toBeInTheDocument()
    expect(within(matchCard()).queryByRole('button', { name: 'Zurücknehmen' })).not.toBeInTheDocument()
  })

  it('counts only the open rows of a match', async () => {
    renderPage(() => json(previewWithRent))
    await upload()

    fireEvent.change(within(row('Miete 2026-01-01')[0]).getByRole('combobox', { name: 'Kategorie für Miete 2026-01-01' }), {
      target: { value: '1' },
    })
    fireEvent.click(within(row('Miete 2026-02-01')[0]).getByRole('checkbox', { name: /erfüllt „Miete“/ }))

    fireEvent.click(within(matchCard()).getByRole('button', { name: 'Alle übernehmen (2)' }))
    expect(within(row('Miete 2026-02-01')[0]).getByRole('checkbox', { name: /erfüllt „Miete“/ })).toBeChecked()
    expect(within(row('Miete 2026-03-01')[0]).getByRole('combobox', { name: 'Kategorie für Miete 2026-03-01' })).toHaveValue('1')
  })

  it('asks for a category per row when the category of the series is archived', async () => {
    renderPage(() =>
      json({
        ...previewWithRent,
        entries: previewWithRent.entries.map((e) =>
          e.replaces?.recurringId === 5 ? { ...e, categoryId: 9, replaces: { ...e.replaces, categoryId: 9 } } : e,
        ),
      }),
    )
    await upload()

    expect(matchCard()).toHaveTextContent('Die Kategorie des Postens ist archiviert; bitte wähle je Zeile eine.')
    expect(within(matchCard()).queryByRole('button', { name: /Alle übernehmen/ })).not.toBeInTheDocument()
  })

  // Decision 46: big files are taken over in parts; the rest stays in the preview
  it('takes over the finished rows and keeps the rest', async () => {
    const fetchMock = renderPage()
    await upload()
    fireEvent.click(within(row('Kino Nord')[0]).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Fertige übernehmen (1)' }))

    expect(await screen.findByRole('status')).toHaveTextContent('übernommen; die übrigen Zeilen bleiben hier.')
    const body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.rows.map((r: { description: string }) => r.description)).toEqual(['Kino Nord'])
    expect(screen.queryByRole('listitem', { name: 'Kino Nord' })).not.toBeInTheDocument()
    expect(row('Spiele Shop')).toHaveLength(2)
    expect(screen.getByRole('region', { name: 'Datei' })).toHaveTextContent('4 neu, 2 schon importiert')
  })

  it('closes the preview with Abbrechen at the top', async () => {
    renderPage()
    await upload()

    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(screen.queryByRole('region', { name: 'Datei' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('CSV-Datei wählen')).toBeInTheDocument()
  })

  // R31, code review 08.11.2026: a series is only noted in the preview and created with the import
  it('notes a suggested series and sends it with the import', async () => {
    const withSeries: ImportPreview = {
      ...preview,
      series: [{ lines: [3, 4], startDate: '2026-08-01', amount: -4.99, lowest: -4.99, highest: -4.99, description: 'Spiele Shop', source: 'C24:Ausgabenkonto', categoryId: 8 }],
    }
    const fetchMock = renderPage(() => json(withSeries))
    await upload({ allKinds: false })

    const card = screen.getByRole('region', { name: 'Fixkosten „Spiele Shop“' })
    expect(card).toHaveTextContent('Sieht nach monatlichen Fixkosten aus: „Spiele Shop“, −4,99 € ab 01.08.2026 (2 Buchungen in der Datei).')
    expect(within(card).getByRole('button', { name: 'Als Fixkosten planen' })).toBeDisabled()
    fireEvent.click(within(card).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.click(within(card).getByRole('button', { name: 'Als Fixkosten planen' }))

    expect(sent(fetchMock, '/api/recurring-transactions')).toBeUndefined()
    // Issue #119: no hint text, only the way back
    expect(within(card).getByRole('button', { name: 'Nicht planen' })).toBeInTheDocument()
    // Its rows are Wiederkehrend now, so the start filter Einmalig no longer shows them; the card shows them in the list
    expect(screen.queryByRole('listitem', { name: 'Spiele Shop' })).not.toBeInTheDocument()
    // The two series rows and Kino Nord (replaces an occurrence)
    expect(screen.getByText('Davon sind 3 gewählte Zeilen vom Filter ausgeblendet.')).toBeInTheDocument()
    fireEvent.click(within(card).getByRole('button', { name: 'In der Liste zeigen' }))
    expect(row('Spiele Shop')).toHaveLength(2)

    fireEvent.click(within(row('Kino Nord')[0]).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.change(screen.getByLabelText('Art filtern'), { target: { value: '' } })
    fireEvent.click(within(row('Auf Pocket')[0]).getByRole('button', { name: 'Vorschlag: Umbuchung' }))
    fireEvent.click(screen.getByRole('button', { name: '4 Buchungen übernehmen' }))
    await screen.findByRole('status')

    const body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.series).toEqual([
      { startDate: '2026-08-01', amount: -4.99, description: 'Spiele Shop', categoryId: 8, source: 'C24:Ausgabenkonto' },
    ])
    expect(body.rows.map((r: { series?: number }) => r.series)).toEqual([undefined, 0, 0, undefined])
    expect(sent(fetchMock, '/api/recurring-transactions')).toBeUndefined()
  })

  it('drops a noted series again and creates nothing on Abbrechen', async () => {
    const fetchMock = renderPage(() =>
      json({ ...preview, series: [{ lines: [3, 4], startDate: '2026-08-01', amount: -4.99, lowest: -4.99, highest: -4.99, description: 'Spiele Shop', source: 'C24:Ausgabenkonto', categoryId: 8 }] }),
    )
    await upload({ allKinds: false })
    const card = screen.getByRole('region', { name: 'Fixkosten „Spiele Shop“' })
    fireEvent.click(within(card).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.click(within(card).getByRole('button', { name: 'Als Fixkosten planen' }))

    expect(screen.queryByRole('listitem', { name: 'Spiele Shop' })).not.toBeInTheDocument()
    fireEvent.click(within(card).getByRole('button', { name: 'Nicht planen' }))
    expect(within(card).getByRole('button', { name: 'Als Fixkosten planen' })).toBeInTheDocument()
    // The rows come back with the category of the series
    expect(row('Spiele Shop')).toHaveLength(2)
    expect(within(row('Spiele Shop')[0]).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('8')

    fireEvent.click(within(card).getByRole('button', { name: 'Als Fixkosten planen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(sent(fetchMock, '/api/imports')).toBeUndefined()
    expect(sent(fetchMock, '/api/recurring-transactions')).toBeUndefined()
    expect(screen.getByLabelText('CSV-Datei wählen')).toBeInTheDocument()
  })

  it('reads the file again after taking over the finished rows of a noted series', async () => {
    const withSeries: ImportPreview = {
      ...preview,
      series: [{ lines: [3, 4], startDate: '2026-08-01', amount: -4.99, lowest: -4.99, highest: -4.99, description: 'Spiele Shop', source: 'C24:Ausgabenkonto', categoryId: 8 }],
    }
    let calls = 0
    const fetchMock = renderPage(() => {
      calls++
      return json(calls === 1 ? withSeries : preview)
    })
    await upload()
    const card = screen.getByRole('region', { name: 'Fixkosten „Spiele Shop“' })
    fireEvent.click(within(card).getByRole('button', { name: /Vorschlag: Freizeit/ }))
    fireEvent.click(within(card).getByRole('button', { name: 'Als Fixkosten planen' }))

    fireEvent.click(screen.getByRole('button', { name: 'Fertige übernehmen (2)' }))

    await waitFor(() => expect(calls).toBe(2))
    const body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.series).toHaveLength(1)
    expect(body.rows.map((r: { series?: number }) => r.series)).toEqual([0, 0])
    await waitFor(() => expect(screen.queryByRole('region', { name: 'Fixkosten „Spiele Shop“' })).not.toBeInTheDocument())
  })

  // Issue #119: an income series is named like its group of the planning page and shows how much the rows vary
  it('names an income series Einkommen and shows the range of its amounts', async () => {
    renderPage(() =>
      json({
        ...preview,
        series: [{ lines: [3, 4], startDate: '2026-08-01', amount: 2465, lowest: 2410, highest: 2530, description: 'Firma Beispiel', source: 'C24:Ausgabenkonto', categoryId: null }],
      }),
    )
    await upload()

    const card = screen.getByRole('region', { name: 'Einkommen „Firma Beispiel“' })
    expect(card).toHaveTextContent(
      'Sieht nach monatlichem Einkommen aus: „Firma Beispiel“, +2.465,00 € ab 01.08.2026 (2 Buchungen in der Datei, schwankt zwischen +2.410,00 € und +2.530,00 €).',
    )
    expect(within(card).getByRole('button', { name: 'Als Einkommen planen' })).toBeDisabled()
  })

  // Issue #119: the details of a row are folded away; a field the bank does not deliver is left out
  it('shows the details of a row on request', async () => {
    renderPage(() =>
      json({
        ...preview,
        entries: [
          entry({
            line: 2, fingerprint: 'a'.repeat(64), description: 'Beispiel Verein', counterparty: 'Beispiel Verein e.V.',
            details: { counterpartyIban: 'GB82WEST12345698765432', purpose: 'Beitrag 11/2026 Mitglied 4711', kind: 'FOLGELASTSCHRIFT', valueDate: '2026-11-05', mandateId: 'M-0001', creditorId: null },
          }),
          entry({ line: 3, fingerprint: 'b'.repeat(64), description: 'Ohne Details', counterparty: null, details: null }),
        ],
      }),
    )
    await upload()

    const club = row('Beispiel Verein')[0]
    const toggle = within(club).getByRole('button', { name: 'Details zu Beispiel Verein' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(within(club).queryByText('Mandatsreferenz')).not.toBeInTheDocument()

    fireEvent.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    const terms = within(club).getAllByRole('term').map((term) => term.textContent)
    expect(terms).toEqual(['Gegenseite', 'IBAN', 'Verwendungszweck', 'Art', 'Wertstellung', 'Mandatsreferenz'])
    expect(within(club).getByText('GB82 WEST 1234 5698 7654 32')).toBeInTheDocument()
    expect(within(club).getByText('05.11.2026')).toBeInTheDocument()
    expect(within(row('Ohne Details')[0]).queryByRole('button', { name: /Details/ })).not.toBeInTheDocument()
  })

  it('hides an ignored series', async () => {
    renderPage(() =>
      json({ ...preview, series: [{ lines: [3, 4], startDate: '2026-08-01', amount: -4.99, lowest: -4.99, highest: -4.99, description: 'Spiele Shop', source: 'C24:Ausgabenkonto', categoryId: null }] }),
    )
    await upload()

    fireEvent.click(screen.getByRole('button', { name: 'Ignorieren' }))

    expect(screen.queryByRole('region', { name: 'Fixkosten „Spiele Shop“' })).not.toBeInTheDocument()
  })

  it('offers a new category at the end of the selection', async () => {
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

  it('creates a new category from a row and offers it to the rows with the same key', async () => {
    const fetchMock = renderPage()
    await upload()
    const [first, second] = row('Spiele Shop')

    fireEvent.change(within(first).getByLabelText('Kategorie für Spiele Shop'), { target: { value: 'new' } })
    fireEvent.change(within(first).getByLabelText('Neue Kategorie für Spiele Shop'), { target: { value: 'Spiele' } })
    fireEvent.click(within(first).getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(within(first).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('50'))
    expect(within(second).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('')
    expect(within(second).getByRole('button', { name: 'Vorschlag: Spiele' })).toBeInTheDocument()
    expect(within(first).queryByLabelText('Neue Kategorie für Spiele Shop')).not.toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/categories' && init?.method === 'POST')
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ name: 'Spiele' })
  })

  it('shows why a new category cannot be created and keeps the row open', async () => {
    renderPage()
    await upload()
    const [first] = row('Spiele Shop')

    fireEvent.change(within(first).getByLabelText('Kategorie für Spiele Shop'), { target: { value: 'new' } })
    fireEvent.click(within(first).getByRole('button', { name: 'Anlegen' }))
    expect(within(first).getByRole('alert')).toHaveTextContent('Der Name ist Pflicht.')

    fireEvent.change(within(first).getByLabelText('Neue Kategorie für Spiele Shop'), { target: { value: 'Wohnen' } })
    fireEvent.click(within(first).getByRole('button', { name: 'Anlegen' }))
    expect(await within(first).findByText(/gibt es schon/)).toBeInTheDocument()
    expect(within(first).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('')
  })

  // After the test (07.11.2026): a chosen category only suggests itself to the rows with the same key
  it('offers a chosen category to the open rows with the same key without setting it', async () => {
    renderPage()
    await upload()

    const [first, second] = row('Spiele Shop')
    fireEvent.change(within(first).getByLabelText('Kategorie für Spiele Shop'), { target: { value: '1' } })

    expect(within(second).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('')
    fireEvent.click(within(second).getByRole('button', { name: 'Vorschlag: Wohnen' }))
    expect(within(second).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('1')
  })

  // The bug from the test: a transfer chosen for one row also set the next row with the same counterparty
  it('never passes a chosen transfer on to other rows', async () => {
    renderPage()
    await upload()

    const [first, second] = row('Spiele Shop')
    fireEvent.change(within(first).getByLabelText('Kategorie für Spiele Shop'), { target: { value: 'transfer' } })

    expect(within(second).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('')
    expect(within(second).queryByRole('button', { name: 'Vorschlag: Umbuchung' })).not.toBeInTheDocument()
    expect(within(second).getByRole('button', { name: /Vorschlag: Freizeit/ })).toBeInTheDocument()
  })

  // Ticket #84: the same filters as Transaktionen, and the shown rows without category get one category at once
  function listed() {
    return within(screen.getByRole('list', { name: 'Neue Zeilen' }))
      .queryAllByRole('listitem')
      .map((item) => item.getAttribute('aria-label'))
  }

  it('filters the rows by description and resets the filter', async () => {
    renderPage()
    await upload()
    expect(screen.queryByRole('region', { name: 'Zuordnen' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'spiele' } })

    expect(listed()).toEqual(['Spiele Shop', 'Spiele Shop'])
    expect(screen.getByText('2 von 5 Zeilen')).toBeInTheDocument()
    // The options show their hits with the other filters; options without a hit are hidden
    const names = (label: string) =>
      Array.from((screen.getByLabelText(label) as HTMLSelectElement).options).map((option) => option.textContent)
    expect(names('Kategorie filtern')).toEqual(['Alle Kategorien', 'Ohne Kategorie (2)'])
    expect(names('Art filtern')).toEqual(['Alle Arten', 'Einmalig (2)'])
    expect(names('Konto filtern')).toEqual(['Alle Konten', 'Ausgabenkonto (2)'])
    fireEvent.change(within(row('Spiele Shop')[0]).getByLabelText('Kategorie für Spiele Shop'), { target: { value: '8' } })
    expect(names('Kategorie filtern')).toEqual(['Alle Kategorien', 'Ohne Kategorie (1)', 'Freizeit (1)'])

    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'nichts' } })
    expect(screen.getByText('Keine Zeilen für diesen Filter.')).toBeInTheDocument()
    // Back to the start filters: the search is empty and the kind is Einmalig again (Kino Nord replaces an occurrence)
    fireEvent.click(screen.getByRole('button', { name: 'Filter zurücksetzen' }))
    expect(screen.getByLabelText('Beschreibung suchen')).toHaveValue('')
    expect(screen.getByLabelText('Art filtern')).toHaveValue('SINGLE')
    expect(listed()).toEqual(['Spiele Shop', 'Spiele Shop', 'Auf Pocket', 'Bäcker'])
  })

  // After the test of ticket #84: the import starts with Einmalig instead of hiding the rows of the cards
  it('starts with the kind Einmalig and offers no reset for the start filters', async () => {
    renderPage()
    await upload({ allKinds: false })

    expect(screen.getByLabelText('Art filtern')).toHaveValue('SINGLE')
    expect(listed()).toEqual(['Spiele Shop', 'Spiele Shop', 'Auf Pocket', 'Bäcker'])
    expect(screen.getByText('4 von 5 Zeilen')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Filter zurücksetzen' })).not.toBeInTheDocument()
    // Kino Nord is chosen but hidden; it is taken over all the same
    expect(screen.getByText('Davon ist 1 gewählte Zeile vom Filter ausgeblendet.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Art filtern'), { target: { value: '' } })
    expect(listed()).toHaveLength(5)
    expect(screen.queryByText(/vom Filter ausgeblendet/)).not.toBeInTheDocument()
  })

  it('keeps an edited row in sight until the filters change', async () => {
    renderPage()
    await upload({ allKinds: false })
    fireEvent.change(screen.getByLabelText('Kategorie filtern'), { target: { value: 'open' } })

    fireEvent.click(within(row('Auf Pocket')[0]).getByRole('button', { name: 'Vorschlag: Umbuchung' }))

    // The row has a category now, but it stays while it is edited
    expect(row('Auf Pocket')[0]).toHaveTextContent('passt nicht zum Filter')
    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'a' } })
    expect(screen.queryByRole('listitem', { name: 'Auf Pocket' })).not.toBeInTheDocument()
  })

  it('keeps the filters when jumping to a counterpart and shows a hidden one', async () => {
    Element.prototype.scrollIntoView = vi.fn()
    renderPage(() => json(previewWithPeerInFile()))
    await upload({ allKinds: false })
    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'pocket' } })
    expect(listed()).toEqual(['Auf Pocket'])

    fireEvent.click(within(row('Auf Pocket')[0]).getByRole('button', { name: /Gegenbuchung/ }))

    expect(screen.getByLabelText('Beschreibung suchen')).toHaveValue('pocket')
    expect(listed()).toEqual(['Kino Nord', 'Auf Pocket'])
    expect(row('Kino Nord')[0]).toHaveClass('highlight')
    expect(row('Kino Nord')[0]).toHaveTextContent('passt nicht zum Filter')
  })

  it('filters the rows by the chosen kind, category and source', async () => {
    renderPage()
    await upload()
    fireEvent.click(within(row('Auf Pocket')[0]).getByRole('button', { name: 'Vorschlag: Umbuchung' }))

    // Umbuchung is no kind but a category option (ticket #84); Kino Nord replaces an occurrence (R28)
    expect(Array.from((screen.getByLabelText('Art filtern') as HTMLSelectElement).options).map((o) => o.value)).toEqual([
      '',
      'RECURRING',
      'SINGLE',
    ])
    fireEvent.change(screen.getByLabelText('Art filtern'), { target: { value: 'RECURRING' } })
    expect(listed()).toEqual(['Kino Nord'])
    fireEvent.change(screen.getByLabelText('Art filtern'), { target: { value: 'SINGLE' } })
    expect(listed()).toEqual(['Spiele Shop', 'Spiele Shop', 'Auf Pocket', 'Bäcker'])

    fireEvent.change(screen.getByLabelText('Art filtern'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Kategorie filtern'), { target: { value: 'transfer' } })
    expect(listed()).toEqual(['Auf Pocket'])

    fireEvent.change(screen.getByLabelText('Kategorie filtern'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Konto filtern'), { target: { value: 'C24:Pocket Urlaub' } })
    expect(listed()).toEqual(['Auf Pocket'])
  })

  it('gives the shown rows without category one category', async () => {
    const fetchMock = renderPage()
    await upload()
    const [first, second] = row('Spiele Shop')
    fireEvent.change(within(second).getByLabelText('Kategorie für Spiele Shop'), { target: { value: '1' } })
    fireEvent.click(within(row('Kino Nord')[0]).getByRole('checkbox', { name: 'Kino Nord importieren' }))
    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: 'o' } })

    const assign = screen.getByRole('region', { name: 'Zuordnen' })
    // Spiele Shop (first) and Auf Pocket have no category; Kino Nord is not chosen, the second Spiele Shop has one
    expect(assign).toHaveTextContent('Den 2 angezeigten Zeilen ohne Kategorie zuordnen:')
    fireEvent.change(within(assign).getByLabelText('Kategorie für die angezeigten Zeilen'), { target: { value: '8' } })
    fireEvent.click(within(assign).getByRole('button', { name: 'Zuordnen' }))

    expect(screen.getByRole('status')).toHaveTextContent('2 Zeilen „Freizeit“ zugeordnet.')
    expect(within(first).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('8')
    expect(within(second).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('1')
    expect(within(row('Auf Pocket')[0]).getByLabelText('Kategorie für Auf Pocket')).toHaveValue('8')
    expect(within(row('Kino Nord')[0]).getByLabelText('Kategorie für Kino Nord')).toHaveValue('')
    expect(screen.queryByRole('region', { name: 'Zuordnen' })).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Beschreibung suchen'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: '3 Buchungen übernehmen' }))
    await waitFor(() => expect(sent(fetchMock, '/api/imports')).toBeDefined())
    const body = JSON.parse(String(sent(fetchMock, '/api/imports')?.body))
    expect(body.rows.map((r: { categoryId: number | null }) => r.categoryId)).toEqual([8, 1, 8])
  })

  it('shows only the rows without category with the filter Ohne Kategorie', async () => {
    renderPage()
    await upload()

    fireEvent.change(screen.getByLabelText('Kategorie filtern'), { target: { value: 'open' } })
    expect(screen.getByRole('option', { name: 'Ohne Kategorie (4)' })).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Neue Zeilen' })).getAllByRole('listitem')).toHaveLength(4)
    fireEvent.click(screen.getByRole('button', { name: 'Nicht importiert (2)' }))
    const skipped = screen.getByRole('list', { name: 'Nicht importierte Zeilen' })
    expect(skipped).toHaveTextContent('schon importiert')
    expect(skipped).toHaveTextContent('vorgemerkt (noch nicht gebucht)')
  })

  // 08.11.2026: a category for a series can be created in its card
  it('creates a new category in the card of a series', async () => {
    const withSeries: ImportPreview = {
      ...preview,
      series: [{ lines: [3, 4], startDate: '2026-08-01', amount: -4.99, lowest: -4.99, highest: -4.99, description: 'Spiele Shop', source: 'C24:Ausgabenkonto', categoryId: null }],
    }
    renderPage(() => json(withSeries))
    await upload()
    const card = screen.getByRole('region', { name: 'Fixkosten „Spiele Shop“' })
    const select = within(card).getByLabelText('Kategorie für Fixkosten „Spiele Shop“')
    expect(within(select).getAllByRole('option').at(-1)).toHaveTextContent('+ Neue Kategorie …')

    fireEvent.change(select, { target: { value: 'new' } })
    fireEvent.change(within(card).getByLabelText('Neue Kategorie für Fixkosten „Spiele Shop“'), { target: { value: 'Spiele' } })
    fireEvent.click(within(card).getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(within(card).getByLabelText('Kategorie für Fixkosten „Spiele Shop“')).toHaveValue('50'))
    expect(within(card).getByRole('button', { name: 'Als Fixkosten planen' })).toBeEnabled()
  })

  // 08.11.2026: a new account for a source can get an own name; it is created right away and chosen
  it('creates an account with an own name for a source and sends it with the import', async () => {
    const fetchMock = renderPage()
    await upload()
    const source = screen.getByLabelText('„Pocket Urlaub“ in der Datei')

    fireEvent.change(source, { target: { value: 'new-named' } })
    const name = screen.getByLabelText('Name des neuen Kontos für Pocket Urlaub')
    expect(name).toHaveValue('C24 Pocket Urlaub')
    fireEvent.change(name, { target: { value: 'Urlaubskasse' } })
    fireEvent.click(within(screen.getByRole('region', { name: 'Datei' })).getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(screen.getByLabelText('„Pocket Urlaub“ in der Datei')).toHaveValue('7'))
    expect(JSON.parse(String(sent(fetchMock, '/api/bank-accounts')?.body))).toEqual({ name: 'Urlaubskasse' })

    fireEvent.click(screen.getByRole('button', { name: /Alle Vorschläge übernehmen/ }))
    fireEvent.click(within(row('Auf Pocket')[0]).getByRole('button', { name: 'Vorschlag: Umbuchung' }))
    fireEvent.click(screen.getByRole('button', { name: '4 Buchungen übernehmen' }))

    await screen.findByRole('status')
    expect(JSON.parse(String(sent(fetchMock, '/api/imports')?.body)).sources).toEqual([
      { key: 'C24:Ausgabenkonto', newAccountName: 'C24 Ausgabenkonto' },
      { key: 'C24:Pocket Urlaub', accountId: 7 },
    ])
  })

  it('shows why an account with an own name cannot be created', async () => {
    renderPage()
    await upload()

    fireEvent.change(screen.getByLabelText('„Pocket Urlaub“ in der Datei'), { target: { value: 'new-named' } })
    fireEvent.change(screen.getByLabelText('Name des neuen Kontos für Pocket Urlaub'), { target: { value: 'Girokonto' } })
    fireEvent.click(within(screen.getByRole('region', { name: 'Datei' })).getByRole('button', { name: 'Anlegen' }))

    expect(await screen.findByText(/Ein Konto mit dem Namen "Girokonto" gibt es schon./)).toBeInTheDocument()
    expect(screen.getByLabelText('„Pocket Urlaub“ in der Datei')).toHaveValue('new')
  })

  it('sends the chosen rows with their accounts and shows the result', async () => {
    const fetchMock = renderPage()
    await upload()
    fireEvent.click(screen.getByRole('button', { name: /Alle Vorschläge übernehmen/ }))
    fireEvent.click(within(row('Auf Pocket')[0]).getByRole('button', { name: 'Vorschlag: Umbuchung' }))
    fireEvent.change(screen.getByLabelText('„Ausgabenkonto“ in der Datei'), { target: { value: '2' } })
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

  // R33, US38: the card "Kategorien der Bank"
  describe('bank categories', () => {
    const withBankCategories: ImportPreview = {
      ...preview,
      bankCategories: [
        {
          bankCategory: 'Freizeit & Unterhaltung',
          parent: 'Freizeit & Unterhaltung',
          child: null,
          rows: 1,
          parentStatus: 'NEW',
          parentCategoryId: null,
          childStatus: null,
          childCategoryId: null,
          mappedCategoryId: null,
        },
      ],
    }

    it('maps a bank category to an own category and sets it at its rows only', async () => {
      const fetchMock = renderPage(() => json(withBankCategories))
      await upload()

      const card = screen.getByRole('region', { name: 'Kategorien der Bank' })
      expect(card).toHaveTextContent('Die Datei enthält 1 Kategorie der Bank: 1 wird neu angelegt, 0 deinen Kategorien zugeordnet.')
      fireEvent.change(within(card).getByLabelText('Wahl für Freizeit & Unterhaltung'), { target: { value: 'own' } })
      fireEvent.change(within(card).getByLabelText('Eigene Kategorie für Freizeit & Unterhaltung'), { target: { value: '8' } })
      expect(card).toHaveTextContent('0 werden neu angelegt, 1 deinen Kategorien zugeordnet.')
      fireEvent.click(within(card).getByRole('button', { name: 'Übernehmen' }))

      await waitFor(() => expect(screen.queryByRole('region', { name: 'Kategorien der Bank' })).not.toBeInTheDocument())
      const call = fetchMock.mock.calls.find(([u]) => u === '/api/bank-category-mappings')
      expect(JSON.parse(String((call?.[1] as RequestInit).body))).toEqual({
        profile: 'C24',
        mappings: [{ bankCategory: 'Freizeit & Unterhaltung', categoryId: 8 }],
      })
      const [withCategory, without] = row('Spiele Shop')
      expect(within(withCategory).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('8')
      expect(within(without).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('')
    })

    it('creates a new category with the name of the bank', async () => {
      const fetchMock = renderPage(() => json(withBankCategories))
      await upload()

      const card = screen.getByRole('region', { name: 'Kategorien der Bank' })
      expect(within(card).getByLabelText('Wahl für Freizeit & Unterhaltung')).toHaveValue('new')
      fireEvent.click(within(card).getByRole('button', { name: 'Übernehmen' }))

      await waitFor(() => expect(fetchMock.mock.calls.some(([u]) => u === '/api/bank-category-mappings')).toBe(true))
      const call = fetchMock.mock.calls.find(([u]) => u === '/api/bank-category-mappings')
      expect(JSON.parse(String((call?.[1] as RequestInit).body)).mappings).toEqual([
        { bankCategory: 'Freizeit & Unterhaltung', parentName: 'Freizeit & Unterhaltung' },
      ])
    })

    // Decision 62: a stored mapping presets the row
    it('presets the category of a row from a stored mapping and shows no card without new bank categories', async () => {
      renderPage(() =>
        json({
          ...preview,
          entries: preview.entries.map((e) => (e.line === 3 ? { ...e, mappedCategoryId: 8 } : e)),
          bankCategories: [{ ...withBankCategories.bankCategories[0], mappedCategoryId: 8 }],
        }),
      )
      await upload()

      expect(screen.queryByRole('region', { name: 'Kategorien der Bank' })).not.toBeInTheDocument()
      expect(within(row('Spiele Shop')[0]).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('8')
    })
  })

  // US37 (second review, 14.11.2026): the import chooses the category in the same two fields as the booking dialog
  describe('subcategories in the import', () => {
    it('offers the subcategories of the chosen category in a row and takes the subcategory', async () => {
      renderPage()
      await upload()
      const [first] = row('Spiele Shop')
      expect(within(first).queryByLabelText('Unterkategorie für Spiele Shop')).not.toBeInTheDocument()

      fireEvent.change(within(first).getByLabelText('Kategorie für Spiele Shop'), { target: { value: '8' } })
      const sub = within(first).getByLabelText('Unterkategorie für Spiele Shop')
      expect(within(sub).getAllByRole('option').map((o) => o.textContent)).toEqual([
        '– keine Unterkategorie –',
        'Kino',
        '+ Neue Unterkategorie …',
      ])
      fireEvent.change(sub, { target: { value: '81' } })

      expect(within(first).getByLabelText('Kategorie für Spiele Shop')).toHaveValue('8')
      expect(within(first).getByLabelText('Unterkategorie für Spiele Shop')).toHaveValue('81')
    })

    it('creates a new subcategory below the chosen category from a row', async () => {
      const fetchMock = renderPage()
      await upload()
      const [first] = row('Spiele Shop')

      fireEvent.change(within(first).getByLabelText('Kategorie für Spiele Shop'), { target: { value: '8' } })
      fireEvent.change(within(first).getByLabelText('Unterkategorie für Spiele Shop'), { target: { value: 'new' } })
      fireEvent.change(within(first).getByLabelText('Neue Unterkategorie von Freizeit'), { target: { value: 'Spiele' } })
      fireEvent.click(within(first).getByRole('button', { name: 'Anlegen' }))

      await waitFor(() => expect(within(first).getByLabelText('Unterkategorie für Spiele Shop')).toHaveValue('50'))
      const call = fetchMock.mock.calls.find(([url, init]) => url === '/api/categories' && init?.method === 'POST')
      expect(JSON.parse(String(call?.[1]?.body))).toEqual({ name: 'Spiele', parentId: 8 })
    })
  })
})
