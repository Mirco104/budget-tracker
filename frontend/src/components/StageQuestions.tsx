import type { IstStage } from '../api/types'
import { recommendStage, stageName, TIMES, WISHES } from '../stages'

/**
 * Decision 86: two questions before the stages: what the user wants to know (a ladder whose answers build on each
 * other) and how much time they want to spend. Below them the recommended stage with its reason.
 */
export function StageQuestions({
  wish,
  time,
  onWish,
  onTime,
}: {
  wish: IstStage | null
  time: IstStage | null
  onWish: (wish: IstStage) => void
  onTime: (time: IstStage) => void
}) {
  const recommended = wish !== null && time !== null ? recommendStage(wish, time) : null
  return (
    <>
      <div className="card stage-question" role="radiogroup" aria-label="Was willst du über dein Geld wissen?">
        <h2>Was willst du über dein Geld wissen?</h2>
        <div className="wish-ladder">
          {WISHES.map((w, i) => (
            <button
              key={w.text}
              type="button"
              role="radio"
              aria-checked={wish === i + 1}
              className={wish === i + 1 ? 'wish on' : 'wish'}
              style={{ ['--step' as string]: i }}
              onClick={() => onWish((i + 1) as IstStage)}
            >
              <strong>{w.text}</strong>
              {w.note && <span className="tx-meta">{w.note}</span>}
            </button>
          ))}
        </div>
      </div>
      <div className="card stage-question" role="radiogroup" aria-label="Wie viel Zeit willst du investieren?">
        <h2>Wie viel Zeit willst du investieren?</h2>
        <div className="time-options">
          {TIMES.map((t, i) => (
            <button
              key={t}
              type="button"
              role="radio"
              aria-checked={time === i + 1}
              className={time === i + 1 ? 'time-option on' : 'time-option'}
              onClick={() => onTime((i + 1) as IstStage)}
            >
              {t}
            </button>
          ))}
        </div>
      </div>
      {recommended !== null && wish !== null && (
        <p className="hint stage-reason">
          <span className="badge planned">
            Stufe {recommended}: {stageName(recommended)}
          </span>{' '}
          {recommended < wish
            ? `Weil du „${TIMES[recommended - 1]}“ investieren willst. Für „${WISHES[wish - 1].text.replace('… und ', '')}“ bräuchtest du mindestens „${TIMES[wish - 1]}“.`
            : 'Passt zu dem, was du wissen willst, und zu deiner Zeit.'}
        </p>
      )}
    </>
  )
}
