import { useState, type FormEvent } from 'react'
import { useChangePassword, useDeleteAccount, useExportAccount } from '../api/account'
import { errorText } from '../api/client'

function PasswordForm() {
  const change = useChangePassword()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (currentPassword === '' || newPassword === '') {
      setMessage({ text: 'Bitte beide Passwörter eingeben.', error: true })
      return
    }
    try {
      await change.mutateAsync({ currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setMessage({ text: 'Das Passwort ist geändert.', error: false })
    } catch (e) {
      setMessage({ text: errorText(e, 'Ändern fehlgeschlagen.'), error: true })
    }
  }

  return (
    <form className="stacked-form" aria-label="Passwort ändern" noValidate onSubmit={handleSubmit}>
      <h4>Passwort ändern</h4>
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
      <button type="submit" disabled={change.isPending}>
        Passwort ändern
      </button>
      {message && (
        <p className={message.error ? 'error' : 'hint'} role={message.error ? 'alert' : 'status'}>
          {message.text}
        </p>
      )}
    </form>
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
export function AccountSettings() {
  return (
    <section>
      <h3>Konto</h3>
      <PasswordForm />
      <ExportButton />
      <DeleteForm />
    </section>
  )
}
