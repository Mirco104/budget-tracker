import { useState } from 'react'
import { errorText } from '../api/client'
import { useUpdatePlannedItem } from '../api/plan'
import type { PlannedItem } from '../api/types'
import { formatEuro, formatMonth, parseAmountInput } from '../format'
import { Dialog } from './ui/Dialog'

interface Props {
  /** The part not planned yet (P4), in euro. */
  amount: number
  /** The saving items valid in the month; only monthly ones and ones without a fixed date can take more. */
  items: PlannedItem[]
  month: string
  onNewItem: () => void
  onClose: () => void
  onDone: (message: string) => void
}

function cents(value: number): number {
  return Math.round(value * 100)
}

/**
 * 27f3 "Monatlich sparen": the part not planned is split onto the saving items that exist; each one gets more from
 * the month shown on (a new version, "Gilt ab"). The fields start empty; "Rest" puts what is still open into a row.
 */
export function SplitSavingDialog({ amount, items, month, onNewItem, onClose, onDone }: Props) {
  const update = useUpdatePlannedItem()
  const usable = items.filter((item) => item.rhythmUnit === null || (item.rhythmUnit === 'MONTH' && item.rhythmCount === 1))
  const [values, setValues] = useState<Record<number, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // Input helper in cents (CLAUDE.md): what is still open after the entered amounts
  const entered = usable.reduce((sum, item) => sum + cents(parseAmountInput(values[item.id] ?? '') ?? 0), 0)
  const open = cents(amount) - entered

  async function save() {
    setError(null)
    const changes = usable
      .map((item) => ({ item, add: parseAmountInput(values[item.id] ?? '') ?? 0 }))
      .filter((change) => change.add > 0)
    if (changes.length === 0) {
      setError('Bitte bei mindestens einem Sparposten einen Betrag eintragen.')
      return
    }
    setSaving(true)
    try {
      for (const { item, add } of changes) {
        await update.mutateAsync({
          id: item.id,
          input: {
            name: item.name,
            amount: -((cents(Math.abs(item.amount)) + cents(add)) / 100),
            saving: true,
            categoryId: item.categoryId,
            accountId: item.accountId,
            rhythmUnit: item.rhythmUnit,
            rhythmCount: item.rhythmCount,
            anchorDate: item.anchorDate,
            entryMode: item.entryMode,
            workdaysOnly: item.workdaysOnly,
            validFrom: month,
            savingGoalId: item.savingGoalId ?? null,
          },
        })
      }
      onDone(`Sparen ab ${formatMonth(month)} erhöht.`)
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
      setSaving(false)
    }
  }

  return (
    <Dialog title={`${formatEuro(amount)} aufteilen`} onClose={onClose}>
      <p className={open < 0 ? 'error' : 'hint'} role="status">
        {open < 0 ? `${formatEuro(-open / 100)} mehr als nicht verplant` : `noch ${formatEuro(open / 100)} offen`}
      </p>
      {usable.length === 0 && <p className="hint">Noch keine monatlichen Sparposten.</p>}
      <ul className="item-list split-list">
        {usable.map((item) => (
          <li key={item.id} className="account-row">
            <span className="item-main">
              <span className="item-name">{item.name}</span>
              <span className="tx-meta">{formatEuro(item.monthlyShare)} im Monat</span>
            </span>
            <span className="split-input">
              +
              <input
                inputMode="decimal"
                aria-label={`Mehr für ${item.name}`}
                placeholder="0,00"
                value={values[item.id] ?? ''}
                onChange={(e) => setValues({ ...values, [item.id]: e.target.value })}
              />
              €
            </span>
            <button
              type="button"
              disabled={open <= 0}
              onClick={() => {
                const current = cents(parseAmountInput(values[item.id] ?? '') ?? 0)
                setValues({ ...values, [item.id]: ((current + open) / 100).toFixed(2).replace('.', ',') })
              }}
            >
              Rest
            </button>
          </li>
        ))}
      </ul>
      <button type="button" className="link-button" onClick={onNewItem}>
        + Neuer Sparposten
      </button>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="dialog-actions">
        <div />
        <div className="dialog-buttons">
          <button type="button" onClick={onClose}>
            Abbrechen
          </button>
          <button type="button" onClick={() => void save()} disabled={saving || open < 0}>
            Speichern
          </button>
        </div>
      </div>
    </Dialog>
  )
}
