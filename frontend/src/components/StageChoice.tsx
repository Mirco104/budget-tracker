import type { IstStage } from '../api/types'
import { STAGES } from '../stages'

/** R35, US42: the four stages as rows to choose from, each with what you do, what you get, effort and accuracy. */
export function StageChoice({
  value,
  onChange,
  disabled = false,
  recommended,
}: {
  value: IstStage
  onChange: (stage: IstStage) => void
  disabled?: boolean
  /** The stage marked "empfohlen", e.g. 2 in the setup wizard. */
  recommended?: IstStage
}) {
  return (
    <div className="stage-list" role="radiogroup" aria-label="Stufe">
      {STAGES.map((s) => (
        <button
          key={s.stage}
          type="button"
          role="radio"
          aria-checked={value === s.stage}
          aria-label={`Stufe ${s.stage}: ${s.name}`}
          aria-describedby={`stage-${s.stage}-details`}
          className={value === s.stage ? 'stage-row on' : 'stage-row'}
          disabled={disabled}
          onClick={() => onChange(s.stage)}
        >
          <span className="stage-number">
            Stufe {s.stage}
            {recommended === s.stage && <span className="badge planned">empfohlen</span>}
          </span>
          <span className="stage-main">
            <strong>{s.name}</strong>
            <span className="tx-meta">{s.what}</span>
          </span>
          <ul className="stage-get">
            {s.get.map((line) => (
              <li key={line}>{line}</li>
            ))}
          </ul>
          <span className="stage-effort tx-meta" id={`stage-${s.stage}-details`}>
            Aufwand: <strong>{s.effort}</strong>
            <br />
            {s.effortNote}
            <br />
            Genauigkeit: <strong>{s.accuracy}</strong>
            <br />
            {s.accuracyNote}
          </span>
        </button>
      ))}
    </div>
  )
}
