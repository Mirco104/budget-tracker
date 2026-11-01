import { useState, type FormEvent } from 'react'
import { errorText } from '../api/client'
import { useAcceptInvitation } from '../api/invitations'

interface Props {
  token: string
  /** Called after the account exists and is signed in, e.g. to remove the token from the address. */
  onAccepted: () => void
}

/** US15: whoever opens the link chooses e-mail, display name and password and gets the default categories. */
export function AcceptInvitationPage({ token, onAccepted }: Props) {
  const accept = useAcceptInvitation()
  const [email, setEmail] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (email.trim() === '' || displayName.trim() === '' || password === '') {
      setError('Bitte alle Felder ausfüllen.')
      return
    }
    if (password !== repeat) {
      setError('Die Passwörter stimmen nicht überein.')
      return
    }
    setError(null)
    try {
      await accept.mutateAsync({ token, input: { email: email.trim(), displayName: displayName.trim(), password } })
      onAccepted()
    } catch (e) {
      setError(errorText(e, 'Das Konto konnte nicht angelegt werden.'))
    }
  }

  return (
    <section>
      <h2>Einladung annehmen</h2>
      <p className="hint">Lege dein Konto an. Das Passwort braucht mindestens 12 Zeichen.</p>
      <form className="stacked-form" noValidate onSubmit={handleSubmit}>
        <label>
          E-Mail
          <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label>
          Anzeigename
          <input
            autoComplete="nickname"
            maxLength={50}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
          />
        </label>
        <label>
          Passwort
          <input
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </label>
        <label>
          Passwort wiederholen
          <input type="password" autoComplete="new-password" value={repeat} onChange={(e) => setRepeat(e.target.value)} />
        </label>
        <button type="submit" disabled={accept.isPending}>
          Konto anlegen
        </button>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
      </form>
    </section>
  )
}
