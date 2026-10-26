import { useReserves } from '../api/reserves'
import type { ForecastMonth, ReserveData } from '../api/types'
import { formatAmount, formatDate, formatEuro, formatMonth } from '../format'
import { INTERVAL_LABELS } from '../labels'

function averagePeriod(months: number): string {
  return months === 1 ? 'letzter Monat' : `letzte ${months} Monate`
}

function AverageExpenses({ data }: { data: ReserveData }) {
  return (
    <>
      <dl className="summary">
        <div>
          <dt>
            Ø Ausgaben pro Monat
            {data.averageMonths > 0 && <span className="tile-note"> ({averagePeriod(data.averageMonths)})</span>}
          </dt>
          <dd>{formatEuro(data.averageExpenses)}</dd>
        </div>
      </dl>
      {data.averageMonths === 0 && (
        <p className="hint">
          Noch kein abgeschlossener Monat mit Einträgen. Der Durchschnitt erscheint ab dem Monat nach dem ersten
          Eintrag.
        </p>
      )}
      {data.averageMonths > 0 && data.averageMonths < 12 && (
        <p className="hint">
          Es gibt erst seit {data.averageMonths === 1 ? 'einem Monat' : `${data.averageMonths} Monaten`} Einträge,
          daher zählen nur diese.
        </p>
      )}
    </>
  )
}

function ReservePot({ data }: { data: ReserveData }) {
  if (data.reserves.length === 0) {
    return (
      <p className="hint">
        Keine seltenen Ausgaben in den nächsten 12 Monaten. Vierteljährliche, halbjährliche und jährliche
        wiederkehrende Ausgaben erscheinen hier.
      </p>
    )
  }
  return (
    <>
      <dl className="summary">
        <div>
          <dt>Monatlich zurücklegen</dt>
          <dd>{formatEuro(data.monthlyReserve)}</dd>
        </div>
        <div>
          <dt>Sollte jetzt angespart sein</dt>
          <dd>{formatEuro(data.reserveBalance)}</dd>
        </div>
      </dl>
      <p className="hint">
        Lege jeden Monat {formatEuro(data.monthlyReserve)} zur Seite, dann ist jede seltene Abbuchung gedeckt, wenn
        sie fällig wird.
      </p>
      <table>
        <thead>
          <tr>
            <th>Ausgabe</th>
            <th className="number col-optional">Betrag (€)</th>
            <th>Fällig am</th>
            <th className="number">Pro Monat (€)</th>
            <th className="number">Angespart (€)</th>
          </tr>
        </thead>
        <tbody>
          {data.reserves.map((line) => (
            <tr key={line.recurringId}>
              <td>
                {line.description}
                <span className="tile-note"> {INTERVAL_LABELS[line.interval]}</span>
              </td>
              <td className="number col-optional">{formatAmount(line.amount)}</td>
              <td>{formatDate(line.nextDate)}</td>
              <td className="number">{formatAmount(line.monthly)}</td>
              <td className="number">{formatAmount(line.saved)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

function ForecastRow({ month, largest }: { month: ForecastMonth; largest: number }) {
  // Bar length relative to the most expensive month, for display only
  const width = largest > 0 ? (month.total / largest) * 100 : 0
  return (
    <tr>
      <td>
        {month.entries.length > 0 ? (
          <details>
            <summary>{formatMonth(month.month)}</summary>
            <ul className="forecast-entries">
              {month.entries.map((entry) => (
                <li key={entry.kind === 'SINGLE' ? `single-${entry.id}` : `recurring-${entry.recurringId}-${entry.date}`}>
                  {formatDate(entry.date)} {entry.description}: {formatEuro(entry.amount)}
                  {entry.kind === 'RECURRING' && <span className="badge">wiederkehrend</span>}
                </li>
              ))}
            </ul>
          </details>
        ) : (
          formatMonth(month.month)
        )}
      </td>
      <td className="number">{formatAmount(month.total)}</td>
      <td className="forecast-bar-cell" aria-hidden="true">
        <span className="forecast-bar" style={{ width: `${width}%` }} />
      </td>
    </tr>
  )
}

/** Analysis (R14, US13): average expenses, reserve pot for rare expenses and a 12-month forecast. */
export function ReservesPage({ month }: { month: string }) {
  const { data, isPending, error } = useReserves(month)

  if (isPending) {
    return <p>Lade Auswertung …</p>
  }
  if (error) {
    return <p className="error">Auswertung konnte nicht geladen werden: {error.message}</p>
  }

  const largest = Math.max(0, ...data.forecast.map((m) => m.total))

  return (
    <section>
      <h2>Auswertung</h2>
      <AverageExpenses data={data} />

      <h3>Rücklagen</h3>
      <ReservePot data={data} />

      <h3>Vorschau 12 Monate</h3>
      <p className="hint">
        Alle gebuchten Ausgaben je Monat, wiederkehrende und einzelne; teure Monate haben einen längeren Balken.
      </p>
      <table className="forecast">
        <thead>
          <tr>
            <th>Monat</th>
            <th className="number">Ausgaben (€)</th>
            <th className="forecast-bar-cell" aria-hidden="true" />
          </tr>
        </thead>
        <tbody>
          {data.forecast.map((m) => (
            <ForecastRow key={m.month} month={m} largest={largest} />
          ))}
        </tbody>
      </table>
    </section>
  )
}
