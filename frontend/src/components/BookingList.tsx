import { useShowsCategories } from '../useShowsCategories'
import type { Category, Transaction } from '../api/types'
import { formatEuro, formatMonth, formatSigned } from '../format'
import { sumOf, totals } from '../sums'
import { CategoryIcon } from './ui/CategoryIcon'
import { Icon } from './ui/Icon'

interface Props {
  entries: Transaction[]
  categories: Category[]
  /** Today as "YYYY-MM-DD"; later entries are marked "geplant" (US26). */
  today: string
  /** Shows the account of each entry; only useful with more than one account (US30). */
  showAccount?: boolean
  onSelect: (entry: Transaction) => void
  /** Ticket #136: in the selection mode a click chooses a row instead of opening it. */
  selection?: {
    ids: Set<number>
    toggle: (entry: Transaction) => void
    /** A transfer or Sparen has no category (R27), so it cannot be chosen. */
    selectable: (entry: Transaction) => boolean
  }
}

const dayName = new Intl.DateTimeFormat('de-DE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

/** "Dienstag, 20. Oktober 2026" */
function formatDay(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return dayName.format(new Date(year, month - 1, day))
}

function entryKey(entry: Transaction): string {
  return `tx-${entry.id}`
}

/** Groups the entries by key, in the order in which the keys first appear. */
function groupBy(entries: Transaction[], keyOf: (entry: Transaction) => string): [string, Transaction[]][] {
  const groups = new Map<string, Transaction[]>()
  for (const entry of entries) {
    const key = keyOf(entry)
    groups.set(key, [...(groups.get(key) ?? []), entry])
  }
  return [...groups]
}

/**
 * Entries grouped by day, newest day first, each day with its sum (decision 30). When the entries span more than
 * one month, each month starts with a heading and its income and expenses (US26). A click on a row opens it for
 * editing, so a row needs no buttons of its own.
 */
export function BookingList({ entries, categories, today, showAccount = false, onSelect, selection }: Props) {
  // R35: the category of a booking only shows in stage 4
  const withCategories = useShowsCategories()
  const sorted = [...entries].sort((a, b) => b.date.localeCompare(a.date))
  const months = groupBy(sorted, (entry) => entry.date.slice(0, 7))
  const iconOf = (id: number | null) => categories.find((category) => category.id === id)?.icon ?? 'tag'

  function rowContent(entry: Transaction) {
    return (
      <>
        {entry.transfer ? (
          <span className="category-icon transfer-icon">
            <Icon name="transfer" size={18} />
          </span>
        ) : (
          <CategoryIcon icon={withCategories ? iconOf(entry.categoryId) : 'tag'} />
        )}
        <span className="tx-main">
          <span className="tx-description">{entry.description}</span>
          <span className="tx-meta">
            {entry.saving ? 'Sparen' : entry.transfer ? 'Umbuchung' : withCategories ? (entry.categoryName ?? 'Ohne Kategorie') : null}
            {showAccount && <span className="tx-account"> · {entry.accountName}</span>}
            {entry.fix && (
              <span className="badge" title="Fix sind regelmäßige Zahlungen, die du in der Planung eingetragen hast, z. B. Miete, Versicherungen, Abos. Alles andere ist variabel.">
                <Icon name="repeat" size={12} />
                fix
              </span>
            )}
            {entry.reserveItemKey && <span className="badge">aus Rücklage</span>}
            {entry.savingGoalId != null && <span className="badge">aus Sparziel</span>}
            {entry.auto && <span className="badge auto">automatisch</span>}
            {entry.date > today && <span className="badge planned">geplant</span>}
          </span>
        </span>
        <span
          className={entry.transfer ? 'tx-amount transfer' : entry.amount > 0 ? 'tx-amount positive' : 'tx-amount'}
        >
          {formatSigned(entry.amount)}
        </span>
      </>
    )
  }

  function dayList(items: Transaction[]) {
    return (
      <div className="day-list">
        {groupBy(items, (entry) => entry.date).map(([day, dayItems]) => {
          const sum = sumOf(dayItems)
          return (
            <section key={day} aria-label={formatDay(day)}>
              <h3 className="day-head">
                <span>{formatDay(day)}</span>
                <span className={sum > 0 ? 'positive' : undefined}>{formatSigned(sum)}</span>
              </h3>
              <ul className="tx-list">
                {dayItems.map((entry) => (
                  <li key={entryKey(entry)}>
                    {selection ? (
                      <label
                        className={`tx${selection.ids.has(entry.id) ? ' chosen' : ''}${selection.selectable(entry) ? '' : ' disabled'}`}
                      >
                        <input
                          type="checkbox"
                          className="tx-check"
                          aria-label={`${entry.description} auswählen`}
                          checked={selection.ids.has(entry.id)}
                          disabled={!selection.selectable(entry)}
                          onChange={() => selection.toggle(entry)}
                        />
                        {rowContent(entry)}
                      </label>
                    ) : (
                      <button type="button" className="tx" onClick={() => onSelect(entry)}>
                        {rowContent(entry)}
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          )
        })}
      </div>
    )
  }

  if (months.length <= 1) {
    return dayList(sorted)
  }

  return (
    <div className="month-list">
      {months.map(([month, items]) => {
        const sums = totals(items)
        return (
          <div key={month} role="group" aria-label={formatMonth(month)} className="month-group">
            <h2 className="month-head">
              <span>{formatMonth(month)}</span>
              <span className="month-sums">
                Einnahmen {formatEuro(sums.income)} · Ausgaben {formatEuro(sums.expenses)}
              </span>
            </h2>
            {dayList(items)}
          </div>
        )
      })}
    </div>
  )
}
