import { useReserves } from '../api/reserves'
import type { ForecastMonth } from '../api/types'
import { formatAmount, formatDate, formatEuro, formatMonth } from '../format'
import { INTERVAL_LABELS } from '../labels'

function ForecastRow({ month, largest }: { month: ForecastMonth; largest: number }) {
  // Bar length relative to the most expensive month, for display only
  const width = largest > 0 ? (month.total / largest) * 100 : 0
  return (
    <tr>
      <td>
        {month.occurrences.length > 0 ? (
          <details>
            <summary>{formatMonth(month.month)}</summary>
            <ul className="forecast-occurrences">
              {month.occurrences.map((occurrence) => (
                <li key={`${occurrence.recurringId}-${occurrence.date}`}>
                  {formatDate(occurrence.date)} {occurrence.description}: {formatEuro(occurrence.amount)}
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

/** Reserve analysis (R14, US13): average monthly costs of recurring expenses and a 12-month forecast. */
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
      <p className="hint">
        Wiederkehrende Ausgaben, die in {formatMonth(month)} laufen. Einzelne Transaktionen und wiederkehrende
        Einnahmen zählen nicht.
      </p>
      <dl className="summary">
        <div>
          <dt>Ø monatliche Kosten</dt>
          <dd>{formatEuro(data.averageMonthlyCosts)}</dd>
        </div>
        <div>
          <dt>davon Rücklage</dt>
          <dd>{formatEuro(data.reserve)}</dd>
        </div>
      </dl>
      <p className="hint">
        Die Rücklage ist der Betrag, den du monatlich zurücklegen solltest, um vierteljährliche, halbjährliche und
        jährliche Abbuchungen zu decken.
      </p>

      <h3>Wiederkehrende Ausgaben</h3>
      {data.series.length === 0 ? (
        <p className="hint">In diesem Monat laufen keine wiederkehrenden Ausgaben.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>Beschreibung</th>
              <th className="col-optional">Kategorie</th>
              <th>Intervall</th>
              <th className="number">Betrag (€)</th>
              <th className="number">Pro Monat (€)</th>
            </tr>
          </thead>
          <tbody>
            {data.series.map((line) => (
              <tr key={line.id}>
                <td>{line.description}</td>
                <td className="col-optional">{line.categoryName}</td>
                <td>{INTERVAL_LABELS[line.interval]}</td>
                <td className="number">{formatAmount(Math.abs(line.amount))}</td>
                <td className="number">{formatAmount(line.monthlyEquivalent)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h3>Vorschau 12 Monate</h3>
      <p className="hint">Abbuchungen wiederkehrender Ausgaben je Monat; teure Monate haben einen längeren Balken.</p>
      <table className="forecast">
        <thead>
          <tr>
            <th>Monat</th>
            <th className="number">Abbuchungen (€)</th>
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
