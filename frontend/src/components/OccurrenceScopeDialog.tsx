import type { OccurrenceScope } from '../api/types'

interface Props {
  /** e.g. "„Streaming“ vom 05.10.2026 bearbeiten" */
  title: string
  onChoose: (scope: OccurrenceScope) => void
  onCancel: () => void
}

/** Asks which entries of a series an edit or delete applies to (R13). There is no "all entries" option. */
export function OccurrenceScopeDialog({ title, onChoose, onCancel }: Props) {
  return (
    <div className="scope-dialog" role="dialog" aria-modal="true" aria-labelledby="scope-dialog-title">
      <h3 id="scope-dialog-title">{title}</h3>
      <p className="hint">Frühere Einträge bleiben unverändert.</p>
      <div>
        <button type="button" onClick={() => onChoose('THIS')}>
          Nur dieser Eintrag
        </button>{' '}
        <button type="button" onClick={() => onChoose('FOLLOWING')}>
          Dieser und alle folgenden
        </button>{' '}
        <button type="button" onClick={onCancel}>
          Abbrechen
        </button>
      </div>
    </div>
  )
}
