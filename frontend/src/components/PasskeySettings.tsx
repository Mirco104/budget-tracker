import { useState, type FormEvent } from 'react'
import { errorText } from '../api/client'
import {
  passkeyErrorText,
  passkeysSupported,
  useAddPasskey,
  useDeletePasskey,
  usePasskeys,
  useRenamePasskey,
} from '../api/passkeys'
import type { Passkey } from '../api/types'
import { formatDateTime } from '../format'

function AddForm({ onDone, onCancel }: { onDone: (message: string) => void; onCancel: () => void }) {
  const add = useAddPasskey()
  const [label, setLabel] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (password === '') {
      setError('Bitte das Passwort eingeben.')
      return
    }
    setError(null)
    try {
      const passkey = await add.mutateAsync({ password, label: label.trim() })
      onDone(`Der Passkey „${passkey.label}“ ist hinzugefügt.`)
    } catch (e) {
      setPassword('')
      setError(passkeyErrorText(e, 'Hinzufügen fehlgeschlagen.'))
    }
  }

  return (
    <form className="stacked-form" aria-label="Passkey hinzufügen" noValidate onSubmit={handleSubmit}>
      <label>
        Name (optional, z. B. „Handy“ oder „Laptop“)
        <input maxLength={50} value={label} onChange={(e) => setLabel(e.target.value)} />
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
      <span className="form-buttons">
        <button type="submit" disabled={add.isPending}>
          Weiter
        </button>
        <button type="button" onClick={onCancel}>
          Abbrechen
        </button>
      </span>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}

function RenameForm({ passkey, onDone }: { passkey: Passkey; onDone: () => void }) {
  const rename = useRenamePasskey()
  const [label, setLabel] = useState(passkey.label)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (label.trim() === '') {
      setError('Bitte einen Namen eingeben.')
      return
    }
    try {
      await rename.mutateAsync({ id: passkey.id, label: label.trim() })
      onDone()
    } catch (e) {
      setError(errorText(e, 'Umbenennen fehlgeschlagen.'))
    }
  }

  return (
    <form className="stacked-form" aria-label="Passkey umbenennen" noValidate onSubmit={handleSubmit}>
      <label>
        Neuer Name
        <input maxLength={50} value={label} onChange={(e) => setLabel(e.target.value)} />
      </label>
      <span className="form-buttons">
        <button type="submit" disabled={rename.isPending}>
          Speichern
        </button>
        <button type="button" onClick={onDone}>
          Abbrechen
        </button>
      </span>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </form>
  )
}

function PasskeyRow({ passkey }: { passkey: Passkey }) {
  const remove = useDeletePasskey()
  const [renaming, setRenaming] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleDelete() {
    if (!window.confirm(`Passkey „${passkey.label}“ löschen? Damit kannst du dich nicht mehr anmelden.`)) {
      return
    }
    setError(null)
    try {
      await remove.mutateAsync(passkey.id)
    } catch (e) {
      setError(errorText(e, 'Löschen fehlgeschlagen.'))
    }
  }

  const used = passkey.lastUsedAt ? `zuletzt benutzt ${formatDateTime(passkey.lastUsedAt)}` : 'noch nicht benutzt'
  return (
    <li className="account-entry">
      <div className="account-row">
        <span className="account-value">
          {passkey.label}
          <span className="hint passkey-meta">
            hinzugefügt {formatDateTime(passkey.createdAt)} · {used}
          </span>
        </span>
        {!renaming && (
          <span className="form-buttons">
            <button type="button" aria-label={`${passkey.label} umbenennen`} onClick={() => setRenaming(true)}>
              Umbenennen
            </button>
            <button
              type="button"
              className="danger"
              aria-label={`${passkey.label} löschen`}
              disabled={remove.isPending}
              onClick={() => void handleDelete()}
            >
              Löschen
            </button>
          </span>
        )}
      </div>
      {renaming && <RenameForm passkey={passkey} onDone={() => setRenaming(false)} />}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}

/**
 * Settings › Konto: the own passkeys (US44). A passkey signs in without the password, with the fingerprint, the face
 * or the PIN of the device; the password stays as the other way in.
 */
export function PasskeySettings() {
  const passkeys = usePasskeys()
  const [adding, setAdding] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const supported = passkeysSupported()

  return (
    <div className="stacked-form passkey-settings">
      <h4>Passkeys</h4>
      <p className="hint">
        Mit einem Passkey meldest du dich ohne Passwort an, mit Fingerabdruck, Gesicht oder der PIN deines Geräts. Dein
        Passwort bleibt gültig.
      </p>
      {passkeys.isError && <p className="error">{errorText(passkeys.error, 'Laden fehlgeschlagen.')}</p>}
      {passkeys.data && passkeys.data.length > 0 && (
        <ul className="passkey-list" aria-label="Deine Passkeys">
          {passkeys.data.map((passkey) => (
            <PasskeyRow key={passkey.id} passkey={passkey} />
          ))}
        </ul>
      )}
      {passkeys.data?.length === 0 && <p className="hint">Du hast noch keinen Passkey.</p>}
      {!supported && <p className="hint">Dieser Browser unterstützt keine Passkeys.</p>}
      {supported && !adding && (
        <button
          type="button"
          onClick={() => {
            setMessage(null)
            setAdding(true)
          }}
        >
          Passkey hinzufügen
        </button>
      )}
      {adding && (
        <AddForm
          onDone={(text) => {
            setAdding(false)
            setMessage(text)
          }}
          onCancel={() => setAdding(false)}
        />
      )}
      {message && (
        <p className="hint" role="status">
          {message}
        </p>
      )}
    </div>
  )
}
