import { currentMonth, shiftMonth } from '../../format'
import { Icon } from './Icon'

interface Props {
  month: string
  onChange: (month: string) => void
  /** Shown in front of the navigation and names the field when the month means something else, e.g. "Gilt ab". */
  label?: string
}

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/

/**
 * ‹ Monat › with a field to pick or type the month, and a way back to the current month (US8). The button keeps
 * its place while hidden, so the navigation does not move when it appears.
 */
export function MonthNav({ month, onChange, label }: Props) {
  const current = currentMonth()
  const atCurrent = month === current
  return (
    <>
      <button
        type="button"
        className="month-today"
        onClick={() => onChange(current)}
        disabled={atCurrent}
        aria-hidden={atCurrent}
        tabIndex={atCurrent ? -1 : undefined}
        style={atCurrent ? { visibility: 'hidden' } : undefined}
      >
        Zum aktuellen Monat
      </button>
      <div className="month-nav-field">
        {label && (
          <span className="month-nav-label" aria-hidden="true">
            {label}
          </span>
        )}
        <div className="month-nav" role="group" aria-label={label ?? 'Monat'}>
          <button
            type="button"
            className="icon-button"
            aria-label="Vorheriger Monat"
            onClick={() => onChange(shiftMonth(month, -1))}
          >
            <Icon name="chevron-left" />
          </button>
          {/* Browsers without a month picker show a text field; only a complete month is taken */}
          <input
            type="month"
            className="month-input"
            aria-label={label ?? 'Monat wählen'}
            value={month}
            required
            onChange={(event) => {
              if (MONTH_PATTERN.test(event.target.value)) {
                onChange(event.target.value)
              }
            }}
          />
          <button
            type="button"
            className="icon-button"
            aria-label="Nächster Monat"
            onClick={() => onChange(shiftMonth(month, 1))}
          >
            <Icon name="chevron-right" />
          </button>
        </div>
      </div>
    </>
  )
}
