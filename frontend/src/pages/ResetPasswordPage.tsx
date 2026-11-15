import { useState, type FormEvent } from 'react'
import { errorText } from '../api/client'
import { useResetPassword } from '../api/passwordResets'

interface Props {
  token: string
  /** Called after the password is set and the user is signed in, e.g. to remove the token from the address. */
  onDone: () => void
}

/** US36: whoever opens the reset link from the admin sets a new password and is signed in. */
export function ResetPasswordPage({ token, onDone }: Props) {
  const reset = useResetPassword()
  const [password, setPassword] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (password === '') {
      setError('Bitte ein neues Passwort eingeben.')
      return
    }
    if (password !== repeat) {
      setError('Die Passwörter stimmen nicht überein.')
      return
    }
    setError(null)
    try {
      await reset.mutateAsync({ token, password })
      onDone()
    } catch (e) {
      setError(errorText(e, 'Das Passwort konnte nicht gesetzt werden.'))
    }
  }

  return (
    <section>
      <h2>Neues Passwort</h2>
      <p className="hint">Lege ein neues Passwort fest. Es braucht mindestens 12 Zeichen.</p>
      <form className="stacked-form" noValidate onSubmit={handleSubmit}>
        <label>
          Neues Passwort
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
        <button type="submit" disabled={reset.isPending}>
          Passwort speichern
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
