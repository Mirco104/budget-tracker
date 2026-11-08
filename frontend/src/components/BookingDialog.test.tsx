import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { BankAccount, Category, RecurringTransaction, Transaction } from '../api/types'
import type { BookingSuggestion } from '../api/suggestions'
import { BookingDialog } from './BookingDialog'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', icon: 'home', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 6, name: 'Freizeit', icon: 'music', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 7, name: 'Lebensmittel', icon: 'cart', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: false },
  { id: 20, name: 'Gehalt', icon: 'wallet', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: false, incomeOnly: true },
  { id: 30, name: 'Kredite', icon: 'card', targetAmount: null, fixedCosts: null, fixedCostItems: [], targetValidFrom: null, archived: true, incomeOnly: false },
]

const kino: Transaction = { kind: 'SINGLE', id: 7, recurringId: null, occurrenceDate: null, amount: -12, description: 'Kino', date: '2026-10-20', categoryId: 6, categoryName: 'Freizeit', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null }
const streaming: Transaction = { kind: 'RECURRING', id: null, recurringId: 3, occurrenceDate: '2026-10-05', amount: -15, description: 'Streaming', date: '2026-10-05', categoryId: 6, categoryName: 'Freizeit', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null }
const recent: Transaction[] = [
  { kind: 'SINGLE', id: 1, recurringId: null, occurrenceDate: null, amount: -50, description: 'Markt', date: '2026-10-02', categoryId: 7, categoryName: 'Lebensmittel', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null },
  kino,
  { kind: 'SINGLE', id: 2, recurringId: null, occurrenceDate: null, amount: 3000, description: 'Lohn', date: '2026-10-01', categoryId: 20, categoryName: 'Gehalt', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null },
  { kind: 'SINGLE', id: 3, recurringId: null, occurrenceDate: null, amount: -900, description: 'Alt', date: '2026-10-01', categoryId: 30, categoryName: 'Kredite', accountId: 1, accountName: 'Girokonto', transfer: false, replaces: null },
]

const series: RecurringTransaction[] = [
  { id: 3, seriesId: 'a1', amount: -15, description: 'Streaming', categoryId: 6, categoryName: 'Freizeit', accountId: 1, accountName: 'Girokonto', interval: 'QUARTERLY', startDate: '2026-01-05', endDate: '2027-12-31', monthlyEquivalent: 5 },
]

function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderDialog(
  entry: Transaction | null = null,
  answer: (url: string) => Promise<Response> = () => json({}),
  bankAccounts: BankAccount[] = [],
  suggestion: BookingSuggestion = { categoryId: null, certain: false },
  period: Transaction[] = recent,
) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (init?.method === 'DELETE') {
      return Promise.resolve(new Response(null, { status: 204 }))
    }
    if (init?.method) {
      return answer(url)
    }
    if (url.startsWith('/api/transactions?from=')) {
      return json(period)
    }
    if (url.startsWith('/api/transactions')) {
      return json(recent)
    }
    if (url.startsWith('/api/bank-accounts')) {
      return json(bankAccounts)
    }
    if (url.startsWith('/api/booking-suggestion')) {
      return json(suggestion)
    }
    return url.startsWith('/api/recurring-transactions') ? json(series) : json([])
  })
  vi.stubGlobal('fetch', fetchMock)
  const onDone = vi.fn()
  const onClose = vi.fn()
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <BookingDialog
        categories={categories}
        entry={entry}
        defaultDate="2026-10-18"
        onClose={onClose}
        onDone={onDone}
      />
    </QueryClientProvider>,
  )
  return { fetchMock, onDone, onClose }
}

function sent(fetchMock: ReturnType<typeof vi.fn>, method: string) {
  const call = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === method)
  return call ? { url: call[0] as string, body: JSON.parse(String((call[1] as RequestInit).body)) } : undefined
}

function fillValid() {
  fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '240' } })
  fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Haftpflicht' } })
  fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: '1' } })
}

function save() {
  fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))
}

