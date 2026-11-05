import { useCategories } from '../api/categories'
import { useReserves } from '../api/reserves'
import { useTransactions } from '../api/transactions'
import { AddBookingButton, AddBookingFab } from '../components/Booking'
import { useBookingDialog } from '../components/useBookingDialog'
import { BookingList } from '../components/BookingList'
import { MonthNav } from '../components/ui/MonthNav'
import { PageHeader } from '../components/ui/PageHeader'
import { bookingDateFor, formatEuro, todayIso } from '../format'

interface Props {
  month: string
  /** Shows the month navigation in the page header. */
  onMonthChange?: (month: string) => void
}

/** The list first, booking in a dialog (decision 30, US27). */
export function TransactionsPage({ month, onMonthChange }: Props) {
  const transactions = useTransactions(month)
  const categories = useCategories(month)
  // Compact reserve pot (R14, decision 15); details on the Auswertung page.
  const reserves = useReserves(month)
  const booking = useBookingDialog({
    categories: categories.data,
    defaultDate: bookingDateFor(month),
  })

  return (
    <section>
      <PageHeader title="Transaktionen">
        {onMonthChange && <MonthNav month={month} onChange={onMonthChange} />}
        <AddBookingButton onClick={booking.openNew} />
      </PageHeader>

      {categories.error && <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>}

      {reserves.data && reserves.data.reserves.length > 0 && (
        <p className="hint">
          Rücklagen: monatlich {formatEuro(reserves.data.monthlyReserve)} zurücklegen, jetzt angespart{' '}
          {formatEuro(reserves.data.reserveBalance)}.
        </p>
      )}

      {transactions.isPending && <p>Lade Transaktionen …</p>}
      {transactions.error && (
        <p className="error">Transaktionen konnten nicht geladen werden: {transactions.error.message}</p>
      )}
      {transactions.data && transactions.data.length === 0 && (
        <p className="card empty">Keine Transaktionen in diesem Monat.</p>
      )}
      {transactions.data && transactions.data.length > 0 && (
        <BookingList
          entries={transactions.data}
          categories={categories.data ?? []}
          today={todayIso()}
          onSelect={booking.openEdit}
        />
      )}

      <AddBookingFab onClick={booking.openNew} />
      {booking.element}
    </section>
  )
}
