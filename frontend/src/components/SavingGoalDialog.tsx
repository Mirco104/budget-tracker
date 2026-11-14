import { useState, type FormEvent } from 'react'
import { errorText } from '../api/client'
import { useCreateSavingGoal, useDeleteSavingGoal, useUpdateSavingGoal } from '../api/goals'
import type { SavingGoal } from '../api/types'
import { parseAmountInput } from '../format'
import { Dialog } from './ui/Dialog'

function amountText(value: number | null): string {
  return value === null ? '' : value.toFixed(2).replace('.', ',')
}

/** 27f4: a saving goal with name, target amount and target date (both optional); a new one when goal is null. */
export function SavingGoalDialog({ goal, onClose }: { goal: SavingGoal | null; onClose: () => void }) {
  const create = useCreateSavingGoal()
  const update = useUpdateSavingGoal()
  const remove = useDeleteSavingGoal()
  const [name, setName] = useState(goal?.name ?? '')
  const [target, setTarget] = useState(amountText(goal?.targetAmount ?? null))
  const [date, setDate] = useState(goal?.targetDate ?? '')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<unknown>) {
    setError(null)
    setBusy(true)
    try {
      await action()
      onClose()
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
      setBusy(false)
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (name.trim() === '') {
      setError('Der Name ist Pflicht.')
      return
    }
    const amount = target.trim() === '' ? null : parseAmountInput(target)
    if (target.trim() !== '' && amount === null) {
      setError('Bitte einen Betrag wie 1.234,56 eingeben.')
      return
    }
    const input = { name: name.trim(), targetAmount: amount, targetDate: date === '' ? null : date }
    void run(() => (goal ? update.mutateAsync({ id: goal.id, input }) : create.mutateAsync(input)))
  }

  return (
    <Dialog title={goal ? 'Sparziel bearbeiten' : 'Neues Sparziel'} onClose={onClose}>
      <form className="booking-form" noValidate onSubmit={handleSubmit}>
        <label className="field">
          Name
          <input value={name} maxLength={100} onChange={(e) => setName(e.target.value)} />
        </label>
        <div className="field-row">
          <label className="field">
            Zielbetrag (optional)
            <input inputMode="decimal" placeholder="0,00" value={target} onChange={(e) => setTarget(e.target.value)} />
          </label>
          <label className="field">
            Zieldatum (optional)
            <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </label>
        </div>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="dialog-actions">
          <div>
            {goal && (
              <button type="button" className="danger" disabled={busy} onClick={() => void run(() => remove.mutateAsync(goal.id))}>
                Löschen
              </button>
            )}
          </div>
          <div className="dialog-buttons">
            <button type="button" onClick={onClose}>
              Abbrechen
            </button>
            <button type="submit" disabled={busy}>
              Speichern
            </button>
          </div>
        </div>
      </form>
    </Dialog>
  )
}
