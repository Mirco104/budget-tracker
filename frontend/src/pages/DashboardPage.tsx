import { useDashboard } from '../api/dashboard'
import { formatDate, formatEuro, formatPercent } from '../format'
import { GROUP_LABELS, GROUP_RECOMMENDATIONS } from '../labels'

function signClass(value: number | null): string | undefined {
  return value !== null && value < 0 ? 'negative' : undefined
}

/** Actual amount, with the part from yearly transactions below it when there is one. */
function ActualCell({ actual, actualYearly }: { actual: number; actualYearly: number }) {
  return (
    <td className="number">
      {formatEuro(actual)}
      {actualYearly !== 0 && <span className="hint cell-hint">davon {formatEuro(actualYearly)} anteilig jährlich</span>}
    </td>
  )
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
          <dd className={signClass(data.surplus)}>
            {formatEuro(data.surplus)}
            {data.savingsRate !== null && <> ({formatPercent(data.savingsRate)})</>}
          </dd>
        </div>
        <div>
          <dt>Davon anteilige Jahreskosten</dt>
          <dd>{formatEuro(data.yearlyExpensesShare)}</dd>
        </div>
      </dl>

      {data.dueYearly.length > 0 && (
        <aside className="notice" aria-label="Fällige Jahresabbuchungen">
          <h3>Fällige Jahresabbuchungen in diesem Monat</h3>
          <ul>
            {data.dueYearly.map((due) => (
              <li key={due.id}>
                {formatDate(due.dueDate)}: {due.description} ({due.categoryName}) – {formatEuro(due.amountPerYear)}
              </li>
            ))}
          </ul>
          <p className="hint">Im Ist ist davon nur der Monatsanteil enthalten, abgebucht wird der volle Jahresbetrag.</p>
        </aside>
      )}

      <h3>Gruppen</h3>
      <table>
        <thead>
          <tr>
            <th>Gruppe</th>
            <th>Empfehlung</th>
            <th className="number">Soll</th>
            <th className="number">Ist</th>
            <th className="number">Differenz</th>
          </tr>
        </thead>
        <tbody>
          {data.groups.map((line) => (
            <tr key={line.group}>
              <td>{GROUP_LABELS[line.group]}</td>
              <td className="hint">{GROUP_RECOMMENDATIONS[line.group]}</td>
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
            <th className="number">Differenz</th>
          </tr>
        </thead>
        <tbody>
          {data.categories.map((line) => (
            <tr key={line.id}>
              <td>{line.name}</td>
              <td className={`number ${signClass(line.target) ?? ''}`}>{formatEuro(line.target)}</td>
              <ActualCell actual={line.actual} actualYearly={line.actualYearly} />
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
              <ActualCell actual={line.actual} actualYearly={line.actualYearly} />
              <td className="number">{formatPercent(line.share)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}
