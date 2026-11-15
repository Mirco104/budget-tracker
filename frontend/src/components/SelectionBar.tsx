import type { ReactNode } from 'react'
import type { Category } from '../api/types'
import { CategoryPicker } from './CategoryPicker'

interface Props {
  /** The chosen entries among the shown ones. */
  count: number
  /** The shown entries that can be chosen. */
  shown: number
  onSelectAll: () => void
  onClear: () => void
  categories: Category[]
  /** The category for the chosen entries as string, '' for none yet (or a value of a leading option). */
  value: string
  onChange: (value: string) => void
  /** Options before the categories, e.g. Umbuchung in the import. */
  leading?: ReactNode
  onAssign: () => void
  onDone: () => void
  busy?: boolean
}

/**
 * Ticket #136: the bar of the multiple selection in Transaktionen and in the import. It chooses all shown entries or
 * none and gives the chosen ones one category; "+ Neue Kategorie …" works as in every category selection.
 */
export function SelectionBar({
  count,
  shown,
  onSelectAll,
  onClear,
  categories,
  value,
  onChange,
  leading,
  onAssign,
  onDone,
  busy = false,
}: Props) {
  const all = shown > 0 && count === shown
  return (
    <section className="selection-bar" aria-label="Auswahl">
      <div className="selection-count">
        <span aria-live="polite">{count === 1 ? '1 ausgewählt' : `${count} ausgewählt`}</span>
        {all ? (
          <button type="button" className="link-button" onClick={onClear}>
            Auswahl aufheben
          </button>
        ) : (
          <button type="button" className="link-button" disabled={shown === 0} onClick={onSelectAll}>
            {shown === 1 ? 'Den angezeigten auswählen' : `Alle ${shown} angezeigten auswählen`}
          </button>
        )}
      </div>
      <div className="selection-actions">
        <CategoryPicker
          ariaLabel="Kategorie für die Auswahl"
          subAriaLabel="Unterkategorie für die Auswahl"
          value={value}
          onChange={onChange}
          categories={categories}
          placeholder="Kategorie wählen …"
          leading={leading}
          disabled={busy}
          createLabel="Neue Kategorie für die Auswahl"
        />
        <button type="button" className="primary" disabled={count === 0 || value === '' || busy} onClick={onAssign}>
          Zuordnen
        </button>
        <button type="button" onClick={onDone}>
          Fertig
        </button>
      </div>
    </section>
  )
}
