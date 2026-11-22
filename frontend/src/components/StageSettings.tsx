import { useState } from 'react'
import { useChangeSetup, useChangeStage } from '../api/account'
import { errorText } from '../api/client'
import type { Me } from '../api/types'
import { ROUTES } from '../router'
import { StageChoice } from './StageChoice'

/** US42: Einstellungen › Ist verfolgen with the stage and a way to run the setup wizard again (US40). */
export function StageSettings({ me }: { me: Me }) {
  const changeStage = useChangeStage()
  const changeSetup = useChangeSetup()
  const [error, setError] = useState<string | null>(null)

  async function restart() {
    setError(null)
    try {
      await changeSetup.mutateAsync(false)
      window.location.hash = ROUTES.setup
    } catch (e) {
      setError(errorText(e, 'Die Einrichtung konnte nicht gestartet werden.'))
    }
  }

  return (
    <div className="stage-settings">
      <p className="hint">
        Wie genau willst du dein Ist verfolgen? Ein Wechsel ändert nur die Anzeige. Deine Buchungen, Kontostände und
        Kategorien bleiben erhalten. In Stufe 1 sind die Seiten Transaktionen und Auswertung ausgeblendet.
      </p>
      <StageChoice
        value={me.istStage}
        disabled={changeStage.isPending}
        onChange={(stage) => {
          setError(null)
          changeStage.mutate(stage, { onError: (e) => setError(errorText(e, 'Die Stufe konnte nicht gespeichert werden.')) })
        }}
      />
      <div className="card settings-card">
        <h2>Einrichtung</h2>
        <p className="tx-meta">
          Geht Schritt für Schritt durch Konten, Einkommen, Fixkosten, variable Kosten und Sparen. Deine Daten sind schon
          eingetragen, du prüfst und ergänzt sie.
        </p>
        <div>
          <button type="button" onClick={() => void restart()} disabled={changeSetup.isPending}>
            Einrichtung starten
          </button>
        </div>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
