import { CategorySettings } from '../components/CategorySettings'

/** Settings of the app; later work packages add persons and the account here. */
export function SettingsPage() {
  return (
    <section>
      <h2>Einstellungen</h2>
      <CategorySettings />
    </section>
  )
}
