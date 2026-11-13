import { useState, type FormEvent } from 'react'
import { useAccounts, useLinkReserve, useReconcile, useReservesByAccount, useSetComfortAmount } from '../api/accounts'
import { useSavingGoals } from '../api/goals'
import { useUpdateBankAccount } from '../api/bankAccounts'
import { errorText } from '../api/client'
import { usePlan } from '../api/plan'
import type { AccountLine, AccountRole, ReserveGroup, SavingGoal } from '../api/types'
import { BankAccountSettings } from '../components/BankAccountSettings'
import { SavingGoalDialog } from '../components/SavingGoalDialog'
import { Dialog } from '../components/ui/Dialog'
import { InfoTip } from '../components/ui/InfoTip'
import { Loading } from '../components/ui/Loading'
import { PageHeader } from '../components/ui/PageHeader'
import { ProgressBar } from '../components/ui/ProgressBar'
import { currentMonth, formatDate, formatEuro, formatSigned, parseAmountInput, todayIso } from '../format'

const ROLE_NAMES: Record<AccountRole, string> = { EVERYDAY: 'Alltag', SAVINGS: 'Sparen', DEPOT: 'Depot' }

/**
 * Konten (concept 4.4, replaces Einstellungen › Bankkonten): accounts by role with their app balance (P11), the
 * balance from the bank with the difference, the reserve pots with their account (P8) and the account management.
 */
