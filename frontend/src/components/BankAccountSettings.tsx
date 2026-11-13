import { useRef, useState, type FormEvent } from 'react'
import {
  useBankAccounts,
  useCreateBankAccount,
  useDeleteBankAccount,
  useUpdateBankAccount,
} from '../api/bankAccounts'
import { errorText } from '../api/client'
import type { BankAccount, BankAccountUpdate, AccountRole } from '../api/types'
import { formatIban } from '../format'
import { CategoryIcon } from './ui/CategoryIcon'
import { Icon } from './ui/Icon'
import { RowMenu } from './ui/RowMenu'
import { Loading } from './ui/Loading'

/** Name and optional IBAN, for a new account and for editing one (R26). */
function AccountForm({
  title,
  initial,
  busy,
  onSubmit,
  onCancel,
  withRole = false,
}: {
  title: string
  initial: { name: string; iban: string }
  busy: boolean
  onSubmit: (name: string, iban: string, role: AccountRole) => Promise<string | null>
  onCancel: () => void
  /** 27f3: a new account asks for its Kontoart; editing changes it on the page Konten */
  withRole?: boolean
}) {
  const [name, setName] = useState(initial.name)
  const [iban, setIban] = useState(initial.iban)
  const [role, setRole] = useState<AccountRole>('EVERYDAY')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (name.trim() === '') {
      setError('Der Name ist Pflicht.')
      return
    }
    setError(await onSubmit(name.trim(), iban.trim(), role))
  }

  return (
    <form className="account-form" noValidate onSubmit={handleSubmit} aria-label={title}>
      <div className="field-row">
        <label className="field">
          Name
          <input
            // Opened on purpose, so typing can start right away
            autoFocus
            value={name}
            maxLength={50}
            onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => e.key === 'Escape' && onCancel()}
            aria-invalid={error ? true : undefined}
          />
        </label>
        <label className="field">
          IBAN (optional)
          <input
            value={iban}
            maxLength={42}
            placeholder="DE00 0000 0000 0000 0000 00"
            autoComplete="off"
            onChange={(e) => setIban(e.target.value)}
            onKeyDown={(e) => e.key === 'Escape' && onCancel()}
          />
        </label>
      </div>
      {withRole && (
        <label className="field">
          Kontoart
          <select value={role} onChange={(e) => setRole(e.target.value as AccountRole)}>
            <option value="EVERYDAY">Alltag</option>
            <option value="SAVINGS">Sparen</option>
            <option value="DEPOT">Depot</option>
          </select>
        </label>
      )}
      <p className="hint">Mit IBAN erkennt der Import Umbuchungen auf dieses Konto.</p>
      <div className="dialog-buttons">
        <button type="button" onClick={onCancel}>
          Abbrechen
        </button>
        <button type="submit" disabled={busy}>
          Speichern
        </button>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}

/** "+ Konto" opens the form for a new account. */
function NewAccount() {
  const create = useCreateBankAccount()
  const [open, setOpen] = useState(false)

  async function submit(name: string, iban: string, role: AccountRole): Promise<string | null> {
    try {
      await create.mutateAsync({ name, iban: iban === '' ? null : iban, role })
      setOpen(false)
      return null
    } catch (e) {
      return errorText(e, 'Anlegen fehlgeschlagen.')
    }
  }

  if (!open) {
    return (
      <button type="button" className="add-category" onClick={() => setOpen(true)}>
        <Icon name="plus" size={18} />
        Konto
      </button>
    )
  }
  return (
    <AccountForm
      title="Neues Konto"
      withRole
      initial={{ name: '', iban: '' }}
      busy={create.isPending}
      onSubmit={submit}
      onCancel={() => setOpen(false)}
    />
  )
}

type Mode = 'view' | 'edit' | 'confirmDelete'

