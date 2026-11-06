import { useState } from 'react'
import { useCategories } from '../api/categories'
import { errorText } from '../api/client'
import { useReserves } from '../api/reserves'
import { useTransactionsBetween } from '../api/transactions'
import type { Transaction } from '../api/types'
import { AddBookingButton, AddBookingFab } from '../components/Booking'
import { BookingList } from '../components/BookingList'
import { Icon } from '../components/ui/Icon'
import { PageHeader } from '../components/ui/PageHeader'
import { useBookingDialog } from '../components/useBookingDialog'
import { currentMonth, formatEuro, todayIso } from '../format'
import { isCompleteDate, periodLabel, presetPeriod, PRESETS, type Period, type PeriodPreset } from '../period'
import { totals } from '../sums'

/**
 * The entries of a period (US26), default this month, grouped by day; search and category filter the loaded
 * entries. Booking is in a dialog (US27, decision 30).
 */
export function TransactionsPage() {
  const today = todayIso()
  const [preset, setPreset] = useState<PeriodPreset>('month')
  const [custom, setCustom] = useState<Period>(() => presetPeriod('month', today))
  const [query, setQuery] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [kind, setKind] = useState<'' | Transaction['kind']>('')

  const period = preset === 'custom' ? custom : presetPeriod(preset, today)
  const complete = isCompleteDate(period.from) && isCompleteDate(period.to)
  const transactions = useTransactionsBetween(period.from, period.to, complete)
  const categories = useCategories(currentMonth())
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

  const search = query.trim().toLocaleLowerCase('de-DE')
  const shown = (transactions.data ?? []).filter(
    (entry) =>
      (categoryId === '' || String(entry.categoryId) === categoryId) &&
      (kind === '' || entry.kind === kind) &&
      (search === '' || entry.description.toLocaleLowerCase('de-DE').includes(search)),
  )
  const sums = totals(shown)
  const filtered = search !== '' || categoryId !== '' || kind !== ''

  return (
    <section>
      <PageHeader title="Transaktionen">
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

        <div className="filter-row">
          <label className="search">
            <Icon name="search" size={18} />
            <input
              type="search"
              placeholder="Beschreibung suchen"
              aria-label="Beschreibung suchen"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </label>
          <select aria-label="Kategorie filtern" value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
            <option value="">Alle Kategorien</option>
            {(categories.data ?? []).map((category) => (
              <option key={category.id} value={category.id}>
                {category.archived ? `${category.name} (archiviert)` : category.name}
              </option>
            ))}
          </select>
          <select
            aria-label="Art filtern"
            value={kind}
            onChange={(e) => setKind(e.target.value as '' | Transaction['kind'])}
          >
            <option value="">Alle Arten</option>
            <option value="RECURRING">Wiederkehrend</option>
            <option value="SINGLE">Einmalig</option>
          </select>
        </div>

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
        <BookingList entries={shown} categories={categories.data ?? []} today={today} onSelect={booking.openEdit} />
      )}

      <AddBookingFab onClick={booking.openNew} />
      {booking.element}
    </section>
  )
}
