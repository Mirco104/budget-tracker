import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { useSavingGoals } from '../api/goals'
import {
  useAcceptPriceChange,
  useCloseMonth,
  useDismissNotification,
  useNotifications,
  type AppNotification,
} from '../api/notifications'
import { errorText } from '../api/client'
import { formatEuro, formatMonth } from '../format'
import { ROUTES } from '../router'
import { SavingGoalDialog } from './SavingGoalDialog'
import { Icon } from './ui/Icon'

const NEW_GOAL = 'new'

/**
 * 27h (decision 65): the bell with a counter. It lists the month closing (P10), an account below its lower limit
 * (P12), a price change (P13) and an overplanned month, each as a card with a heading; each can be marked "Erledigt",
 * the month closing is answered.
 */
export function Bell() {
  const notifications = useNotifications().data ?? []
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    function handlePointer(event: MouseEvent) {
      const target = event.target as Element
      // A dialog opened from the bell (new saving goal) lies outside it but keeps the bell open
      if (root.current && !root.current.contains(target) && !target.closest?.('.overlay')) {
        setOpen(false)
      }
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape' && !document.querySelector('.overlay')) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointer)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handlePointer)
      document.removeEventListener('keydown', handleKey)
    }
  }, [open])

  const count = notifications.length
  return (
    <div className="bell" ref={root}>
      <button
        type="button"
        className="bell-button"
        aria-label={count === 0 ? 'Benachrichtigungen' : `Benachrichtigungen (${count})`}
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        <Icon name="bell" />
        {count > 0 && (
          <span className="bell-count" aria-hidden="true">
            {count}
          </span>
        )}
      </button>
      {open && (
        <div className="menu bell-menu" role="region" aria-label="Benachrichtigungen">
          {count === 0 ? (
            <p className="hint">Keine Benachrichtigungen.</p>
          ) : (
            <ul className="bell-list">
              {notifications.map((notification) => (
                <li key={notification.key}>
                  <NotificationItem notification={notification} onNavigate={() => setOpen(false)} />
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

function titleOf(n: AppNotification): string {
  switch (n.kind) {
    case 'MONTH_CLOSING':
      return `Monatsabschluss ${formatMonth(n.month ?? '')}`
    case 'LOWER_LIMIT':
      return `Untergrenze: ${n.accountName ?? ''}`
    case 'PRICE_CHANGE':
      return 'Preisänderung'
    default:
      return 'Planung überplant'
  }
}

function NotificationItem({ notification: n, onNavigate }: { notification: AppNotification; onNavigate: () => void }) {
  const dismiss = useDismissNotification()
  const acceptPrice = useAcceptPriceChange()
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<unknown>) {
    setError(null)
    try {
      await action()
    } catch (e) {
      setError(errorText(e, 'Das hat nicht geklappt.'))
    }
  }

  const done = (
    <button type="button" onClick={() => void run(() => dismiss.mutateAsync(n.key))}>
      Erledigt
    </button>
  )

  let body
  if (n.kind === 'MONTH_CLOSING') {
    body = <MonthClosingItem month={n.month ?? ''} amount={n.amount ?? 0} onError={setError} />
  } else if (n.kind === 'LOWER_LIMIT') {
    const below = (n.balance ?? 0) < (n.limit ?? 0)
    body = (
      <>
        <p>
          {below
            ? `${n.accountName} liegt bei ${formatEuro(n.balance)} und damit unter der Untergrenze von ${formatEuro(n.limit)}.`
            : `${n.accountName} fällt nach den erwarteten Zahlungen voraussichtlich auf ${formatEuro(n.expected)}, unter die Untergrenze von ${formatEuro(n.limit)}.`}
        </p>
        <div className="bell-actions">
          <a className="button-link" href={ROUTES.accounts} onClick={onNavigate}>
            Zu den Konten
          </a>
          {done}
        </div>
      </>
    )
  } else if (n.kind === 'PRICE_CHANGE') {
    const income = (n.newAmount ?? 0) > 0
    const newAmount = Math.abs(n.newAmount ?? 0)
    body = (
      <>
        <p>
          „{n.itemName}“ {income ? 'bringt' : 'kostet'} jetzt {formatEuro(newAmount)} statt{' '}
          {formatEuro(Math.abs(n.oldAmount ?? 0))}.
        </p>
        <div className="bell-actions">
          <button
            type="button"
            className="primary"
            onClick={() => void run(() => acceptPrice.mutateAsync({ itemId: n.itemId ?? 0, amount: n.newAmount ?? 0 }))}
          >
            Übernehmen
          </button>
          {done}
        </div>
      </>
    )
  } else {
    body = (
      <>
        <p>
          Deine Planung für {formatMonth(n.month ?? '')} ist um {formatEuro(n.amount)} überplant.
        </p>
        <div className="bell-actions">
          <a className="button-link" href={ROUTES.planning} onClick={onNavigate}>
            Zur Planung
          </a>
          {done}
        </div>
      </>
    )
  }
  const warn = n.kind === 'LOWER_LIMIT' || n.kind === 'OVERPLANNED'
  return (
    <article className={warn ? 'bell-item warn' : 'bell-item'} aria-label={titleOf(n)}>
      <h3 className="bell-title">{titleOf(n)}</h3>
      {body}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </article>
  )
}

/**
 * P10: "180 € sind übrig geblieben. Soll es auf ein Sparziel gerechnet werden?" with the saving goals, a new saving
 * goal right here, and "Nicht zuordnen".
 */
function MonthClosingItem({ month, amount, onError }: { month: string; amount: number; onError: (e: string | null) => void }) {
  const goalsQuery = useSavingGoals()
  const goals = goalsQuery.data ?? []
  const close = useCloseMonth()
  const [goalId, setGoalId] = useState('')
  const [creating, setCreating] = useState(false)
  const chosen = goalId !== '' ? goalId : goals[0] ? String(goals[0].id) : ''

  async function submit(savingGoalId: number | null) {
    onError(null)
    try {
      await close.mutateAsync({ month, savingGoalId })
    } catch (e) {
      onError(errorText(e, 'Der Monat konnte nicht abgeschlossen werden.'))
    }
  }

  return (
    <>
      <p>{formatEuro(amount)} sind übrig geblieben. Soll es auf ein Sparziel gerechnet werden?</p>
      {goals.length > 0 ? (
        <div className="bell-actions">
          <select
            aria-label="Sparziel"
            value={chosen}
            onChange={(e) => (e.target.value === NEW_GOAL ? setCreating(true) : setGoalId(e.target.value))}
          >
            {goals.map((goal) => (
              <option key={goal.id} value={goal.id}>
                {goal.name}
              </option>
            ))}
            <option value={NEW_GOAL}>+ Neues Sparziel …</option>
          </select>
          <button type="button" className="primary" disabled={close.isPending} onClick={() => void submit(Number(chosen))}>
            Zuordnen
          </button>
        </div>
      ) : (
        // Only once the goals are loaded, so it does not flash up while loading
        goalsQuery.isSuccess && (
          <p className="hint">
            Noch kein Sparziel.{' '}
            <button type="button" className="link-button" onClick={() => setCreating(true)}>
              Sparziel erstellen
            </button>
          </p>
        )
      )}
      <button
        type="button"
        className="link-button bell-skip"
        disabled={close.isPending}
        onClick={() => void submit(null)}
      >
        Nicht zuordnen (das Geld bleibt unverplant auf dem Konto)
      </button>
      {creating &&
        // In the body, so the dialog lies above the header and the bottom bar
        createPortal(
          <SavingGoalDialog
            goal={null}
            onCreated={(goal) => setGoalId(String(goal.id))}
            onClose={() => setCreating(false)}
          />,
          document.body,
        )}
    </>
  )
}
