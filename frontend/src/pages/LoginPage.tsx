import { useState, type FormEvent } from 'react'
import { useLogin } from '../api/auth'

/** US14: sign in with e-mail and password. The backend gives one text for a wrong e-mail or password. */
export function LoginPage() {
  const login = useLogin()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

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
    <section className="login">
      <h2>Anmelden</h2>
      <form noValidate onSubmit={handleSubmit}>
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
    </section>
  )
}
