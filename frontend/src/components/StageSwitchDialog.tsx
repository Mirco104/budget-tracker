import { useState } from 'react'
import { useChangeStage } from '../api/account'
import { errorText } from '../api/client'
import type { IstStage } from '../api/types'
import { currentMonth, formatDate, todayIso } from '../format'
import { stageName } from '../stages'
import { Dialog } from './ui/Dialog'

/**
 * Decision 90: leaving stage 1 asks whether income and fixed costs are entered automatically (saving on ticking) and
 * from when; switching to stage 1 says that the app enters nothing any more. Only what is confirmed here is saved.
 */
export function StageSwitchDialog({
  from,
  to,
  onClose,
  onDone,
}: {
  from: IstStage
  to: IstStage
  onClose: () => void
  onDone?: () => void
}) {
  const changeStage = useChangeStage()
  const month = currentMonth()
  const leaving = from === 1 && to > 1
  const [auto, setAuto] = useState(true)
  // Own decision (22.6, 32h): for stage 2 "rückwirkend" from this month, so fixed costs before today are entered
  const [since, setSince] = useState<'today' | 'back'>(to === 2 ? 'back' : 'today')
  const [backMonth, setBackMonth] = useState(month)
  const [error, setError] = useState<string | null>(null)

  function confirm() {
    setError(null)
    const input = leaving
      ? { stage: to, autoEntries: auto, entriesFrom: auto ? (since === 'today' ? todayIso() : `${backMonth}-01`) : undefined }
      : { stage: to }
    changeStage.mutate(input, {
      onSuccess: () => {
        onDone?.()
        onClose()
      },
      onError: (e) => setError(errorText(e, 'Die Stufe konnte nicht gespeichert werden.')),
    })
  }

  return (
    <Dialog title={`Auf Stufe ${to}: ${stageName(to)} wechseln`} onClose={onClose}>
      <div className="booking-form stage-switch">
        {leaving ? (
          <>
            <p>Ab Stufe 2 kann die App Einkommen und Fixkosten an ihrem Termin selbst eintragen.</p>
            <label className="check-field">
              <input type="checkbox" checked={auto} onChange={(e) => setAuto(e.target.checked)} />
              Einkommen und Fixkosten automatisch eintragen, Sparraten beim Abhaken
            </label>
            {auto ? (
              <fieldset className="radio-group">
                <legend>Ab wann?</legend>
                <label className="check-field">
                  <input type="radio" name="since" checked={since === 'today'} onChange={() => setSince('today')} />
                  Ab heute ({formatDate(todayIso())})
                </label>
                <label className="check-field">
                  <input type="radio" name="since" checked={since === 'back'} onChange={() => setSince('back')} />
                  Rückwirkend ab
                  <input
                    type="month"
                    aria-label="Rückwirkend ab Monat"
                    value={backMonth}
                    max={month}
                    disabled={since !== 'back'}
                    onChange={(e) => setBackMonth(e.target.value || month)}
                  />
                </label>
                {since === 'back' ? (
                  <p className="hint">
                    Die App trägt die Termine seit dann nach dem Plan ein, der damals galt. Echte Buchungen aus einem Import
                    ersetzen die Einträge später.
                  </p>
                ) : (
                  to === 2 && (
                    <p className="hint">
                      Fixkosten, die in diesem Monat schon fällig waren, trägt die App nicht ein. Beim nächsten Kontostand
                      zählen sie dann als variabel.
                    </p>
                  )
                )}
              </fieldset>
            ) : (
              <p className="hint">Dann trägst du Einkommen und Fixkosten selbst ein oder importierst sie.</p>
            )}
          </>
        ) : (
          <p>In Stufe 1 trägt die App nichts mehr ein. Bisherige Buchungen bleiben gespeichert und sind wieder da, wenn du zurückwechselst.</p>
        )}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="dialog-actions">
          <div className="dialog-buttons">
            <button type="button" onClick={onClose}>
              Abbrechen
            </button>
            <button type="button" className="primary" disabled={changeStage.isPending} onClick={confirm}>
              Stufe wechseln
            </button>
          </div>
        </div>
      </div>
    </Dialog>
  )
}