const twoAccounts: BankAccount[] = [
  { id: 1, name: 'Girokonto', iban: null, defaultAccount: true, archived: false, used: true },
  { id: 2, name: 'Kreditkarte', iban: null, defaultAccount: false, archived: false, used: false },
  { id: 3, name: 'Alt', iban: null, defaultAccount: false, archived: true, used: true },
]

const miete = {
  categoryId: 1,
  certain: true,
  occurrence: { recurringId: 3, date: '2026-10-01', description: 'Miete Wohnung', amount: 900, categoryId: 1 },
}

describe('BookingDialog', () => {
  // R28, US35: a new booking replaces the planned occurrence the backend finds for amount and date
  it('replaces the planned occurrence of a series unless the user unticks it', async () => {
    const { fetchMock, onDone } = renderDialog(null, () => json({}), [], miete)
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '905' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Miete Oktober' } })

    const replace = await screen.findByRole('checkbox', { name: /Ersetzt das geplante „Miete Wohnung“ vom 01.10.2026/ })
    expect(replace).toBeChecked()
    // The category of the series is only a suggestion (decision 46)
    expect(screen.getByLabelText('Kategorie')).toHaveValue('')
    fireEvent.click(screen.getByRole('button', { name: /Vorschlag: Wohnen/ }))
    expect(screen.getByLabelText('Kategorie')).toHaveValue('1')
    const urls = fetchMock.mock.calls.map(([url]) => String(url)).filter((url) => url.startsWith('/api/booking-suggestion'))
    expect(urls).toContain('/api/booking-suggestion?text=Miete%20Oktober&income=false&amount=-905&date=2026-10-18')
    save()

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(sent(fetchMock, 'POST')?.body).toMatchObject({ amount: -905, replaces: { recurringId: 3, date: '2026-10-01' } })
  })

  it('books without replacing when the user unticks it', async () => {
    const { fetchMock, onDone } = renderDialog(null, () => json({}), [], miete)
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '905' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Miete Oktober' } })
    fireEvent.click(await screen.findByRole('checkbox', { name: /Ersetzt das geplante/ }))
    fireEvent.click(screen.getByRole('button', { name: /Vorschlag: Wohnen/ }))
    save()

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(sent(fetchMock, 'POST')?.body).not.toHaveProperty('replaces')
  })

  it('offers no replacement for a series or when an entry is edited', async () => {
    renderDialog(kino, () => json({}), [], miete)
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '905' } })
    await new Promise((resolve) => setTimeout(resolve, 400))
    expect(screen.queryByRole('checkbox', { name: /Ersetzt das geplante/ })).not.toBeInTheDocument()
  })

  // US32: the suggestion fills the category of a new booking until the user chooses one
  // Decision 46: even a certain suggestion is not preset; it is a button below the field
  it('offers the suggested category as a button and presets nothing', async () => {
    const { fetchMock } = renderDialog(null, () => json({}), [], { categoryId: 7, certain: true })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'REWE Markt' } })

    const button = await screen.findByRole('button', { name: /Vorschlag: Lebensmittel/ })
    expect(screen.getByLabelText('Kategorie')).toHaveValue('')
    fireEvent.click(button)
    expect(screen.getByLabelText('Kategorie')).toHaveValue('7')
    expect(screen.queryByRole('button', { name: /Vorschlag:/ })).not.toBeInTheDocument()
    const call = fetchMock.mock.calls.map(([url]) => String(url)).find((url) => url.startsWith('/api/booking-suggestion'))
    expect(call).toBe('/api/booking-suggestion?text=REWE%20Markt&income=false')
  })

  it('keeps the category the user chose', async () => {
    renderDialog(null, () => json({}), [], { categoryId: 7, certain: true })
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: '6' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'REWE Markt' } })

    await new Promise((resolve) => setTimeout(resolve, 400))
    expect(screen.getByLabelText('Kategorie')).toHaveValue('6')
    expect(screen.queryByRole('button', { name: /Vorschlag:/ })).not.toBeInTheDocument()
  })

  it('asks for no suggestion when an entry is edited', async () => {
    const { fetchMock } = renderDialog(kino, () => json({}), [], { categoryId: 7, certain: true })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Kino am Abend' } })

    await new Promise((resolve) => setTimeout(resolve, 400))
    expect(screen.getByLabelText('Kategorie')).toHaveValue('6')
    expect(fetchMock.mock.calls.some(([url]) => String(url).startsWith('/api/booking-suggestion'))).toBe(false)
  })

  it('saves the suggested category', async () => {
    const { fetchMock, onDone } = renderDialog(null, () => json({}), [], { categoryId: 7, certain: false })
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '23,40' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'REWE Markt' } })
    fireEvent.click(await screen.findByRole('button', { name: /Vorschlag: Lebensmittel/ }))
    save()

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(sent(fetchMock, 'POST')?.body).toMatchObject({ categoryId: 7, amount: -23.4 })
  })

  // US30: the account field is preset to the default account
  it('books on the chosen account', async () => {
    const { fetchMock, onDone } = renderDialog(null, () => json({}), twoAccounts)
    const account = await screen.findByLabelText('Konto')
    expect(account).toHaveValue('1')
    expect(within(account).queryByRole('option', { name: 'Alt' })).not.toBeInTheDocument()

    fillValid()
    fireEvent.change(account, { target: { value: '2' } })
    save()

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(sent(fetchMock, 'POST')?.body).toMatchObject({ accountId: 2 })
  })

  // 08.11.2026: a missing category is created in the dialog and chosen
  it('creates a new category and books on it', async () => {
    const { fetchMock, onDone } = renderDialog(null, (url) =>
      url.startsWith('/api/categories') ? json({ ...categories[0], id: 40, name: 'Spiele', icon: 'tag' }, 201) : json({}),
    )
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '20' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Brettspiel' } })
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: 'new' } })

    const name = screen.getByRole('textbox', { name: 'Neue Kategorie' })
    fireEvent.change(name, { target: { value: 'Spiele' } })
    // Enter creates the category and does not save the booking
    fireEvent.keyDown(name, { key: 'Enter' })

    await waitFor(() => expect(screen.getByLabelText('Kategorie')).toHaveValue('40'))
    expect(screen.queryByRole('textbox', { name: 'Neue Kategorie' })).not.toBeInTheDocument()
    expect(sent(fetchMock, 'POST')).toEqual({ url: '/api/categories', body: { name: 'Spiele' } })
    expect(onDone).not.toHaveBeenCalled()

    save()
    await waitFor(() => expect(onDone).toHaveBeenCalled())
    const posts = fetchMock.mock.calls.filter(([, init]) => (init as RequestInit | undefined)?.method === 'POST')
    expect(posts.map(([url]) => url)).toEqual(['/api/categories', '/api/transactions'])
    expect(JSON.parse(String((posts[1][1] as RequestInit).body))).toMatchObject({ categoryId: 40 })
  })

  it('keeps the dialog open when the name field is cancelled with Escape', async () => {
    const { onClose } = renderDialog()
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: 'new' } })

    fireEvent.keyDown(screen.getByRole('textbox', { name: 'Neue Kategorie' }), { key: 'Escape' })

    expect(screen.queryByRole('textbox', { name: 'Neue Kategorie' })).not.toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  // 08.11.2026: the account field shows with one account too, so a second one can be created
  it('creates a new account from the account field and books on it', async () => {
    const { fetchMock, onDone } = renderDialog(
      null,
      (url) =>
        url.startsWith('/api/bank-accounts')
          ? json({ id: 5, name: 'Tagesgeld', iban: null, defaultAccount: false, archived: false, used: false }, 201)
          : json({}),
      [twoAccounts[0]],
    )
    const account = await screen.findByLabelText('Konto')
    expect(account).toHaveValue('1')
    fillValid()

    fireEvent.change(account, { target: { value: 'new' } })
    fireEvent.change(screen.getByRole('textbox', { name: 'Neues Konto' }), { target: { value: 'Tagesgeld' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    await waitFor(() => expect(screen.queryByRole('textbox', { name: 'Neues Konto' })).not.toBeInTheDocument())
    save()

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    const posts = fetchMock.mock.calls.filter(([, init]) => (init as RequestInit | undefined)?.method === 'POST')
    expect(posts.map(([url]) => url)).toEqual(['/api/bank-accounts', '/api/transactions'])
    expect(JSON.parse(String((posts[1][1] as RequestInit).body))).toMatchObject({ accountId: 5 })
  })

  it('shows the error of a category name that exists', async () => {
    renderDialog(null, () => json({ status: 409, detail: 'Eine Kategorie „Wohnen“ gibt es schon.' }, 409))
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: 'new' } })
    fireEvent.change(screen.getByRole('textbox', { name: 'Neue Kategorie' }), { target: { value: 'Wohnen' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Eine Kategorie „Wohnen“ gibt es schon.')
    expect(screen.getByLabelText('Kategorie')).toHaveValue('')
  })

  it('sends no account for a new booking on the default account', async () => {
    const { fetchMock, onDone } = renderDialog(null, () => json({}), twoAccounts)
    await screen.findByLabelText('Konto')
    fillValid()
    save()

    await waitFor(() => expect(onDone).toHaveBeenCalled())
    expect(sent(fetchMock, 'POST')?.body).not.toHaveProperty('accountId')
  })

  it('opens as a dialog with the fields in the order of US27 and the focus on the amount', () => {
    renderDialog()

    const dialog = screen.getByRole('dialog', { name: 'Neue Buchung' })
    expect(within(dialog).getByRole('button', { name: 'Ausgabe' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByLabelText('Betrag in Euro')).toHaveFocus()
    expect(screen.getByLabelText('Datum')).toHaveValue('2026-10-18')
    expect(screen.getByLabelText('Wiederholen')).toHaveValue('NONE')
    expect(screen.queryByLabelText('Enddatum (optional)')).not.toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: 'Löschen' })).not.toBeInTheDocument()
    const labels = within(dialog)
      .getAllByText(/^(Beschreibung|Kategorie|Datum|Wiederholen)$/)
      .map((element) => element.firstChild?.textContent)
    expect(labels).toEqual(['Beschreibung', 'Kategorie', 'Datum', 'Wiederholen'])
  })

  it('shows required-field messages in the dialog and sends nothing', () => {
    const { fetchMock } = renderDialog()
    fireEvent.change(screen.getByLabelText('Datum'), { target: { value: '' } })

    save()

    expect(screen.getByText('Der Betrag ist Pflicht.')).toBeInTheDocument()
    expect(screen.getByText('Die Beschreibung ist Pflicht.')).toBeInTheDocument()
    expect(screen.getByText('Das Datum ist Pflicht.')).toBeInTheDocument()
    expect(screen.getByText('Die Kategorie ist Pflicht.')).toBeInTheDocument()
    expect(sent(fetchMock, 'POST')).toBeUndefined()
  })

  it.each(['0', '-5', '12.50', 'abc'])('rejects the amount %s', (amount) => {
    const { fetchMock } = renderDialog()
    fillValid()
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: amount } })

    save()

    expect(screen.getByLabelText('Betrag in Euro')).toHaveAttribute('aria-invalid', 'true')
    expect(sent(fetchMock, 'POST')).toBeUndefined()
  })

  // R11: entered positive, the switch sets the sign
  it('books an expense with a negative amount by default', async () => {
    const { fetchMock, onDone } = renderDialog()
    fillValid()

    save()

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('Buchung gespeichert.'))
    expect(sent(fetchMock, 'POST')).toEqual({
      url: '/api/transactions',
      body: { amount: -240, description: 'Haftpflicht', categoryId: 1, date: '2026-10-18' },
    })
  })

  it('books income with a positive amount', async () => {
    const { fetchMock } = renderDialog()
    fireEvent.click(screen.getByRole('button', { name: 'Einnahme' }))
    fillValid()

    save()

    await waitFor(() => expect(sent(fetchMock, 'POST')?.body.amount).toBe(240))
  })

  // US27: the last used categories with the chosen sign, newest first, without archived ones
  it('offers the last used categories of the month for the chosen sign', async () => {
    const { fetchMock } = renderDialog()

    const quick = await screen.findByRole('group', { name: 'Zuletzt genutzt' })
    expect(fetchMock).toHaveBeenCalledWith('/api/transactions?month=2026-10', expect.anything())
    expect(within(quick).getAllByRole('button').map((b) => b.textContent)).toEqual(['Freizeit', 'Lebensmittel'])

    fireEvent.click(within(quick).getByRole('button', { name: 'Lebensmittel' }))
    expect(within(quick).getByRole('button', { name: 'Lebensmittel' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByLabelText('Kategorie')).toHaveValue('7')

    fireEvent.click(screen.getByRole('button', { name: 'Einnahme' }))
    expect(
      within(screen.getByRole('group', { name: 'Zuletzt genutzt' })).getAllByRole('button').map((b) => b.textContent),
    ).toEqual(['Gehalt'])
  })

  it('lists all active categories in the selection, without archived ones', () => {
    renderDialog()

    const options = within(screen.getByLabelText('Kategorie')).getAllByRole('option').map((o) => o.textContent)
    expect(options).toEqual([
      'Kategorie wählen …',
      'Umbuchung zwischen eigenen Konten (zählt nicht)',
      'Wohnen',
      'Freizeit',
      'Lebensmittel',
      'Gehalt',
      '+ Neue Kategorie …',
    ])
  })

  // R27, US31: a transfer has no category and cannot repeat
  it('books a transfer without category and without repeat', async () => {
    const { fetchMock, onDone } = renderDialog()
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '500' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Aufs Tagesgeld' } })
    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'MONTHLY' } })
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: 'transfer' } })

    expect(screen.queryByLabelText('Wiederholen')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Datum')).toBeInTheDocument()
    save()

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('Buchung gespeichert.'))
    expect(sent(fetchMock, 'POST')).toEqual({
      url: '/api/transactions',
      body: { amount: -500, description: 'Aufs Tagesgeld', date: '2026-10-18', transfer: true },
    })
  })

  it('opens a transfer with the transfer chosen', () => {
    renderDialog({ ...kino, categoryId: null, categoryName: null, transfer: true, replaces: null })

    expect(screen.getByLabelText('Kategorie')).toHaveValue('transfer')
  })

  it('offers no transfer for an occurrence of a series', () => {
    renderDialog(streaming)

    const options = within(screen.getByLabelText('Kategorie')).getAllByRole('option').map((o) => o.textContent)
    expect(options).not.toContain('Umbuchung zwischen eigenen Konten (zählt nicht)')
  })

  it('creates a series with "Erste Abbuchung" and an end date when a repeat is chosen', async () => {
    const { fetchMock } = renderDialog()
    fillValid()
    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'YEARLY' } })

    expect(screen.getByLabelText('Erste Abbuchung')).toHaveValue('2026-10-18')
    fireEvent.change(screen.getByLabelText('Enddatum (optional)'), { target: { value: '2030-10-18' } })
    save()

    await waitFor(() => expect(sent(fetchMock, 'POST')?.url).toBe('/api/recurring-transactions'))
    expect(sent(fetchMock, 'POST')?.body).toEqual({
      amount: -240,
      description: 'Haftpflicht',
      categoryId: 1,
      interval: 'YEARLY',
      startDate: '2026-10-18',
      endDate: '2030-10-18',
    })
  })

  it('rejects an end date before the first occurrence', () => {
    const { fetchMock } = renderDialog()
    fillValid()
    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'MONTHLY' } })
    fireEvent.change(screen.getByLabelText('Enddatum (optional)'), { target: { value: '2026-10-01' } })

    save()

    expect(screen.getByText('Das Enddatum darf nicht vor dem Startdatum liegen.')).toBeInTheDocument()
    expect(sent(fetchMock, 'POST')).toBeUndefined()
  })

  it('shows server field errors at the field and keeps the dialog open', async () => {
    const { onDone } = renderDialog(null, () =>
      json({ status: 400, errors: [{ field: 'startDate', message: 'Das Startdatum ist ungültig.' }] }, 400),
    )
    fillValid()
    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'MONTHLY' } })

    save()

    expect(await screen.findByText('Das Startdatum ist ungültig.')).toBeInTheDocument()
    expect(onDone).not.toHaveBeenCalled()
  })

  it('shows a general server error in the dialog', async () => {
    renderDialog(null, () => json({ status: 409, detail: 'Etwas ist schiefgelaufen.' }, 409))
    fillValid()

    save()

    expect(await screen.findByRole('alert')).toHaveTextContent('Etwas ist schiefgelaufen.')
  })

  it('edits a single transaction with the amount positive and Ausgabe selected', async () => {
    const { fetchMock, onDone } = renderDialog(kino)

    expect(screen.getByRole('dialog', { name: 'Buchung bearbeiten' })).toBeInTheDocument()
    expect(screen.getByLabelText('Betrag in Euro')).toHaveValue('12,00')
    expect(screen.getByRole('button', { name: 'Ausgabe' })).toHaveAttribute('aria-pressed', 'true')
    // Ticket #76: a single booking can become a series; "nie" keeps it single
    expect(screen.getByLabelText('Wiederholen')).toHaveValue('NONE')
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '14' } })

    save()

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('Änderung gespeichert.'))
    expect(sent(fetchMock, 'PUT')).toEqual({
      url: '/api/transactions/7',
      body: { amount: -14, description: 'Kino', categoryId: 6, accountId: 1, date: '2026-10-20' },
    })
  })

  // Ticket #76
  it('turns an edited single booking into the first occurrence of a new series', async () => {
    const { fetchMock } = renderDialog(kino)

    fireEvent.change(screen.getByLabelText('Wiederholen'), { target: { value: 'MONTHLY' } })
    expect(screen.getByText('Diese Buchung wird zur ersten Abbuchung der neuen Serie.')).toBeInTheDocument()
    expect(screen.getByLabelText('Erste Abbuchung')).toHaveValue('2026-10-20')
    save()

    await waitFor(() =>
      expect(sent(fetchMock, 'POST')).toEqual({
        url: '/api/recurring-transactions/from-transaction/7',
        body: {
          amount: -12,
          description: 'Kino',
          categoryId: 6,
          accountId: 1,
          interval: 'MONTHLY',
          startDate: '2026-10-20',
          endDate: null,
        },
      }),
    )
    expect(sent(fetchMock, 'PUT')).toBeUndefined()
  })

  // Ticket #91
  it('lets an edited single booking replace a nearby occurrence of a series', async () => {
    const { fetchMock } = renderDialog(kino, undefined, [], undefined, [streaming])

    const join = await screen.findByLabelText('Gehört zu einer Serie')
    expect(join).toHaveValue('')
    fireEvent.change(join, { target: { value: '3|2026-10-05' } })
    // A booking that joins a series does not become a series of its own
    expect(screen.queryByLabelText('Wiederholen')).not.toBeInTheDocument()
    save()

    await waitFor(() =>
      expect(sent(fetchMock, 'PUT')).toEqual({
        url: '/api/transactions/7',
        body: {
          amount: -12,
          description: 'Kino',
          categoryId: 6,
          accountId: 1,
          date: '2026-10-20',
          replaces: { recurringId: 3, date: '2026-10-05' },
        },
      }),
    )
  })

  it('keeps the link of a booking that already replaces an occurrence and offers no repeat', async () => {
    const linked: Transaction = { ...kino, replaces: { recurringId: 3, date: '2026-10-05' } }
    const { fetchMock } = renderDialog(linked, undefined, [], undefined, [])

    expect(await screen.findByLabelText('Gehört zu einer Serie')).toHaveDisplayValue(
      'wie bisher (ersetzt den Eintrag vom 05.10.2026)',
    )
    expect(screen.queryByLabelText('Wiederholen')).not.toBeInTheDocument()
    save()

    await waitFor(() => expect(sent(fetchMock, 'PUT')?.body).not.toHaveProperty('replaces'))
  })

  it('keeps an archived category of an edited entry', () => {
    renderDialog({ ...kino, categoryId: 30, categoryName: 'Kredite', accountId: 1, accountName: 'Girokonto' })

    expect(screen.getByLabelText('Kategorie')).toHaveValue('30')
  })

  it('deletes a single transaction after asking once more', async () => {
    const { fetchMock, onDone } = renderDialog(kino)

    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    expect(screen.getByText('Buchung wirklich löschen?')).toBeInTheDocument()
    expect(fetchMock).not.toHaveBeenCalledWith('/api/transactions/7', expect.objectContaining({ method: 'DELETE' }))

    fireEvent.click(screen.getByRole('button', { name: 'Endgültig löschen' }))

    await waitFor(() => expect(onDone).toHaveBeenCalledWith('Buchung gelöscht.'))
    expect(fetchMock).toHaveBeenCalledWith('/api/transactions/7', expect.objectContaining({ method: 'DELETE' }))
  })

  it('goes back from the delete question without deleting', () => {
    const { fetchMock } = renderDialog(kino)

    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Zurück' }))

    expect(screen.getByRole('button', { name: 'Speichern' })).toBeInTheDocument()
    expect(sent(fetchMock, 'DELETE')).toBeUndefined()
  })

  // R13: an occurrence asks for the scope in the dialog; there is no "all entries"
  it('edits only this occurrence by default, as a single transaction', async () => {
    const { fetchMock } = renderDialog(streaming)

    expect(screen.getByRole('dialog', { name: 'Wiederkehrende Buchung bearbeiten' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Nur dieser Eintrag' })).toBeChecked()
    expect(screen.queryByRole('radio', { name: /Alle/ })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Wiederholen')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Datum'), { target: { value: '2026-10-07' } })

    save()

    await waitFor(() =>
      expect(sent(fetchMock, 'PUT')).toEqual({
        url: '/api/recurring-transactions/3/occurrences/2026-10-05?scope=THIS',
        body: { amount: -15, description: 'Streaming', categoryId: 6, accountId: 1, date: '2026-10-07' },
      }),
    )
  })

  it('edits this and all following occurrences as a series, filled from the series', async () => {
    const { fetchMock } = renderDialog(streaming)
    const following = screen.getByRole('radio', { name: 'Dieser und alle folgenden' })
    await waitFor(() => expect(following).toBeEnabled())

    fireEvent.click(following)

    expect(screen.getByLabelText('Erste Abbuchung')).toHaveValue('2026-10-05')
    expect(screen.getByLabelText('Wiederholen')).toHaveValue('QUARTERLY')
    expect(within(screen.getByLabelText('Wiederholen')).queryByRole('option', { name: 'nie' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Enddatum (optional)')).toHaveValue('2027-12-31')
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '18' } })
    save()

    await waitFor(() => expect(sent(fetchMock, 'PUT')?.url).toBe('/api/recurring-transactions/3/occurrences/2026-10-05?scope=FOLLOWING'))
    expect(sent(fetchMock, 'PUT')?.body).toEqual({
      amount: -18,
      description: 'Streaming',
      categoryId: 6,
      accountId: 1,
      interval: 'QUARTERLY',
      startDate: '2026-10-05',
      endDate: '2027-12-31',
    })
  })

  it('deletes this and all following occurrences with the chosen scope', async () => {
    const { fetchMock } = renderDialog(streaming)
    const following = screen.getByRole('radio', { name: 'Dieser und alle folgenden' })
    await waitFor(() => expect(following).toBeEnabled())
    fireEvent.click(following)

    fireEvent.click(screen.getByRole('button', { name: 'Löschen' }))
    expect(screen.getByText('Diesen und alle folgenden Einträge löschen?')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Endgültig löschen' }))

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/recurring-transactions/3/occurrences/2026-10-05?scope=FOLLOWING',
        expect.objectContaining({ method: 'DELETE' }),
      ),
    )
  })

  it('closes with Escape, the close button and Abbrechen', () => {
    const { onClose } = renderDialog()

    fireEvent.keyDown(screen.getByLabelText('Betrag in Euro'), { key: 'Escape' })
    fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(onClose).toHaveBeenCalledTimes(3)
  })
})
