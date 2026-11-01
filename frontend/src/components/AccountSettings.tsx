import { useState, type FormEvent, type ReactNode } from 'react'
import {
  useChangeDisplayName,
  useChangeEmail,
  useChangePassword,
  useDeleteAccount,
  useExportAccount,
} from '../api/account'
import { errorText } from '../api/client'
import type { Me } from '../api/types'

type Field = 'displayName' | 'email' | 'password'

interface EditProps {
  /** Closes the form and shows the message in the row. */
  onDone: (message: string) => void
  onCancel: () => void
}

function FormButtons({ pending, onCancel }: { pending: boolean; onCancel: () => void }) {
  return (
    <span className="form-buttons">
      <button type="submit" disabled={pending}>
        Speichern
      </button>
      <button type="button" onClick={onCancel}>
        Abbrechen
      </button>
    </span>
  )
}

function FormError({ error }: { error: string | null }) {
  return error ? (
    <p className="error" role="alert">
      {error}
    </p>
  ) : null
}

function DisplayNameForm({ current, onDone, onCancel }: EditProps & { current: string }) {
  const change = useChangeDisplayName()
  const [displayName, setDisplayName] = useState(current)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (displayName.trim() === '') {
      setError('Bitte einen Anzeigenamen eingeben.')
      return
    }
    try {
      await change.mutateAsync(displayName.trim())
      onDone('Der Anzeigename ist geändert.')
    } catch (e) {
      setError(errorText(e, 'Ändern fehlgeschlagen.'))
    }
  }

  return (
    <form className="stacked-form" aria-label="Anzeigename ändern" noValidate onSubmit={handleSubmit}>
      <label>
        Neuer Anzeigename
        <input
          autoComplete="nickname"
          maxLength={50}
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
        />
      </label>
      <FormButtons pending={change.isPending} onCancel={onCancel} />
      <FormError error={error} />
    </form>
  )
}

function EmailForm({ current, onDone, onCancel }: EditProps & { current: string }) {
  const change = useChangeEmail()
  const [email, setEmail] = useState(current)
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (email.trim() === '' || password === '') {
      setError('Bitte neue E-Mail-Adresse und Passwort eingeben.')
      return
    }
    try {
      await change.mutateAsync({ email: email.trim(), password })
      onDone('Die E-Mail-Adresse ist geändert.')
    } catch (e) {
      setError(errorText(e, 'Ändern fehlgeschlagen.'))
    }
  }

  return (
    <form className="stacked-form" aria-label="E-Mail ändern" noValidate onSubmit={handleSubmit}>
      <label>
        Neue E-Mail-Adresse
        <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label>
        Passwort zur Bestätigung
        <input
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      <FormButtons pending={change.isPending} onCancel={onCancel} />
      <FormError error={error} />
    </form>
  )
}

function PasswordForm({ onDone, onCancel }: EditProps) {
  const change = useChangePassword()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (currentPassword === '' || newPassword === '') {
      setError('Bitte beide Passwörter eingeben.')
      return
    }
    try {
      await change.mutateAsync({ currentPassword, newPassword })
      onDone('Das Passwort ist geändert.')
    } catch (e) {
      setError(errorText(e, 'Ändern fehlgeschlagen.'))
    }
  }

  return (
    <form className="stacked-form" aria-label="Passwort ändern" noValidate onSubmit={handleSubmit}>
      <label>
        Bisheriges Passwort
        <input
          type="password"
          autoComplete="current-password"
          value={currentPassword}
          onChange={(e) => setCurrentPassword(e.target.value)}
        />
      </label>
      <label>
        Neues Passwort (mindestens 12 Zeichen)
        <input
          type="password"
          autoComplete="new-password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
      </label>
      <FormButtons pending={change.isPending} onCancel={onCancel} />
      <FormError error={error} />
    </form>
  )
}

interface RowProps {
  label: string
  value: string
  editing: boolean
  message: string | null
  onEdit: () => void
  /** The form, shown while editing. */
  children: ReactNode
}

