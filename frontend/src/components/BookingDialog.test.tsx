import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { BankAccount, Category, Transaction } from '../api/types'
import type { BookingSuggestion } from '../api/suggestions'
import { BookingDialog } from './BookingDialog'

// Fictional values, shaped like the backend responses.
const categories: Category[] = [
  { id: 1, name: 'Wohnen', icon: 'home', archived: false, incomeOnly: false },
  { id: 6, name: 'Freizeit', icon: 'music', archived: false, incomeOnly: false },
  { id: 7, name: 'Lebensmittel', icon: 'cart', archived: false, incomeOnly: false },
  { id: 20, name: 'Gehalt', icon: 'wallet', archived: false, incomeOnly: true },
  { id: 30, name: 'Kredite', icon: 'card', archived: true, incomeOnly: false },
]

const kino: Transaction = { id: 7, amount: -12, description: 'Kino', date: '2026-10-20', categoryId: 6, categoryName: 'Freizeit', accountId: 1, accountName: 'Girokonto', transfer: false, auto: false, fix: false }
const recent: Transaction[] = [
  { id: 1, amount: -50, description: 'Markt', date: '2026-10-02', categoryId: 7, categoryName: 'Lebensmittel', accountId: 1, accountName: 'Girokonto', transfer: false, auto: false, fix: false },
  kino,
  { id: 2, amount: 3000, description: 'Lohn', date: '2026-10-01', categoryId: 20, categoryName: 'Gehalt', accountId: 1, accountName: 'Girokonto', transfer: false, auto: false, fix: false },
  { id: 3, amount: -900, description: 'Alt', date: '2026-10-01', categoryId: 30, categoryName: 'Kredite', accountId: 1, accountName: 'Girokonto', transfer: false, auto: false, fix: false },
]


function json(body: unknown, status = 200) {
  return Promise.resolve(new Response(JSON.stringify(body), { status }))
}

function renderDialog(
  entry: Transaction | null = null,
  answer: (url: string) => Promise<Response> = () => json({}),
  bankAccounts: BankAccount[] = [],
  suggestion: BookingSuggestion = { categoryId: null, certain: false },
) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (init?.method === 'DELETE') {
      return Promise.resolve(new Response(null, { status: 204 }))
    }
    if (init?.method) {
      return answer(url)
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
    return json([])
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


describe('BookingDialog', () => {

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
      'Sparen (auf ein Spar- oder Depotkonto)',
      'Wohnen',
      'Freizeit',
      'Lebensmittel',
      'Gehalt',
      '+ Neue Kategorie …',
    ])
  })

  it('opens a transfer with the transfer chosen', () => {
    renderDialog({ ...kino, categoryId: null, categoryName: null, transfer: true, auto: false, fix: false })

    expect(screen.getByLabelText('Kategorie')).toHaveValue('transfer')
  })

  it('shows a general server error in the dialog', async () => {
    renderDialog(null, () => json({ status: 409, detail: 'Etwas ist schiefgelaufen.' }, 409))
    fillValid()

    save()

    expect(await screen.findByRole('alert')).toHaveTextContent('Etwas ist schiefgelaufen.')
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

  it('closes with Escape, the close button and Abbrechen', () => {
    const { onClose } = renderDialog()

    fireEvent.keyDown(screen.getByLabelText('Betrag in Euro'), { key: 'Escape' })
    fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

    expect(onClose).toHaveBeenCalledTimes(3)
  })

  // 27f3: Sparen with source and target account makes both bookings
  it('books Sparen from one account to another', async () => {
    const { fetchMock } = renderDialog(null, () => json({}, 201), twoAccounts)
    fireEvent.change(screen.getByLabelText('Betrag in Euro'), { target: { value: '200' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Auf Tagesgeld' } })
    fireEvent.change(screen.getByLabelText('Kategorie'), { target: { value: 'saving' } })
    const target = await screen.findByLabelText('Auf Konto')
    fireEvent.change(target, { target: { value: String(twoAccounts[1].id) } })
    fireEvent.click(screen.getByRole('button', { name: 'Speichern' }))

    await waitFor(() => expect(sent(fetchMock, 'POST')).toBeDefined())
    expect(sent(fetchMock, 'POST')!.body).toMatchObject({
      amount: -200, transfer: true, saving: true, targetAccountId: twoAccounts[1].id,
    })
  })
})
