import { useDashboard } from '../api/dashboard'
import { formatEuro, formatPercent } from '../format'

function signClass(value: number | null): string | undefined {
  return value !== null && value < 0 ? 'negative' : undefined
}

export function DashboardPage({ month }: { month: string }) {
  const { data, isPending, error } = useDashboard(month)

  if (isPending) {
    return <p>Lade Dashboard …</p>
  }
  if (error) {
    return <p className="error">Dashboard konnte nicht geladen werden: {error.message}</p>
  }

  return (
    <section>
      <h2>Dashboard</h2>

      <dl className="summary">
        <div>
          <dt>Einnahmen gesamt</dt>
          <dd>{formatEuro(data.incomeTotal)}</dd>
        </div>
        <div>
          <dt>Ausgaben (Soll / Ist)</dt>
          <dd>
            {formatEuro(data.expenses.target)} / {formatEuro(data.expenses.actual)}
          </dd>
        </div>
        <div>
          <dt>Differenz</dt>
          <dd className={signClass(data.expenses.difference)}>{formatEuro(data.expenses.difference)}</dd>
        </div>
        <div>
          <dt>{data.surplusLabel}</dt>
          <dd className={signClass(data.surplus)}>{formatEuro(data.surplus)}</dd>
        </div>
        {data.changeLabel !== null && (
          <div>
            <dt>{data.changeLabel}</dt>
            <dd className={signClass(data.changePercent)}>{formatPercent(data.changePercent)}</dd>
          </div>
        )}
      </dl>

      <h3>Ausgaben</h3>
      <table>
        <thead>
          <tr>
            <th>Kategorie</th>
            <th className="number">Soll</th>
            <th className="number">Ist</th>
            <th className="number">Differenz</th>
          </tr>
        </thead>
        <tbody>
          {data.categories.map((line) => (
            <tr key={line.id}>
              <td>{line.name}</td>
              <td className={`number ${signClass(line.target) ?? ''}`}>{formatEuro(line.target)}</td>
              <td className="number">{formatEuro(line.actual)}</td>
              <td className={`number ${signClass(line.difference) ?? ''}`}>{formatEuro(line.difference)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h3>Einnahmen</h3>
      <table>
        <thead>
          <tr>
            <th>Kategorie</th>
            <th className="number">Ist</th>
            <th className="number">Anteil</th>
          </tr>
        </thead>
        <tbody>
          {data.income.map((line) => (
            <tr key={line.id}>
              <td>{line.name}</td>
              <td className="number">{formatEuro(line.actual)}</td>
              <td className="number">{formatPercent(line.share)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