/** One account with the menu "⋯": edit, make default, archive or restore, delete while unused (US30). */
function AccountRow({ account }: { account: BankAccount }) {
  const update = useUpdateBankAccount()
  const remove = useDeleteBankAccount()
  const [mode, setMode] = useState<Mode>('view')
  const [menuOpen, setMenuOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const menuButton = useRef<HTMLButtonElement>(null)
  const busy = update.isPending || remove.isPending

  async function send(changes: Partial<BankAccountUpdate>): Promise<string | null> {
    setError(null)
    try {
      await update.mutateAsync({
        id: account.id,
        input: {
          name: account.name,
          iban: account.iban,
          archived: account.archived,
          defaultAccount: account.defaultAccount,
          ...changes,
        },
      })
      return null
    } catch (e) {
      return errorText(e, 'Speichern fehlgeschlagen.')
    }
  }

  async function sendFromMenu(changes: Partial<BankAccountUpdate>) {
    setMenuOpen(false)
    setError(await send(changes))
  }

  async function handleDelete() {
    setError(null)
    try {
      await remove.mutateAsync(account.id)
    } catch (e) {
      setError(errorText(e, 'Löschen fehlgeschlagen.'))
      setMode('view')
    }
  }

  return (
    <li className={account.archived ? 'category-row archived' : 'category-row'} aria-label={account.name}>
      {mode === 'edit' ? (
        <AccountForm
          title={`${account.name} bearbeiten`}
          initial={{ name: account.name, iban: account.iban ?? '' }}
          busy={busy}
          onSubmit={async (name, iban) => {
            const failed = await send({ name, iban: iban === '' ? null : iban })
            if (!failed) {
              setMode('view')
            }
            return failed
          }}
          onCancel={() => setMode('view')}
        />
      ) : (
        <div className="category-line">
          <CategoryIcon icon="wallet" />
          <span className="category-name account-name">
            <span>
              {account.name}
              {account.defaultAccount && <span className="badge">Standard</span>}
            </span>
            {account.iban && <span className="hint">{formatIban(account.iban)}</span>}
          </span>
          {account.archived ? (
            <>
              <button type="button" disabled={busy} onClick={() => void sendFromMenu({ archived: false })}>
                Wiederherstellen
              </button>
              {!account.used && (
                <button type="button" className="danger" disabled={busy} onClick={() => setMode('confirmDelete')}>
                  Löschen
                </button>
              )}
            </>
          ) : (
            <div className="row-menu-anchor">
              <button
                ref={menuButton}
                type="button"
                className="icon-button"
                aria-label={`Aktionen für ${account.name}`}
                aria-expanded={menuOpen}
                disabled={busy}
                onClick={() => setMenuOpen(!menuOpen)}
              >
                <Icon name="more" />
              </button>
              {menuOpen && (
                <RowMenu
                  label={`Aktionen für ${account.name}`}
                  onClose={() => {
                    setMenuOpen(false)
                    menuButton.current?.focus()
                  }}
                >
                  <button
                    type="button"
                    className="menu-item"
                    onClick={() => {
                      setMenuOpen(false)
                      setMode('edit')
                    }}
                  >
                    Bearbeiten
                  </button>
                  {!account.defaultAccount && (
                    <>
                      <button
                        type="button"
                        className="menu-item"
                        onClick={() => void sendFromMenu({ defaultAccount: true })}
                      >
                        Als Standardkonto festlegen
                      </button>
                      <button type="button" className="menu-item" onClick={() => void sendFromMenu({ archived: true })}>
                        Archivieren
                      </button>
                      <button
                        type="button"
                        className="menu-item danger"
                        disabled={account.used}
                        title={account.used ? 'Auf dem Konto gibt es Buchungen; es kann nur archiviert werden.' : undefined}
                        onClick={() => {
                          setMenuOpen(false)
                          setMode('confirmDelete')
                        }}
                      >
                        Löschen
                      </button>
                    </>
                  )}
                </RowMenu>
              )}
            </div>
          )}
        </div>
      )}
      {mode === 'confirmDelete' && (
        <div className="dialog-actions confirm" role="group" aria-label="Löschen bestätigen">
          <span>„{account.name}“ löschen?</span>
          <div className="dialog-buttons">
            <button type="button" onClick={() => setMode('view')} disabled={busy}>
              Abbrechen
            </button>
            <button type="button" className="danger" onClick={() => void handleDelete()} disabled={busy}>
              Endgültig löschen
            </button>
          </div>
        </div>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}

/** Settings › Bankkonten (US30): the user's accounts, the default account first. */
export function BankAccountSettings() {
  const accounts = useBankAccounts()

  if (accounts.isPending) {
    return <Loading text="Lade Konten …" />
  }
  if (accounts.error) {
    return <p className="error">Konten konnten nicht geladen werden: {accounts.error.message}</p>
  }
  const active = accounts.data.filter((account) => !account.archived)
  const archived = accounts.data.filter((account) => account.archived)

  return (
    <>
      <section className="card settings-card" aria-labelledby="bank-accounts-title">
        <div className="settings-card-head">
          <div>
            <h2 id="bank-accounts-title">Bankkonten</h2>
            <p className="hint">
              Jede Buchung gehört zu einem Konto. Ohne Auswahl landet sie auf dem Standardkonto. Ein Konto mit
              Buchungen lässt sich nur archivieren.
            </p>
          </div>
          <NewAccount />
        </div>
        <ul className="category-list">
          {active.map((account) => (
            <AccountRow key={`${account.id}-${account.name}-${account.iban}`} account={account} />
          ))}
        </ul>
      </section>
      {archived.length > 0 && (
        <section className="card settings-card" aria-labelledby="archived-accounts-title">
          <h2 id="archived-accounts-title">Archiviert ({archived.length})</h2>
          <p className="hint">Fehlen im Buchungsdialog; bestehende Buchungen behalten ihr Konto.</p>
          <ul className="category-list">
            {archived.map((account) => (
              <AccountRow key={account.id} account={account} />
            ))}
          </ul>
        </section>
      )}
    </>
  )
}
