import { useState } from 'react'
import { errorText } from '../api/client'
import type { Me, Role, UserSummary } from '../api/types'
import { useChangeRole, useUsers } from '../api/users'
import { formatDateTime } from '../format'

const ROLE_LABELS: Record<Role, string> = { USER: 'User', ADMIN: 'Admin', SYSTEM_ADMIN: 'Systemadmin' }

function UserRow({ user, isMe }: { user: UserSummary; isMe: boolean }) {
  const change = useChangeRole()
  const [error, setError] = useState<string | null>(null)
  // The system admin stays, and nobody changes their own role (the backend answers 409)
  const canChange = !isMe && user.role !== 'SYSTEM_ADMIN'
  const nextRole = user.role === 'USER' ? 'ADMIN' : 'USER'

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
      {canChange && (
        <button type="button" disabled={change.isPending} onClick={() => void handleChange()}>
          {nextRole === 'ADMIN' ? 'Zum Admin machen' : 'Zum User machen'}
        </button>
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
        Admins können Einladungen erzeugen und Rollen ändern. Der Systemadmin bleibt immer Systemadmin.
      </p>
      {users.isPending && <p>Lade Nutzer …</p>}
      {users.error && <p className="error">Nutzer konnten nicht geladen werden: {users.error.message}</p>}
      {users.data?.map((user) => (
        <UserRow key={user.id} user={user} isMe={user.id === me.id} />
      ))}
    </section>
  )
}
