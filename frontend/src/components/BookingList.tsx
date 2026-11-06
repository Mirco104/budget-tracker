import type { Category, Transaction } from '../api/types'
import { formatSigned } from '../format'
import { CategoryIcon } from './ui/CategoryIcon'
import { Icon } from './ui/Icon'

interface Props {
  entries: Transaction[]
  categories: Category[]
  /** Today as "YYYY-MM-DD"; later entries are marked "geplant" (US26). */
  today: string
  onSelect: (entry: Transaction) => void
}

const dayName = new Intl.DateTimeFormat('de-DE', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })

/** "Dienstag, 20. Oktober 2026" */
function formatDay(isoDate: string): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  return dayName.format(new Date(year, month - 1, day))
}

/** Sum in whole cents, so adding many amounts shows no float noise (display only). */
function sumOf(entries: Transaction[]): number {
  return entries.reduce((sum, entry) => sum + Math.round(entry.amount * 100), 0) / 100
}

function entryKey(entry: Transaction): string {
  return entry.kind === 'SINGLE' ? `single-${entry.id}` : `recurring-${entry.recurringId}-${entry.occurrenceDate}`
}

/**
 * Entries grouped by day, newest day first, each day with its sum (decision 30). A click on a row opens it for
 * editing, so a row needs no buttons of its own.
 */
export function BookingList({ entries, categories, today, onSelect }: Props) {
  const days = new Map<string, Transaction[]>()
  for (const entry of [...entries].sort((a, b) => b.date.localeCompare(a.date))) {
    days.set(entry.date, [...(days.get(entry.date) ?? []), entry])
  }
  const iconOf = (id: number) => categories.find((category) => category.id === id)?.icon ?? 'tag'

  return (
    <div className="day-list">
      {[...days].map(([day, items]) => {
        const sum = sumOf(items)
        return (
          <section key={day} aria-label={formatDay(day)}>
            <h3 className="day-head">
              <span>{formatDay(day)}</span>
              <span className={sum > 0 ? 'positive' : undefined}>{formatSigned(sum)}</span>
            </h3>
            <ul className="tx-list">
              {items.map((entry) => (
                <li key={entryKey(entry)}>
                  <button type="button" className="tx" onClick={() => onSelect(entry)}>
                    <CategoryIcon icon={iconOf(entry.categoryId)} />
                    <span className="tx-main">
                      <span className="tx-description">{entry.description}</span>
                      <span className="tx-meta">
                        {entry.categoryName}
                        {entry.kind === 'RECURRING' && (
                          <span className="badge">
                            <Icon name="repeat" size={12} />
                            wiederkehrend
                          </span>
                        )}
                        {entry.date > today && <span className="badge planned">geplant</span>}
                      </span>
                    </span>
                    <span className={entry.amount > 0 ? 'tx-amount positive' : 'tx-amount'}>
                      {formatSigned(entry.amount)}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}
