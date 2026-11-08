import { useState, type KeyboardEvent } from 'react'
import { errorText } from '../../api/client'

interface Props {
  /** Accessible name of the name field, e.g. "Neue Kategorie". */
  label: string
  placeholder: string
  /** Prefilled name, e.g. the suggested name of an account. */
  initial?: string
  /** Creates the entry and chooses it; a thrown error is shown next to the field. */
  onCreate: (name: string) => Promise<void>
  onCancel: () => void
}

/**
 * A name field with "Anlegen" and "Abbrechen" to create a missing category or account right where it is chosen
 * (08.11.2026). It is no form, because it also sits inside the booking dialog: Enter creates the entry and does not
 * save the dialog, and Escape cancels without closing the dialog.
 */
export function InlineCreate({ label, placeholder, initial = '', onCreate, onCancel }: Props) {
  const [name, setName] = useState(initial)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function create() {
    const trimmed = name.trim()
    if (trimmed === '') {
      setError('Der Name ist Pflicht.')
      return
    }
    setError(null)
    setBusy(true)
    try {
      await onCreate(trimmed)
    } catch (e) {
      setError(errorText(e, 'Anlegen fehlgeschlagen.'))
    } finally {
      setBusy(false)
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault()
      void create()
    } else if (event.key === 'Escape') {
      event.stopPropagation()
      onCancel()
    }
  }

  return (
    <div className="inline-create" role="group">
      <input
        aria-label={label}
        placeholder={placeholder}
        // Opened on purpose from the selection
        autoFocus
        maxLength={50}
        value={name}
        onChange={(e) => setName(e.target.value)}
        onKeyDown={handleKeyDown}
        aria-invalid={error ? true : undefined}
      />
      <button type="button" disabled={busy} onClick={() => void create()}>
        Anlegen
      </button>
      <button type="button" onClick={onCancel}>
        Abbrechen
      </button>
      {error && (
        <span className="error" role="alert">
          {error}
        </span>
      )}
    </div>
  )
}
