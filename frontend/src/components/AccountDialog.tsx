import { useState, type FormEvent } from 'react'
import { useReconcile, useSetLowerLimit } from '../api/accounts'
import {
  useBankAccounts,
  useCreateBankAccount,
  useDeleteBankAccount,
  useMergeBankAccount,
  useUpdateBankAccount,
} from '../api/bankAccounts'
import { errorText } from '../api/client'
import type { AccountLine, AccountRole, LowerLimitMode } from '../api/types'
import { formatEuro, formatSigned, parseAmountInput, todayIso } from '../format'
import { ROLE_NAMES } from '../accountRoles'
import { Dialog } from './ui/Dialog'
import { InfoTip } from './ui/InfoTip'

function toAmountText(amount: number | null): string {
  return amount === null ? '' : amount.toFixed(2).replace('.', ',')
}

/**
 * 27f4 (decision 60): one window per account on the page Konten, for a new account (account null) and for editing
 * one: name, IBAN, Kontoart, default account, lower limit (P12, 27h), the balance from the bank (P11, books nothing),
 * archive and delete (US30), and since ticket #139 merge into another account.
 */
export function AccountDialog({
  account,
  initialName = '',
  onCreated,
  onClose,
}: {
  account: AccountLine | null
  /** Decision 66: the name a new account starts with, e.g. the suggestion of the import. */
  initialName?: string
  /** Decision 66: gets the id of a new account, e.g. to choose it in the selection that opened the window. */
  onCreated?: (id: number) => void
  onClose: () => void
}) {
  const create = useCreateBankAccount()
  const update = useUpdateBankAccount()
  const remove = useDeleteBankAccount()
  const reconcile = useReconcile()
  const setLowerLimit = useSetLowerLimit()
  const merge = useMergeBankAccount()
  const bankAccounts = useBankAccounts().data ?? []
  const used = bankAccounts.find((a) => a.id === account?.id)?.used ?? true
  // Ticket #139: an archived account takes over nothing
  const mergeTargets = bankAccounts.filter((a) => a.id !== account?.id && !a.archived)

  const [name, setName] = useState(account?.name ?? initialName)
  const [iban, setIban] = useState(account?.iban ?? '')
  const [role, setRole] = useState<AccountRole>(account?.role ?? 'EVERYDAY')
  const [defaultAccount, setDefaultAccount] = useState(account?.defaultAccount ?? false)
  // P12: a new everyday account starts at 0 €, a savings or depot account without a limit
  const initialMode: LowerLimitMode | null = account?.lowerLimitMode ?? null
  const [limitMode, setLimitMode] = useState<LowerLimitMode | null>(initialMode)
  const mode: LowerLimitMode = limitMode ?? (role === 'EVERYDAY' ? 'ZERO' : 'OFF')
  const initialLimit = account?.lowerLimitMode === 'CUSTOM' ? toAmountText(account.lowerLimit) : ''
  const [limitText, setLimitText] = useState(initialLimit)
  const [date, setDate] = useState(todayIso())
  const [balance, setBalance] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const [merging, setMerging] = useState(false)
  const [mergeTarget, setMergeTarget] = useState('')
  const busy =
    create.isPending ||
    update.isPending ||
    remove.isPending ||
    reconcile.isPending ||
    setLowerLimit.isPending ||
    merge.isPending

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (name.trim() === '') {
      setError('Der Name ist Pflicht.')
      return
    }
    const limitValue = mode === 'CUSTOM' ? parseAmountInput(limitText.trim()) : null
    if (mode === 'CUSTOM' && limitValue === null) {
      setError('Bitte eine Untergrenze wie 500,00 eingeben.')
      return
    }
    // Negative balances are allowed: "-12,50"
    const negative = balance.trim().startsWith('-')
    const parsed = balance.trim() === '' ? null : parseAmountInput(balance.trim().replace(/^-/, ''))
    if (balance.trim() !== '' && parsed === null) {
      setError('Bitte einen Kontostand wie 1.234,56 eingeben.')
      return
    }
    setError(null)
    const ibanValue = iban.trim() === '' ? null : iban.trim()
    try {
      let id = account?.id
      if (account === null) {
        id = (await create.mutateAsync({ name: name.trim(), iban: ibanValue, role })).id
        onCreated?.(id)
      } else if (
        name.trim() !== account.name ||
        ibanValue !== account.iban ||
        role !== account.role ||
        defaultAccount !== account.defaultAccount
      ) {
        await update.mutateAsync({
          id: account.id,
          input: { name: name.trim(), iban: ibanValue, archived: account.archived, defaultAccount, role },
        })
      }
      const limitChanged = account === null ? limitMode !== null : mode !== initialMode || limitText.trim() !== initialLimit
      if (id !== undefined && limitChanged) {
        await setLowerLimit.mutateAsync({ accountId: id, mode, amount: limitValue })
      }
      if (id !== undefined && parsed !== null) {
        // The window stays open and shows the difference to the app balance
        await reconcile.mutateAsync({ accountId: id, date, amount: negative ? -parsed : parsed })
        setBalance('')
      } else {
        onClose()
      }
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
    }
  }

  async function archive(archived: boolean) {
    if (account === null) {
      return
    }
    setError(null)
    try {
      await update.mutateAsync({
        id: account.id,
        input: { name: account.name, iban: account.iban, archived, defaultAccount: false, role: account.role },
      })
      onClose()
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
    }
  }

  async function handleDelete() {
    if (account === null) {
      return
    }
    try {
      await remove.mutateAsync(account.id)
      onClose()
    } catch (e) {
      setError(errorText(e, 'Löschen fehlgeschlagen.'))
      setConfirmingDelete(false)
    }
  }

  async function handleMerge() {
    if (account === null || mergeTarget === '') {
      return
    }
    setError(null)
    try {
      await merge.mutateAsync({ id: account.id, targetId: Number(mergeTarget) })
      onClose()
    } catch (e) {
      setError(errorText(e, 'Zusammenführen fehlgeschlagen.'))
    }
  }

  const targetName = mergeTargets.find((a) => String(a.id) === mergeTarget)?.name
  const result = reconcile.data
  return (
    <Dialog title={account ? `Konto ${account.name}` : 'Neues Konto'} onClose={onClose}>
      <form className="booking-form" noValidate onSubmit={(e) => void handleSubmit(e)}>
        <div className="field-row">
          <label className="field">
            Name
            <input value={name} maxLength={50} onChange={(e) => setName(e.target.value)} />
          </label>
          <label className="field">
            Kontoart
            <select value={role} onChange={(e) => setRole(e.target.value as AccountRole)}>
              {(Object.keys(ROLE_NAMES) as AccountRole[]).map((r) => (
                <option key={r} value={r}>
                  {ROLE_NAMES[r]}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label className="field">
          IBAN (optional)
          <input
            value={iban}
            maxLength={42}
            placeholder="DE00 0000 0000 0000 0000 00"
            autoComplete="off"
            onChange={(e) => setIban(e.target.value)}
          />
        </label>
        <span className="hint">Mit IBAN erkennt der Import Umbuchungen auf dieses Konto.</span>
        {account !== null && !account.archived && (
          <label className="check-field">
            <input
              type="checkbox"
              checked={defaultAccount}
              disabled={account.defaultAccount}
              onChange={(e) => setDefaultAccount(e.target.checked)}
            />
            Standardkonto (Buchungen ohne Auswahl landen hier)
          </label>
        )}
        {/* P12 (27h, decision 65): replaces the comfort amount */}
        <div className="field">
          <span id="account-limit-label">
            Untergrenze{' '}
            <InfoTip label="Was löst die Untergrenze aus?">
              Unter der Grenze wird der Kontostand orange. Fällt er voraussichtlich darunter, weil noch Zahlungen erwartet
              werden, erscheint eine Benachrichtigung (Glocke).
            </InfoTip>
          </span>
          <div className="field-row limit-row">
            <select
              aria-labelledby="account-limit-label"
              value={mode}
              onChange={(e) => setLimitMode(e.target.value as LowerLimitMode)}
            >
              <option value="OFF">Aus</option>
              <option value="ZERO">0 €</option>
              <option value="CUSTOM">Eigener Betrag</option>
            </select>
            {mode === 'CUSTOM' && (
              <input
                inputMode="decimal"
                aria-label="Eigene Untergrenze"
                placeholder="z. B. 500,00"
                value={limitText}
                onChange={(e) => setLimitText(e.target.value)}
              />
            )}
          </div>
        </div>
        {account !== null && (
          <div className="field-row">
            <label className="field">
              Stichtag
              <input type="date" value={date} max={todayIso()} onChange={(e) => setDate(e.target.value)} />
            </label>
            <label className="field">
              Kontostand laut Bank (optional)
              <input inputMode="decimal" placeholder="0,00" value={balance} onChange={(e) => setBalance(e.target.value)} />
            </label>
          </div>
        )}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        {result && (
          <p className={result.difference === 0 ? 'plan-done' : 'hint'} role="status">
            {result.difference === 0
              ? '✓ Stimmt mit der App überein.'
              : `Die App hatte ${formatEuro(result.appBalance)}. Differenz ${formatSigned(result.difference)}: fehlt eine Buchung?`}
          </p>
        )}
        {merging ? (
          <div className="dialog-actions confirm merge-confirm" role="group" aria-label="Zusammenführen">
            <label className="field">
              In Konto
              <select value={mergeTarget} onChange={(e) => setMergeTarget(e.target.value)}>
                <option value="">Konto wählen …</option>
                {mergeTargets.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}
                  </option>
                ))}
              </select>
            </label>
            <p className="hint">
              Alle Buchungen, geplanten Posten und Rücklagen von „{account?.name}“ gehen auf{' '}
              {targetName ? `„${targetName}“` : 'das gewählte Konto'} über. Umbuchungen zwischen den beiden Konten werden
              gelöscht, ebenso die Kontostände von „{account?.name}“. Danach wird „{account?.name}“ gelöscht
              {account?.defaultAccount ? ' und das Zielkonto wird Standardkonto' : ''}.
            </p>
            <div className="dialog-buttons">
              <button type="button" onClick={() => setMerging(false)} disabled={busy}>
                Abbrechen
              </button>
              <button
                type="button"
                className="primary"
                onClick={() => void handleMerge()}
                disabled={busy || mergeTarget === ''}
              >
                Zusammenführen
              </button>
            </div>
          </div>
        ) : confirmingDelete ? (
          <div className="dialog-actions confirm" role="group" aria-label="Löschen bestätigen">
            <span>„{account?.name}“ löschen?</span>
            <div className="dialog-buttons">
              <button type="button" onClick={() => setConfirmingDelete(false)} disabled={busy}>
                Abbrechen
              </button>
              <button type="button" className="danger" onClick={() => void handleDelete()} disabled={busy}>
                Endgültig löschen
              </button>
            </div>
          </div>
        ) : (
          <div className="dialog-actions">
            <div className="dialog-buttons">
              {account !== null && !account.defaultAccount && (
                <button type="button" disabled={busy} onClick={() => void archive(!account.archived)}>
                  {account.archived ? 'Wiederherstellen' : 'Archivieren'}
                </button>
              )}
              {account !== null && mergeTargets.length > 0 && (
                <button type="button" disabled={busy} onClick={() => setMerging(true)}>
                  Zusammenführen …
                </button>
              )}
              {account !== null && !used && (
                <button type="button" className="danger" disabled={busy} onClick={() => setConfirmingDelete(true)}>
                  Löschen
                </button>
              )}
            </div>
            <div className="dialog-buttons">
              <button type="button" onClick={onClose}>
                Schließen
              </button>
              <button type="submit" disabled={busy}>
                Speichern
              </button>
            </div>
          </div>
        )}
      </form>
    </Dialog>
  )
}
