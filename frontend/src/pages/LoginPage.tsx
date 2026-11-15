import { useState, type FormEvent } from 'react'
import { useLogin } from '../api/auth'

/** US14: sign in with e-mail and password. The backend gives one text for a wrong e-mail or password. */
export function LoginPage() {
  const login = useLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [forgotten, setForgotten] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (email.trim() === '' || password === '') {
      setError('Bitte E-Mail und Passwort eingeben.')
      return
    }
    setError(null)
    try {
      await login.mutateAsync({ email: email.trim(), password })
    } catch (e) {
      setPassword('')
      setError(e instanceof Error ? e.message : 'Anmeldung fehlgeschlagen.')
    }
  }

  return (
    <section>
      <h2>Anmelden</h2>
      <form className="stacked-form" noValidate onSubmit={handleSubmit}>
        <label>
          E-Mail
          <input
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            aria-invalid={error ? true : undefined}
          />
        </label>
        <label>
          Passwort
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            aria-invalid={error ? true : undefined}
          />
        </label>
        <button type="submit" disabled={login.isPending}>
          Anmelden
        </button>
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
      </form>
      {/* US36: the app sends no e-mail (decision 19), an admin creates a link for a new password */}
      <button type="button" className="link-button" aria-expanded={forgotten} onClick={() => setForgotten(!forgotten)}>
        Passwort vergessen?
      </button>
      {forgotten && (
        <p className="hint">
          Bitte deinen Admin um einen Link für ein neues Passwort. Er erzeugt ihn unter Einstellungen › Nutzer; der Link
          ist einmal nutzbar und 24 Stunden gültig.
        </p>
      )}
    </section>
  )
}
