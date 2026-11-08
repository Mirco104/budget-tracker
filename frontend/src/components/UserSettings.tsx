import { useState } from 'react'
import { errorText } from '../api/client'
import { useCreatePasswordReset } from '../api/passwordResets'
import type { Me, PasswordResetLink, Role, UserSummary } from '../api/types'
import { useChangeRole, useUsers } from '../api/users'
import { formatDateTime } from '../format'
import { CopyLink } from './ui/CopyLink'

const ROLE_LABELS: Record<Role, string> = { USER: 'User', ADMIN: 'Admin', SYSTEM_ADMIN: 'Systemadmin' }

function UserRow({ user, isMe, meRole }: { user: UserSummary; isMe: boolean; meRole: Role }) {
  const change = useChangeRole()
  const createReset = useCreatePasswordReset()
  const [error, setError] = useState<string | null>(null)
  const [resetLink, setResetLink] = useState<PasswordResetLink | null>(null)
  // The system admin stays, and nobody changes their own role (the backend answers 409)
  const canChange = !isMe && user.role !== 'SYSTEM_ADMIN'
  const nextRole = user.role === 'USER' ? 'ADMIN' : 'USER'
  // US36: a link never gives more rights than its creator: an admin resets users, the system admin also admins
  const canReset = canChange && (user.role === 'USER' || meRole === 'SYSTEM_ADMIN')

  async function handleReset() {
    setError(null)
    try {
      setResetLink(await createReset.mutateAsync(user.id))
    } catch (e) {
      setError(errorText(e, 'Der Link konnte nicht erzeugt werden.'))
    }
  }

  async function handleChange() {
    setError(null)
    try {
      await change.mutateAsync({ id: user.id, role: nextRole })
    } catch (e) {
      setError(errorText(e, 'Ändern fehlgeschlagen.'))
    }
  }

  return (
    <div className="user-row" aria-label={user.displayName}>
      <div className="user-name">
        <strong>
          {user.displayName}
          {isMe && ' (Du)'}
        </strong>
        <span className="hint">{user.email}</span>
        <span className="hint">
          {ROLE_LABELS[user.role]} · seit {formatDateTime(user.createdAt)} · letzte Anmeldung{' '}
          {user.lastLoginAt ? formatDateTime(user.lastLoginAt) : 'noch nie'}
        </span>
      </div>
      <div className="user-actions">
        {canChange && (
          <button type="button" disabled={change.isPending} onClick={() => void handleChange()}>
            {nextRole === 'ADMIN' ? 'Zum Admin machen' : 'Zum User machen'}
          </button>
        )}
        {canReset && (
          <button type="button" disabled={createReset.isPending} onClick={() => void handleReset()}>
            Link für neues Passwort
          </button>
        )}
      </div>
      {resetLink && (
        <div className="user-reset">
          <CopyLink key={resetLink.link} link={resetLink.link} label={`Link für neues Passwort von ${user.displayName}`}>
            <span className="hint">
              Einmal nutzbar, gültig bis {formatDateTime(resetLink.expiresAt)}. Schicke ihn {user.displayName} selbst.
            </span>
          </CopyLink>
        </div>
      )}
      {error && <p className="error">{error}</p>}
    </div>
  )
}

/** Settings › Nutzer, only for admins (US25): account data only, no financial data. */
export function UserSettings({ me }: { me: Me }) {
  const users = useUsers()

  return (
    <section>
      <h3>Nutzer</h3>
      <p className="hint">
        Admins können Einladungen erzeugen, Rollen ändern und bei vergessenem Passwort einen Link für ein neues
        Passwort erzeugen. Der Systemadmin bleibt immer Systemadmin.
      </p>
      {users.isPending && <p>Lade Nutzer …</p>}
      {users.error && <p className="error">Nutzer konnten nicht geladen werden: {users.error.message}</p>}
      {users.data?.map((user) => (
        <UserRow key={user.id} user={user} isMe={user.id === me.id} meRole={me.role} />
      ))}
    </section>
  )
}
