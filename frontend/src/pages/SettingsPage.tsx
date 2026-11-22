import type { Me } from '../api/types'
import { AccountSettings } from '../components/AccountSettings'
import { CategorySettings } from '../components/CategorySettings'
import { InvitationSettings } from '../components/InvitationSettings'
import { StageSettings } from '../components/StageSettings'
import { UserSettings } from '../components/UserSettings'
import { PageHeader } from '../components/ui/PageHeader'
import { SETTINGS_ROUTES, useSettingsPage, type SettingsPage as Page } from '../router'

const LABELS: Record<Page, string> = {
  categories: 'Kategorien',
  stage: 'Ist verfolgen',
  bankAccounts: 'Bankkonten',
  account: 'Konto',
  invitations: 'Einladungen',
  users: 'Nutzer',
}

/**
 * Settings with sub pages (US10, US30): categories, bank accounts, the own account and, for admins, invitations and
 * users. Each sub
 * page has its own address (US29).
 */
export function SettingsPage({ me }: { me: Me }) {
  const chosen = useSettingsPage()
  // Display only; the backend checks the role on every call
  const admin = me.role !== 'USER'
  const pages: Page[] = admin
    ? ['categories', 'stage', 'account', 'invitations', 'users']
    : ['categories', 'stage', 'account']
  const page = pages.includes(chosen) ? chosen : 'categories'

  return (
    <section>
      <PageHeader title="Einstellungen" />
      <nav className="subtabs" aria-label="Einstellungen">
        {pages.map((item) => (
          <a key={item} href={SETTINGS_ROUTES[item]} aria-current={page === item ? 'page' : undefined}>
            {LABELS[item]}
          </a>
        ))}
      </nav>
      {page === 'categories' && <CategorySettings />}
      {page === 'stage' && <StageSettings me={me} />}
      {page === 'bankAccounts' && (
        <p className="hint">
          Die Bankkonten stehen jetzt im Reiter <a href="#/konten">Konten</a>.
        </p>
      )}
      {page !== 'categories' && page !== 'bankAccounts' && page !== 'stage' && (
        <div className="card settings-card">
          {page === 'account' && <AccountSettings me={me} />}
          {page === 'invitations' && <InvitationSettings senderName={me.displayName} />}
          {page === 'users' && <UserSettings me={me} />}
        </div>
      )}
    </section>
  )
}
