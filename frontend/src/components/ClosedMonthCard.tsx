import { useState } from 'react'
import { errorText } from '../api/client'
import { useSavingGoals } from '../api/goals'
import { useCloseMonth, useMonthView, useUndoClosing } from '../api/notifications'
import { formatMonthName, formatSigned } from '../format'
import { goalOrFirst } from './goalChoice'
import { SavingGoalSelect } from './SavingGoalSelect'

/**
 * Decision 59 (27h): a past month shows its surplus or shortfall (P10) like the other cards; open it shows per account
 * the change of the balance and the closing: to which saving goal the leftover went, with "Rückgängig", or the choice.
 */
export function ClosedMonthCard({ month }: { month: string }) {
  const view = useMonthView(month, true)
  const goals = useSavingGoals().data ?? []
  const close = useCloseMonth()
  const undo = useUndoClosing()
  const [open, setOpen] = useState(false)
  const [goalId, setGoalId] = useState('')
  const [error, setError] = useState<string | null>(null)

  if (!view.data) {
    return null
  }
  const data = view.data
  const surplus = data.leftover >= 0
  const chosen = goalOrFirst(goalId, goals)

  async function run(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(errorText(e, 'Das hat nicht geklappt.'))
    }
  }

  const status = data.closed
    ? data.savingGoalName
      ? `dem Sparziel „${data.savingGoalName}“ zugeordnet`
      : 'abgeschlossen ohne Sparziel'
    : data.leftover > 0
      ? 'noch nicht zugeordnet'
      : null

  return (
    <section className={open ? 'summary-row open' : 'summary-row'} aria-label={surplus ? 'Überschuss' : 'Fehlbetrag'}>
      <button type="button" className="summary-head" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span className="summary-label">
          {surplus ? 'Überschuss' : 'Fehlbetrag'} im {formatMonthName(month)}
        </span>
        <span className={surplus ? 'summary-value income' : 'summary-value over'}>{formatSigned(data.leftover)}</span>
        {status && <span className="summary-note">{status}</span>}
        <span className="summary-chevron" aria-hidden="true">
          {open ? '▴' : '▾'}
        </span>
      </button>
      {open && (
        <div className="summary-body">
          <ul className="budget-list">
            {data.accounts.map((account) => (
              <li key={account.accountId} className="budget-item">
                <div className="budget-head">
                  <span className="budget-name">{account.name}</span>
                  <span className={account.change < 0 ? 'budget-amount over' : 'budget-amount'}>
                    {formatSigned(account.change)}
                  </span>
                </div>
              </li>
            ))}
          </ul>
          <div className="closing-actions">
            {data.closed ? (
              <button type="button" className="link-button" onClick={() => void run(() => undo.mutateAsync(month))}>
                Rückgängig
              </button>
            ) : (
              data.leftover > 0 && (
                <>
                  <SavingGoalSelect value={chosen} onChange={setGoalId} />
                  {chosen !== '' && (
                    <button
                      type="button"
                      className="primary"
                      disabled={close.isPending}
                      onClick={() => void run(() => close.mutateAsync({ month, savingGoalId: Number(chosen) }))}
                    >
                      Zuordnen
                    </button>
                  )}
                  <button
                    type="button"
                    className="link-button"
                    disabled={close.isPending}
                    onClick={() => void run(() => close.mutateAsync({ month, savingGoalId: null }))}
                  >
                    Nicht zuordnen
                  </button>
                </>
              )
            )}
          </div>
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
        </div>
      )}
    </section>
  )
}
