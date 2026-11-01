import type { Me } from '../api/types'
import { AccountSettings } from '../components/AccountSettings'
import { CategorySettings } from '../components/CategorySettings'
import { InvitationSettings } from '../components/InvitationSettings'

/** Settings of the app: categories, the own account and, for admins, invitations. */
export function SettingsPage({ me }: { me: Me }) {
  return (
    <section>
      <h2>Einstellungen</h2>
      <CategorySettings />
      <AccountSettings />
      {me.role === 'ADMIN' && <InvitationSettings />}
    </section>
  )
}
