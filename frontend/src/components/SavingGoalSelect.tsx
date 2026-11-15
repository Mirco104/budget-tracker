import { useState } from 'react'
import { useSavingGoals } from '../api/goals'
import { SavingGoalDialog } from './SavingGoalDialog'

const NEW_GOAL = 'new'

interface Props {
  /** The chosen goal id as text; '' is "ohne Sparziel" when noneLabel is given. */
  value: string
  onChange: (value: string) => void
  /** With this first option for no goal (planned item); without it a goal must be chosen (month closing). */
  noneLabel?: string
  /** Accessible name, unless labelledBy points to a visible label. */
  label?: string
  labelledBy?: string
}

/**
 * The choice of a saving goal, the same everywhere (27h): the goals and "+ Neues Sparziel …", which opens the window
 * "Neues Sparziel" (decision 66) and chooses the new goal. Without goals and without noneLabel it offers "Sparziel
 * erstellen" instead.
 */
export function SavingGoalSelect({ value, onChange, noneLabel, label = 'Sparziel', labelledBy }: Props) {
  const goalsQuery = useSavingGoals()
  const goals = goalsQuery.data ?? []
  const [creating, setCreating] = useState(false)

  const dialog = creating && (
    <SavingGoalDialog goal={null} onCreated={(goal) => onChange(String(goal.id))} onClose={() => setCreating(false)} />
  )

  if (noneLabel === undefined && goals.length === 0) {
    // Only once the goals are loaded, so it does not flash up while loading
    if (!goalsQuery.isSuccess) {
      return null
    }
    return (
      <p className="hint">
        Noch kein Sparziel.{' '}
        <button type="button" className="link-button" onClick={() => setCreating(true)}>
          Sparziel erstellen
        </button>
        {dialog}
      </p>
    )
  }
  return (
    <>
      <select
        aria-label={labelledBy ? undefined : label}
        aria-labelledby={labelledBy}
        value={value}
        onChange={(e) => (e.target.value === NEW_GOAL ? setCreating(true) : onChange(e.target.value))}
      >
        {noneLabel !== undefined && <option value="">{noneLabel}</option>}
        {goals.map((goal) => (
          <option key={goal.id} value={goal.id}>
            {goal.name}
          </option>
        ))}
        <option value={NEW_GOAL}>+ Neues Sparziel …</option>
      </select>
      {dialog}
    </>
  )
}