export function AccountsPage() {
  const accounts = useAccounts()
  const month = currentMonth()
  const plan = usePlan(month)
  const updateAccount = useUpdateBankAccount()
  const linkReserve = useLinkReserve()
  const reserves = useReservesByAccount()
  const goals = useSavingGoals()
  const [reconciling, setReconciling] = useState<AccountLine | null>(null)
  // 27f4: the goal in the dialog; 'new' for a new one
  const [editGoal, setEditGoal] = useState<SavingGoal | 'new' | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (accounts.isPending) {
    return <Loading text="Lade Konten …" />
  }
  if (accounts.error) {
    return <p className="error">Konten konnten nicht geladen werden: {accounts.error.message}</p>
  }
  const savingsAccounts = accounts.data.filter((g) => g.role !== 'EVERYDAY').flatMap((g) => g.accounts)

  async function changeRole(account: AccountLine, role: AccountRole) {
    setError(null)
    try {
      await updateAccount.mutateAsync({
        id: account.id,
        input: { name: account.name, iban: account.iban, archived: account.archived, defaultAccount: account.defaultAccount, role },
      })
    } catch (e) {
      setError(errorText(e, 'Die Rolle konnte nicht geändert werden.'))
    }
  }

  return (
    <section>
      <PageHeader title="Konten" />
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {accounts.data
        .filter((group) => group.accounts.length > 0)
        .map((group) => (
          <section key={group.role} className="card account-group" aria-label={ROLE_NAMES[group.role]}>
            <div className="plan-group-head">
              <h2>{ROLE_NAMES[group.role]}</h2>
              <span className="plan-group-total">{formatEuro(group.total)}</span>
            </div>
            <ul className="item-list">
              {group.accounts.map((account) => (
                <li key={account.id} className="account-row">
                  <span className="item-main">
                    <span className="item-name">{account.name}</span>
                    <span className="tx-meta">
                      {account.statementDate
                        ? `Bank am ${formatDate(account.statementDate)}: ${formatEuro(account.statementAmount)}`
                        : 'noch kein Kontostand eingetragen'}
                      {account.comfortAmount !== null && (
                        <span className={account.belowComfort ? 'below-comfort' : undefined}>
                          {' '}
                          · {account.belowComfort ? 'unter ' : ''}Wohlfühlbetrag {formatEuro(account.comfortAmount)}
                        </span>
                      )}
                    </span>
                  </span>
                  <select
                    aria-label={`Kontoart von ${account.name}`}
                    value={account.role}
                    onChange={(e) => void changeRole(account, e.target.value as AccountRole)}
                  >
                    {(Object.keys(ROLE_NAMES) as AccountRole[]).map((role) => (
                      <option key={role} value={role}>
                        {ROLE_NAMES[role]}
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    className={account.belowComfort ? 'link-button item-amount below-comfort' : 'link-button item-amount'}
                    aria-label={`Kontostand von ${account.name} laut Bank eintragen`}
                    onClick={() => setReconciling(account)}
                  >
                    {formatEuro(account.balance)}
                  </button>
                </li>
              ))}
            </ul>
          </section>
        ))}

      {(reserves.data ?? []).length > 0 && (
        <section className="card account-group" aria-label="Rücklagen">
          <h2>Rücklagen</h2>
          <ul className="budget-list">
            {(reserves.data ?? []).map((group) => (
              <ReserveGroupRow
                key={group.accountId ?? 'none'}
                group={group}
                accountOf={(itemId) => plan.data?.items.find((i) => i.id === itemId)?.reserveAccountId ?? null}
                savingsAccounts={savingsAccounts}
                onLink={(itemId, accountId) => linkReserve.mutate({ itemId, accountId })}
              />
            ))}
          </ul>
        </section>
      )}

      {/* 27f4: the saving goals are managed here */}
      <section className="card account-group" aria-label="Sparziele">
        <div className="plan-group-head">
          <h2>Sparziele</h2>
          <button type="button" className="link-button" onClick={() => setEditGoal('new')}>
            + Neues Sparziel
          </button>
        </div>
        {(goals.data ?? []).length === 0 ? (
          <p className="hint">Noch keine Sparziele.</p>
        ) : (
          <ul className="budget-list">
            {(goals.data ?? []).map((goal) => (
              <li key={goal.id} className="budget-item">
                <button type="button" className="budget-head budget-toggle" onClick={() => setEditGoal(goal)}>
                  <span className="budget-name">
                    {goal.name}
                    {goal.targetDate && <span className="tx-meta"> · bis {formatDate(goal.targetDate)}</span>}
                  </span>
                  <span className="budget-amount">
                    <span className="saving">
                      {formatEuro(goal.saved)}
                      {goal.targetAmount !== null && ` / ${formatEuro(goal.targetAmount)}`}
                    </span>
                    {goal.monthly > 0 && <span className="budget-left">{formatEuro(goal.monthly)} im Monat</span>}
                  </span>
                </button>
                {goal.targetAmount !== null && goal.targetAmount > 0 && (
                  <div className="saving-progress">
                    <ProgressBar value={goal.saved / goal.targetAmount} />
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <div className="card">
        <BankAccountSettings />
      </div>

      {reconciling && <ReconcileDialog account={reconciling} onClose={() => setReconciling(null)} />}
      {editGoal && <SavingGoalDialog goal={editGoal === 'new' ? null : editGoal} onClose={() => setEditGoal(null)} />}
    </section>
  )
}

/**
 * 27f2: the reserves of one savings account (or without account) at a glance: saved of target with a bar, and on the
 * right ✓ when the account balance covers them, otherwise what is missing. Open it shows the single reserves.
 */
function ReserveGroupRow({
  group,
  accountOf,
  savingsAccounts,
  onLink,
}: {
  group: ReserveGroup
  accountOf: (itemId: number) => number | null
  savingsAccounts: AccountLine[]
  onLink: (itemId: number, accountId: number | null) => void
}) {
  const [open, setOpen] = useState(false)
  const missing = group.missing ?? 0
  return (
    <li className="budget-item">
      <button type="button" className="budget-head budget-toggle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span className="budget-name">{group.accountName ?? 'Ohne Konto'}</span>
        <span className="budget-amount">
          <span>
            {formatEuro(group.saved)} / {formatEuro(group.target)}
          </span>
          {group.accountId !== null &&
            (missing > 0 ? (
              <span className="budget-left over">−{formatEuro(missing)}</span>
            ) : (
              <span className="budget-left covered" aria-label="gedeckt">
                ✓
              </span>
            ))}
        </span>
      </button>
      <div className="saving-progress">
        <ProgressBar value={group.target > 0 ? group.saved / group.target : 0} />
      </div>
      {/* 27f4: what the orange amount or the tick means, with the formula and the numbers */}
      {group.accountId !== null && group.accountBalance !== null && (
        <p className="breakdown reserve-cover">
          <span>
            {missing > 0
              ? `Auf ${group.accountName} fehlen ${formatEuro(missing)}`
              : `${group.accountName} deckt die Rücklagen`}
          </span>{' '}
          <InfoTip label="Wie wird das gerechnet?">
            Fehlt = Summe der Rücklagen − Kontostand in der App = {formatEuro(group.saved)} −{' '}
            {formatEuro(group.accountBalance)}
            {missing > 0 ? ` = ${formatEuro(missing)}.` : ' ≤ 0, also gedeckt.'} Eine Rücklage zählt mit dem, was
            nach den Zahlungen dieses Monats noch in ihr liegt; die Monatsraten legst du per Umbuchung auf dieses Konto.
          </InfoTip>
        </p>
      )}
      {open && (
        <ul className="item-list">
          {group.pots.map((pot) => (
            <li key={pot.itemId} className="account-row">
              <span className="item-main">
                <span className="item-name">{pot.name}</span>
                <span className="tx-meta">
                  {formatEuro(pot.balance)} / {formatEuro(pot.amount)}
                  {pot.nextDue && ` · fällig ${formatDate(pot.nextDue)}`}
                </span>
              </span>
              <select
                aria-label={`Konto der Rücklage ${pot.name}`}
                value={accountOf(pot.itemId) ?? ''}
                onChange={(e) => onLink(pot.itemId, e.target.value === '' ? null : Number(e.target.value))}
              >
                <option value="">ohne Konto</option>
                {savingsAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.name}
                  </option>
                ))}
              </select>
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}

/** P11: the balance from the bank on a day; shows the difference to the app balance and books nothing. */
function ReconcileDialog({ account, onClose }: { account: AccountLine; onClose: () => void }) {
  const reconcile = useReconcile()
  const setComfort = useSetComfortAmount()
  const [date, setDate] = useState(todayIso())
  const [amount, setAmount] = useState('')
  const initialComfort = account.comfortAmount === null ? '' : account.comfortAmount.toFixed(2).replace('.', ',')
  const [comfort, setComfortText] = useState(initialComfort)
  const [error, setError] = useState<string | null>(null)
  const [comfortSaved, setComfortSaved] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const comfortChanged = comfort.trim() !== initialComfort
    const comfortValue = comfort.trim() === '' ? null : parseAmountInput(comfort.trim())
    if (comfortChanged && comfort.trim() !== '' && comfortValue === null) {
      setError('Bitte einen Wohlfühlbetrag wie 500,00 eingeben.')
      return
    }
    if (amount.trim() === '' && !comfortChanged) {
      setError('Bitte einen Betrag wie 1.234,56 eingeben.')
      return
    }
    // Negative balances are allowed: "-12,50"
    const negative = amount.trim().startsWith('-')
    const parsed = amount.trim() === '' ? null : parseAmountInput(amount.trim().replace(/^-/, ''))
    if (amount.trim() !== '' && parsed === null) {
      setError('Bitte einen Betrag wie 1.234,56 eingeben.')
      return
    }
    setError(null)
    try {
      if (comfortChanged) {
        await setComfort.mutateAsync({ accountId: account.id, amount: comfortValue })
        setComfortSaved(true)
      }
      if (parsed !== null) {
        await reconcile.mutateAsync({ accountId: account.id, date, amount: negative ? -parsed : parsed })
      } else {
        onClose()
      }
    } catch (e) {
      setError(errorText(e, 'Der Kontostand konnte nicht gespeichert werden.'))
    }
  }

  const result = reconcile.data
  return (
    <Dialog title={`Kontostand ${account.name}`} onClose={onClose}>
      <form className="booking-form" noValidate onSubmit={(e) => void handleSubmit(e)}>
        <div className="field-row">
          <label className="field">
            Stichtag
            <input type="date" value={date} max={todayIso()} onChange={(e) => setDate(e.target.value)} />
          </label>
          <label className="field">
            Kontostand laut Bank
            <input inputMode="decimal" placeholder="0,00" value={amount} onChange={(e) => setAmount(e.target.value)} />
          </label>
        </div>
        {/* 27f4: below it the balance turns orange */}
        <label className="field">
          Wohlfühlbetrag (optional)
          <input
            inputMode="decimal"
            placeholder="z. B. 500,00"
            value={comfort}
            onChange={(e) => {
              setComfortText(e.target.value)
              setComfortSaved(false)
            }}
          />
        </label>
        {comfortSaved && (
          <p className="hint" role="status">
            Wohlfühlbetrag gespeichert.
          </p>
        )}
        {error && <p className="error">{error}</p>}
        {result && (
          <p className={result.difference === 0 ? 'plan-done' : 'hint'} role="status">
            {result.difference === 0
              ? '✓ Stimmt mit der App überein.'
              : `Die App hatte ${formatEuro(result.appBalance)}. Differenz ${formatSigned(result.difference)}: fehlt eine Buchung?`}
          </p>
        )}
        <div className="dialog-actions">
          <div />
          <div className="dialog-buttons">
            <button type="button" onClick={onClose}>
              Schließen
            </button>
            <button type="submit" disabled={reconcile.isPending}>
              Speichern
            </button>
          </div>
        </div>
      </form>
    </Dialog>
  )
}
