import { useDashboard } from '../api/dashboard'
import { formatEuro, formatPercent } from '../format'
import { GROUP_LABELS, GROUP_RECOMMENDATIONS, UNGROUPED_LABEL } from '../labels'

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
          <dt>Abweichung zum Soll</dt>
          <dd className={signClass(data.expenses.difference)}>{formatEuro(data.expenses.difference)}</dd>
        </div>
        <div>
          <dt>Nicht verplant</dt>
          <dd className={signClass(data.unplanned)}>{formatEuro(data.unplanned)}</dd>
        </div>
        <div>
          <dt>{data.surplusLabel}</dt>
          <dd className={signClass(data.surplus)}>
            {formatEuro(data.surplus)}
            {data.savingsRate !== null && <> ({formatPercent(data.savingsRate)})</>}
          </dd>
        </div>
      </dl>

      <h3>Gruppen</h3>
      <table>
        <thead>
          <tr>
            <th>Gruppe</th>
            <th>Empfehlung</th>
            <th className="number">Soll</th>
            <th className="number">Ist</th>
            <th className="number">Abweichung</th>
          </tr>
        </thead>
        <tbody>
          {data.groups.map((line) => (
            <tr key={line.group ?? 'UNGROUPED'}>
              <td>{line.group ? GROUP_LABELS[line.group] : UNGROUPED_LABEL}</td>
              <td className="hint">{line.group && GROUP_RECOMMENDATIONS[line.group]}</td>
              <td className={`number ${signClass(line.target) ?? ''}`}>{formatEuro(line.target)}</td>
              <td className="number">{formatEuro(line.actual)}</td>
              <td className={`number ${signClass(line.difference) ?? ''}`}>{formatEuro(line.difference)}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h3>Ausgaben</h3>
      <table>
        <thead>
          <tr>
            <th>Kategorie</th>
            <th className="number">Soll</th>
            <th className="number">Ist</th>
            <th className="number">Abweichung</th>
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
              <td className={`number ${signClass(line.actual) ?? ''}`}>{formatEuro(line.actual)}</td>
              <td className="number">{formatPercent(line.share)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
