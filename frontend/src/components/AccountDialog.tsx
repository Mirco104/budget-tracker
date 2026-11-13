import { useState, type FormEvent } from 'react'
import { useReconcile, useSetComfortAmount } from '../api/accounts'
import { useBankAccounts, useCreateBankAccount, useDeleteBankAccount, useUpdateBankAccount } from '../api/bankAccounts'
import { errorText } from '../api/client'
import type { AccountLine, AccountRole } from '../api/types'
import { formatEuro, formatSigned, parseAmountInput, todayIso } from '../format'
import { ROLE_NAMES } from '../accountRoles'
import { Dialog } from './ui/Dialog'


function toAmountText(amount: number | null): string {
  return amount === null ? '' : amount.toFixed(2).replace('.', ',')
}

/**
 * 27f4 (decision 60): one window per account on the page Konten, for a new account (account null) and for editing
 * one: name, IBAN, Kontoart, default account, comfort amount, the balance from the bank (P11, books nothing),
 * archive and delete (US30).
 */
export function AccountDialog({ account, onClose }: { account: AccountLine | null; onClose: () => void }) {
  const create = useCreateBankAccount()
  const update = useUpdateBankAccount()
  const remove = useDeleteBankAccount()
  const reconcile = useReconcile()
  const setComfort = useSetComfortAmount()
  const used = useBankAccounts().data?.find((a) => a.id === account?.id)?.used ?? true

  const [name, setName] = useState(account?.name ?? '')
  const [iban, setIban] = useState(account?.iban ?? '')
  const [role, setRole] = useState<AccountRole>(account?.role ?? 'EVERYDAY')
  const [defaultAccount, setDefaultAccount] = useState(account?.defaultAccount ?? false)
  const initialComfort = toAmountText(account?.comfortAmount ?? null)
  const [comfort, setComfortText] = useState(initialComfort)
  const [date, setDate] = useState(todayIso())
  const [balance, setBalance] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const busy = create.isPending || update.isPending || remove.isPending || reconcile.isPending || setComfort.isPending

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (name.trim() === '') {
      setError('Der Name ist Pflicht.')
      return
    }
    const comfortValue = comfort.trim() === '' ? null : parseAmountInput(comfort.trim())
    if (comfort.trim() !== '' && comfortValue === null) {
      setError('Bitte einen Wohlfühlbetrag wie 500,00 eingeben.')
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
      if (id !== undefined && comfort.trim() !== initialComfort) {
        await setComfort.mutateAsync({ accountId: id, amount: comfortValue })
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
        <label className="field">
          Wohlfühlbetrag (optional)
          <input
            inputMode="decimal"
            placeholder="z. B. 500,00"
            value={comfort}
            onChange={(e) => setComfortText(e.target.value)}
          />
        </label>
        <span className="hint">Liegt der Kontostand darunter, wird er orange.</span>
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
        {confirmingDelete ? (
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
