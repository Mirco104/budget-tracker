import { useState, type FormEvent } from 'react'
import { useAccounts, useLinkReserve, useReconcile } from '../api/accounts'
import { useUpdateBankAccount } from '../api/bankAccounts'
import { errorText } from '../api/client'
import { useDashboard } from '../api/dashboard'
import { usePlan } from '../api/plan'
import type { AccountLine, AccountRole } from '../api/types'
import { BankAccountSettings } from '../components/BankAccountSettings'
import { Dialog } from '../components/ui/Dialog'
import { Loading } from '../components/ui/Loading'
import { PageHeader } from '../components/ui/PageHeader'
import { currentMonth, formatDate, formatEuro, formatSigned, parseAmountInput, todayIso } from '../format'

const ROLE_NAMES: Record<AccountRole, string> = { EVERYDAY: 'Alltag', SAVINGS: 'Sparen', DEPOT: 'Depot' }

/**
 * Konten (concept 4.4, replaces Einstellungen › Bankkonten): accounts by role with their app balance (P11), the
 * balance from the bank with the difference, the reserve pots with their account (P8) and the account management.
 */
export function AccountsPage() {
  const accounts = useAccounts()
  const month = currentMonth()
  const dashboard = useDashboard(month)
  const plan = usePlan(month)
  const updateAccount = useUpdateBankAccount()
  const linkReserve = useLinkReserve()
  const [reconciling, setReconciling] = useState<AccountLine | null>(null)
  const [error, setError] = useState<string | null>(null)

  if (accounts.isPending) {
    return <Loading text="Lade Konten …" />
  }
  if (accounts.error) {
    return <p className="error">Konten konnten nicht geladen werden: {accounts.error.message}</p>
  }
  const savingsAccounts = accounts.data.filter((g) => g.role !== 'EVERYDAY').flatMap((g) => g.accounts)
  const pots = dashboard.data?.reserves ?? []

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
                    className="link-button item-amount"
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

      {pots.length > 0 && (
        <section className="card account-group" aria-label="Rücklagen">
          <h2>Rücklagen</h2>
          <ul className="item-list">
            {pots.map((pot) => {
              const item = plan.data?.items.find((i) => i.id === pot.itemId)
              return (
                <li key={pot.itemId} className="account-row">
                  <span className="item-main">
                    <span className="item-name">{pot.name}</span>
                    <span className="tx-meta">
                      {formatEuro(pot.balance)} von {formatEuro(pot.amount)} · monatlich {formatEuro(pot.monthly)}
                      {pot.nextDue && ` · fällig am ${formatDate(pot.nextDue)}`}
                    </span>
                  </span>
                  <select
                    aria-label={`Konto der Rücklage ${pot.name}`}
                    value={item?.reserveAccountId ?? ''}
                    onChange={(e) =>
                      linkReserve.mutate({ itemId: pot.itemId, accountId: e.target.value === '' ? null : Number(e.target.value) })
                    }
                  >
                    <option value="">ohne Konto (gerechnet)</option>
                    {savingsAccounts.map((account) => (
                      <option key={account.id} value={account.id}>
                        {account.name}
                      </option>
                    ))}
                  </select>
                </li>
              )
            })}
          </ul>
        </section>
      )}

      <div className="card">
        <BankAccountSettings />
      </div>

      {reconciling && <ReconcileDialog account={reconciling} onClose={() => setReconciling(null)} />}
    </section>
  )
}

/** P11: the balance from the bank on a day; shows the difference to the app balance and books nothing. */
function ReconcileDialog({ account, onClose }: { account: AccountLine; onClose: () => void }) {
  const reconcile = useReconcile()
  const [date, setDate] = useState(todayIso())
  const [amount, setAmount] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // Negative balances are allowed: "-12,50"
    const negative = amount.trim().startsWith('-')
    const parsed = parseAmountInput(amount.trim().replace(/^-/, ''))
    if (parsed === null) {
      setError('Bitte einen Betrag wie 1.234,56 eingeben.')
      return
    }
    setError(null)
    try {
      await reconcile.mutateAsync({ accountId: account.id, date, amount: negative ? -parsed : parsed })
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