/** One value of the account with its "Ändern" button; the form opens below it. */
function AccountRow({ label, value, editing, message, onEdit, children }: RowProps) {
  return (
    <div className="account-entry">
      <div className="account-row">
        <span className="account-label">{label}</span>
        <span className="account-value">{value}</span>
        {!editing && (
          <button type="button" aria-label={`${label} ändern`} onClick={onEdit}>
            Ändern
          </button>
        )}
      </div>
      {editing && children}
      {!editing && message && (
        <p className="hint" role="status">
          {message}
        </p>
      )}
    </div>
  )
}

/** Name, e-mail and password; one form at a time (US16). */
function AccountDetails({ me }: { me: Me }) {
  const [editing, setEditing] = useState<Field | null>(null)
  const [message, setMessage] = useState<{ field: Field; text: string } | null>(null)

  function edit(field: Field) {
    setMessage(null)
    setEditing(field)
  }

  function done(field: Field) {
    return (text: string) => {
      setEditing(null)
      setMessage({ field, text })
    }
  }

  const cancel = () => setEditing(null)
  const messageFor = (field: Field) => (message?.field === field ? message.text : null)

  return (
    <div className="account-details">
      <AccountRow
        label="Anzeigename"
        value={me.displayName}
        editing={editing === 'displayName'}
        message={messageFor('displayName')}
        onEdit={() => edit('displayName')}
      >
        <DisplayNameForm current={me.displayName} onDone={done('displayName')} onCancel={cancel} />
      </AccountRow>
      <AccountRow
        label="E-Mail"
        value={me.email}
        editing={editing === 'email'}
        message={messageFor('email')}
        onEdit={() => edit('email')}
      >
        <EmailForm current={me.email} onDone={done('email')} onCancel={cancel} />
      </AccountRow>
      <AccountRow
        label="Passwort"
        value="••••••••••••"
        editing={editing === 'password'}
        message={messageFor('password')}
        onEdit={() => edit('password')}
      >
        <PasswordForm onDone={done('password')} onCancel={cancel} />
      </AccountRow>
    </div>
  )
}

function ExportButton() {
  const exportAccount = useExportAccount()
  const [error, setError] = useState<string | null>(null)

  // The data arrives as JSON and is saved as a file in the browser
  async function handleExport() {
    setError(null)
    try {
      const data = await exportAccount.mutateAsync()
      const url = URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' }))
      const link = document.createElement('a')
      link.href = url
      link.download = 'budget-export.json'
      link.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      setError(errorText(e, 'Export fehlgeschlagen.'))
    }
  }

  return (
    <div className="stacked-form">
      <h4>Daten exportieren</h4>
      <p className="hint">Alle deine Kategorien, Soll-Werte und Transaktionen als JSON-Datei.</p>
      <button type="button" disabled={exportAccount.isPending} onClick={() => void handleExport()}>
        Daten exportieren
      </button>
      {error && <p className="error">{error}</p>}
    </div>
  )
}

function DeleteForm() {
  const remove = useDeleteAccount()
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (password === '') {
      setError('Bitte das Passwort eingeben.')
      return
    }
    if (!window.confirm('Konto und alle Daten endgültig löschen? Das lässt sich nicht rückgängig machen.')) {
      return
    }
    setError(null)
    try {
      await remove.mutateAsync(password)
    } catch (e) {
      setError(errorText(e, 'Löschen fehlgeschlagen.'))
    }
  }

  return (
    <form className="stacked-form" aria-label="Konto löschen" noValidate onSubmit={handleSubmit}>
      <h4>Konto löschen</h4>
      <p className="hint">Löscht dein Konto und alle deine Daten. Exportiere sie vorher, wenn du sie behalten willst.</p>
      <label>
        Passwort
        <input
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
      </label>
      <button type="submit" disabled={remove.isPending}>
        Konto löschen
      </button>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}

/** Settings › Konto (US16). */
export function AccountSettings({ me }: { me: Me }) {
  return (
    <section>
      <h3>Konto</h3>
      <AccountDetails me={me} />
      <ExportButton />
      <DeleteForm />
    </section>
  )
}
