import type { IstStage } from '../api/types'
import { CHECKS, STAGES } from '../stages'

// Decision 87: a small picture of what the overview of a stage shows: the plan as a bar, a sum, a list, a list with
// categories
function MiniPicture({ stage }: { stage: IstStage }) {
  const lines = [0, 1, 2]
  return (
    <svg className="stage-mini" viewBox="0 0 120 64" aria-hidden="true">
      <rect x="8" y="10" width="44" height="8" rx="3" className="mini-fixed" />
      <rect x="52" y="10" width="34" height="8" className="mini-budget" />
      <rect x="86" y="10" width="26" height="8" rx="3" className="mini-saving" />
      {stage === 1 && (
        <text x="8" y="44" className="mini-note">
          nur der Plan
        </text>
      )}
      {stage === 2 && (
        <>
          <text x="8" y="42" className="mini-sum">
            412 €
          </text>
          <text x="54" y="42" className="mini-note">
            variabel
          </text>
        </>
      )}
      {stage >= 3 &&
        lines.map((i) => (
          <g key={i}>
            {stage === 4 && <rect x="8" y={27 + i * 11} width="7" height="7" rx="2" className={`mini-tag mini-tag-${i}`} />}
            <rect x={stage === 4 ? 19 : 8} y={28 + i * 11} width={60 - i * 10} height="5" rx="2" className="mini-line" />
            <rect x="98" y={28 + i * 11} width="14" height="5" rx="2" className="mini-amount" />
          </g>
        ))}
    </svg>
  )
}

function Dots({ label, value, kind }: { label: string; value: number; kind: 'effort' | 'accuracy' }) {
  return (
    <span className={`stage-dots ${kind}`} aria-label={`${label}: ${value} von 4`}>
      <span>{label}</span>
      <span aria-hidden="true">
        {[1, 2, 3, 4].map((i) => (
          <i key={i} className={i <= value ? 'on' : undefined} />
        ))}
      </span>
    </span>
  )
}

/**
 * R35, decision 87: the four stages as a staircase of cards: a picture of the overview, the checks of what the stage
 * delivers (stage n has the first n) and effort and accuracy as dots; the recommended stage has a badge.
 */
export function StageChoice({
  value,
  onChange,
  disabled = false,
  recommended,
}: {
  value: IstStage
  onChange: (stage: IstStage) => void
  disabled?: boolean
  /** The stage marked "Für dich empfohlen" (decision 86). */
  recommended?: IstStage | null
}) {
  return (
    <div className="stage-stairs" role="radiogroup" aria-label="Stufe">
      {STAGES.map((s) => (
        <button
          key={s.stage}
          type="button"
          role="radio"
          aria-checked={value === s.stage}
          aria-label={`Stufe ${s.stage}: ${s.name}`}
          className={value === s.stage ? 'stage-card on' : 'stage-card'}
          disabled={disabled}
          onClick={() => onChange(s.stage)}
        >
          {recommended === s.stage && <span className="stage-recommended">Für dich empfohlen</span>}
          <span className="stage-number">Stufe {s.stage}</span>
          <strong>{s.name}</strong>
          <MiniPicture stage={s.stage} />
          <ul className="stage-checks">
            {CHECKS.map((check, i) => (
              <li key={check} className={i < s.stage ? 'yes' : 'no'}>
                <span className="mark" aria-hidden="true">
                  {i < s.stage ? '✓' : '–'}
                </span>
                <span>
                  <span className="visually-hidden">{i < s.stage ? 'Enthalten: ' : 'Nicht enthalten: '}</span>
                  {check}
                </span>
              </li>
            ))}
          </ul>
          <Dots label="Aufwand" value={s.stage} kind="effort" />
          <Dots label="Genauigkeit" value={s.stage} kind="accuracy" />
        </button>
      ))}
    </div>
  )
}
