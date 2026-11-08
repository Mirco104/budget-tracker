import { useState } from 'react'
import { useBankAccounts } from '../api/bankAccounts'
import { useCategories } from '../api/categories'
import { errorText } from '../api/client'
import { useReserves } from '../api/reserves'
import { useTransactionsBetween } from '../api/transactions'
import type { Transaction } from '../api/types'
import { AddBookingButton, AddBookingFab } from '../components/Booking'
import { BookingList } from '../components/BookingList'
import { TransactionFilters } from '../components/TransactionFilters'
import { PageHeader } from '../components/ui/PageHeader'
import { useBookingDialog } from '../components/useBookingDialog'
import { facetCounts, NO_FILTERS, type Filters } from '../filters'
import { currentMonth, formatEuro, todayIso } from '../format'
import { isCompleteDate, periodLabel, presetPeriod, PRESETS, type Period, type PeriodPreset } from '../period'
import { IMPORT_ROUTE, useTransactionsRoute } from '../router'
import { ImportPage } from './ImportPage'
import { totals } from '../sums'

/** For the filter Art: a booking that replaces an occurrence counts as recurring (R28). */
function kindOf(entry: Transaction): Transaction['kind'] {
  return entry.replaces ? 'RECURRING' : entry.kind
}

/** The value of the filter Kategorie for an entry: a transfer (R27) is chosen like a category when booking. */
const TRANSFER_CATEGORY = 'transfer'
function categoryOf(entry: Transaction): string {
  return entry.transfer ? TRANSFER_CATEGORY : String(entry.categoryId)
}

/** US26: whether an entry passes the filters (description, category, account, kind). */
function matchesFilters(entry: Transaction, filters: Filters): boolean {
  const search = filters.query.trim().toLocaleLowerCase('de-DE')
  return (
    (filters.category === '' || categoryOf(entry) === filters.category) &&
    (filters.account === '' || String(entry.accountId) === filters.account) &&
    (filters.kind === '' || kindOf(entry) === filters.kind) &&
    (search === '' || entry.description.toLocaleLowerCase('de-DE').includes(search))
  )
}

const FACET_KEYS = {
  category: categoryOf,
  account: (entry: Transaction) => String(entry.accountId),
  kind: (entry: Transaction) => kindOf(entry),
}

/**
 * The entries of a period (US26), default this month, grouped by day; search and category filter the loaded
 * entries. Booking is in a dialog (US27, decision 30).
 */
export function TransactionsPage({ initialPeriod = null }: { initialPeriod?: Period | null }) {
  const today = todayIso()
  // A period in the address bar (e.g. after an import, US33) starts as a custom period
  const [preset, setPreset] = useState<PeriodPreset>(initialPeriod ? 'custom' : 'month')
  const [custom, setCustom] = useState<Period>(() => initialPeriod ?? presetPeriod('month', today))
  // Description, category, account (US26, US30: only with more than one) and kind: Wiederkehrend, einmalig (single
  // transactions that are no transfer) or Umbuchung (R27)
  const [filters, setFilters] = useState<Filters>(NO_FILTERS)

  const period = preset === 'custom' ? custom : presetPeriod(preset, today)
  const complete = isCompleteDate(period.from) && isCompleteDate(period.to)
  const transactions = useTransactionsBetween(period.from, period.to, complete)
  const categories = useCategories(currentMonth())
  const bankAccounts = useBankAccounts()
  // Compact reserve pot (R14, decision 15); details on the Auswertung page.
  const reserves = useReserves()
  const booking = useBookingDialog({ categories: categories.data, defaultDate: today })

  function choosePreset(next: PeriodPreset) {
    if (next === 'custom' && preset !== 'custom') {
      // Starts with the period shown so far
      setCustom(period)
    }
    setPreset(next)
  }

  const loaded = transactions.data ?? []
  const shown = loaded.filter((entry) => matchesFilters(entry, filters))
  // Ticket #84: the hits of each filter option with the other filters
  const counts = facetCounts(loaded, filters, matchesFilters, FACET_KEYS)
  const sums = totals(shown)
  const filtered =
    filters.query.trim() !== '' || filters.category !== '' || filters.kind !== '' || filters.account !== ''
  const accounts = bankAccounts.data ?? []

  return (
    <section>
      <PageHeader title="Transaktionen">
        <a className="button-link" href={IMPORT_ROUTE}>
          Importieren
        </a>
        <AddBookingButton onClick={booking.openNew} />
      </PageHeader>

      <div className="filters">
        <div className="chips" role="group" aria-label="Zeitraum">
          {PRESETS.map((option) => (
            <button
              key={option.value}
              type="button"
              className="chip plain"
              aria-pressed={preset === option.value}
              onClick={() => choosePreset(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>

        {preset === 'custom' && (
          <div className="period-fields">
            <label className="field">
              Von
              <input
                type="date"
                value={custom.from}
                max={custom.to || undefined}
                onChange={(e) => setCustom({ ...custom, from: e.target.value })}
              />
            </label>
            <label className="field">
              Bis
              <input
                type="date"
                value={custom.to}
                min={custom.from || undefined}
                onChange={(e) => setCustom({ ...custom, to: e.target.value })}
              />
            </label>
          </div>
        )}

        <TransactionFilters
          filters={filters}
          onChange={setFilters}
          counts={counts}
          categories={categories.data ?? []}
          extraCategoryOptions={[{ value: TRANSFER_CATEGORY, label: 'Umbuchung' }]}
          accounts={accounts.map((account) => ({
            value: String(account.id),
            label: account.archived ? `${account.name} (archiviert)` : account.name,
          }))}
        />

        {transactions.data && (
          <p className="hint period-summary" aria-live="polite">
            {periodLabel(period)} · {shown.length === 1 ? '1 Eintrag' : `${shown.length} Einträge`} · Einnahmen{' '}
            {formatEuro(sums.income)} · Ausgaben {formatEuro(sums.expenses)}
          </p>
        )}
      </div>

      {categories.error && <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>}

      {reserves.data && reserves.data.reserves.length > 0 && (
        <p className="hint">
          Rücklagen: monatlich {formatEuro(reserves.data.monthlyReserve)} zurücklegen, jetzt angespart{' '}
          {formatEuro(reserves.data.reserveBalance)}.
        </p>
      )}

      {!complete && <p className="hint">Bitte „Von“ und „Bis“ vollständig angeben.</p>}
      {complete && transactions.isPending && <p>Lade Transaktionen …</p>}
      {transactions.error && (
        <p className="error">
          Transaktionen konnten nicht geladen werden: {errorText(transactions.error, 'Unbekannter Fehler')}
        </p>
      )}
      {transactions.data && shown.length === 0 && (
        <p className="card empty">
          {filtered ? 'Keine Einträge für diesen Filter.' : 'Keine Einträge in diesem Zeitraum.'}
        </p>
      )}
      {shown.length > 0 && (
        <BookingList
          entries={shown}
          categories={categories.data ?? []}
          today={today}
          showAccount={accounts.length > 1}
          onSelect={booking.openEdit}
        />
      )}

      <AddBookingFab onClick={booking.openNew} />
      {booking.element}
    </section>
  )
}

/** Transaktionen with its sub page Importieren (US33); a new period in the address bar starts the list afresh. */
export function TransactionsRoute() {
  const route = useTransactionsRoute()
  if (route.importPage) {
    return <ImportPage />
  }
  return <TransactionsPage key={route.hash} initialPeriod={route.period} />
}
