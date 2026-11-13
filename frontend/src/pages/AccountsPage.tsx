import { useState } from 'react'
import { useAccounts, useLinkReserve, useReservesByAccount } from '../api/accounts'
import { useSavingGoals } from '../api/goals'
import { usePlan } from '../api/plan'
import type { AccountLine, ReserveGroup, SavingGoal } from '../api/types'
import { ROLE_NAMES } from '../accountRoles'
import { AccountDialog } from '../components/AccountDialog'
import { SavingGoalDialog } from '../components/SavingGoalDialog'
import { InfoTip } from '../components/ui/InfoTip'
import { Loading } from '../components/ui/Loading'
import { PageHeader } from '../components/ui/PageHeader'
import { ProgressBar } from '../components/ui/ProgressBar'
import { currentMonth, formatDate, formatEuro } from '../format'

/**
 * Konten (concept 4.4, replaces Einstellungen › Bankkonten): accounts by role with their app balance (P11), the
 * balance from the bank with the difference, the reserve pots with their account (P8) and the account management.
 */
export function AccountsPage() {
  const accounts = useAccounts()
  const month = currentMonth()
  const plan = usePlan(month)
  const linkReserve = useLinkReserve()
  const reserves = useReservesByAccount()
  const goals = useSavingGoals()
  // 27f4 (decision 60): the account in the window; 'new' for a new one
  const [editAccount, setEditAccount] = useState<AccountLine | 'new' | null>(null)
  const [archivedOpen, setArchivedOpen] = useState(false)
  // 27f4: the goal in the dialog; 'new' for a new one
  const [editGoal, setEditGoal] = useState<SavingGoal | 'new' | null>(null)

  if (accounts.isPending) {
    return <Loading text="Lade Konten …" />
  }
  if (accounts.error) {
    return <p className="error">Konten konnten nicht geladen werden: {accounts.error.message}</p>
  }
  // 27f4 (decision 60): any active account may hold a reserve
  const all = accounts.data.flatMap((g) => g.accounts)
  const reserveAccounts = all.filter((a) => !a.archived)
  const archived = all.filter((a) => a.archived)

  return (
    <section>
      <PageHeader title="Konten" />
      {/* 27f4 (decision 60): one card with the accounts by Kontoart; a row opens the window of the account */}
      <section className="card account-group" aria-label="Bankkonten">
        <div className="plan-group-head">
          <h2>Bankkonten</h2>
          <button type="button" className="link-button" onClick={() => setEditAccount('new')}>
            + Neues Konto
          </button>
        </div>
        {accounts.data
          .map((group) => ({ ...group, accounts: group.accounts.filter((a) => !a.archived) }))
          .filter((group) => group.accounts.length > 0)
          .map((group) => (
            <div key={group.role} className="account-role" role="group" aria-label={ROLE_NAMES[group.role]}>
              <div className="account-role-head">
                <h3>{ROLE_NAMES[group.role]}</h3>
                <span>{formatEuro(group.total)}</span>
              </div>
              <ul className="item-list">
                {group.accounts.map((account) => (
                  <AccountRow key={account.id} account={account} onOpen={() => setEditAccount(account)} />
                ))}
              </ul>
            </div>
          ))}
        {/* 27f4: the total of all accounts, like the total row of the budget */}
        <div className="account-total budget-total">
          <span>Gesamt</span>
          <span>{formatEuro(totalOf(accounts.data))}</span>
        </div>
        {archived.length > 0 && (
          <div className="account-role">
            <button
              type="button"
              className="link-button"
              aria-expanded={archivedOpen}
              onClick={() => setArchivedOpen((v) => !v)}
            >
              Archiviert ({archived.length}) {archivedOpen ? '▴' : '▾'}
            </button>
            {archivedOpen && (
              <ul className="item-list">
                {archived.map((account) => (
                  <AccountRow key={account.id} account={account} onOpen={() => setEditAccount(account)} />
                ))}
              </ul>
            )}
          </div>
        )}
      </section>

      {(reserves.data ?? []).length > 0 && (
        <section className="card account-group" aria-label="Rücklagen">
          <h2>Rücklagen</h2>
          <ul className="budget-list">
            {(reserves.data ?? []).map((group) => (
              <ReserveGroupRow
                key={group.accountId ?? 'none'}
                group={group}
                accountOf={(itemId) => plan.data?.items.find((i) => i.id === itemId)?.reserveAccountId ?? null}
                reserveAccounts={reserveAccounts}
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

      {editAccount && (
        <AccountDialog account={editAccount === 'new' ? null : editAccount} onClose={() => setEditAccount(null)} />
      )}
      {editGoal && <SavingGoalDialog goal={editGoal === 'new' ? null : editGoal} onClose={() => setEditGoal(null)} />}
    </section>
  )
}

/**
 * 27f2: the reserves of one account (or without account) at a glance: saved of target with a bar and below it whether
 * the account covers them (27f4: the tick stands in that line). Open it shows the single reserves as a table with
 * the account each one lies on.
 */
function ReserveGroupRow({
  group,
  accountOf,
  reserveAccounts,
  onLink,
}: {
  group: ReserveGroup
  accountOf: (itemId: number) => number | null
  reserveAccounts: AccountLine[]
  onLink: (itemId: number, accountId: number | null) => void
}) {
  const [open, setOpen] = useState(false)
  const missing = group.missing ?? 0
  return (
    <li className="budget-item">
      <button type="button" className="budget-head budget-toggle" aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <span className="budget-name">
          {group.accountName ?? 'Ohne Konto'}{' '}
          <span className="summary-chevron-inline" aria-hidden="true">
            {open ? '▴' : '▾'}
          </span>
        </span>
        <span className="budget-amount">
          <span>
            {formatEuro(group.saved)} / {formatEuro(group.target)}
          </span>
        </span>
      </button>
      <div className="saving-progress">
        <ProgressBar value={group.target > 0 ? group.saved / group.target : 0} />
      </div>
      {/* 27f4: whether the account covers its reserves, in words everyone understands */}
      {group.accountId !== null && group.accountBalance !== null && (
        <p className={missing > 0 ? 'breakdown reserve-cover short' : 'breakdown reserve-cover'}>
          <span>
            {missing > 0
              ? `Auf ${group.accountName} fehlen ${formatEuro(missing)}`
              : `✓ ${group.accountName} deckt die Rücklagen`}
          </span>{' '}
          <InfoTip label="Was heißt das?">
            Für deine Rücklagen sollten auf {group.accountName} {formatEuro(group.saved)} liegen. Laut App liegen dort{' '}
            {formatEuro(group.accountBalance)}. {missing > 0 ? `Es fehlen ${formatEuro(missing)}.` : 'Das reicht.'}
          </InfoTip>
        </p>
      )}
      {open && (
        <table className="reserve-table">
          <thead>
            <tr>
              <th scope="col">Rücklage</th>
              <th scope="col">Angespart / Betrag</th>
              <th scope="col">Liegt auf</th>
            </tr>
          </thead>
          <tbody>
            {group.pots.map((pot) => (
              <tr key={pot.itemId}>
                <td>
                  <span className="item-name">{pot.name}</span>
                  {pot.nextDue && <span className="tx-meta"> fällig {formatDate(pot.nextDue)}</span>}
                </td>
                <td className="reserve-amount">
                  {formatEuro(pot.balance)} / {formatEuro(pot.amount)}
                </td>
                <td>
                  <label className="reserve-account">
                    <span className="mobile-label">Liegt auf</span>
                    <select
                      aria-label={`Konto der Rücklage ${pot.name}`}
                      value={accountOf(pot.itemId) ?? ''}
                      onChange={(e) => onLink(pot.itemId, e.target.value === '' ? null : Number(e.target.value))}
                    >
                      <option value="">ohne Konto</option>
                      {reserveAccounts.map((account) => (
                        <option key={account.id} value={account.id}>
                          {account.name}
                        </option>
                      ))}
                    </select>
                  </label>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </li>
  )
}

/** The balance of all accounts, added in whole cents (display only). */
function totalOf(groups: { total: number }[]): number {
  return groups.reduce((sum, group) => sum + Math.round(group.total * 100), 0) / 100
}

/** 27f4 (decision 60): name, hint and the app balance (P11); the whole row opens the window of the account. */
function AccountRow({ account, onOpen }: { account: AccountLine; onOpen: () => void }) {
  return (
    <li>
      <button type="button" className="account-row account-open" onClick={onOpen} aria-label={`Konto ${account.name} bearbeiten`}>
        <span className="item-main">
          <span className="item-name">
            {account.name}
            {account.defaultAccount && <span className="badge">Standard</span>}
          </span>
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
        <span className={account.belowComfort ? 'item-amount below-comfort' : 'item-amount'}>
          {formatEuro(account.balance)}
        </span>
      </button>
    </li>
  )
}
