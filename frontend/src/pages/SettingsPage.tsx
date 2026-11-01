import type { Me } from '../api/types'
import { AccountSettings } from '../components/AccountSettings'
import { CategorySettings } from '../components/CategorySettings'
import { InvitationSettings } from '../components/InvitationSettings'
import { UserSettings } from '../components/UserSettings'

/** Settings of the app: categories, the own account and, for admins, users and invitations. */
export function SettingsPage({ me }: { me: Me }) {
  return (
    <section>
      <h2>Einstellungen</h2>
      <CategorySettings />
      <AccountSettings me={me} />
      {/* Display only; the backend checks the role on every call */}
      {me.role !== 'USER' && <UserSettings me={me} />}
      {me.role !== 'USER' && <InvitationSettings senderName={me.displayName} />}
    </section>
  )
}
