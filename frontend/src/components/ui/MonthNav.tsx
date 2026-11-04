import { currentMonth, formatMonth, shiftMonth } from '../../format'
import { Icon } from './Icon'

interface Props {
  month: string
  onChange: (month: string) => void
}

/** ‹ Monat › with a way back to the current month (US8). */
export function MonthNav({ month, onChange }: Props) {
  const current = currentMonth()
  return (
    <>
      <div className="month-nav" role="group" aria-label="Monat">
        <button
          type="button"
          className="icon-button"
          aria-label="Vorheriger Monat"
          onClick={() => onChange(shiftMonth(month, -1))}
        >
          <Icon name="chevron-left" />
        </button>
        <span className="month-label" aria-live="polite">
          {formatMonth(month)}
        </span>
        <button
          type="button"
          className="icon-button"
          aria-label="Nächster Monat"
          onClick={() => onChange(shiftMonth(month, 1))}
        >
          <Icon name="chevron-right" />
        </button>
      </div>
      {month !== current && (
        <button type="button" onClick={() => onChange(current)}>
          Zum aktuellen Monat
        </button>
      )}
    </>
  )
}
