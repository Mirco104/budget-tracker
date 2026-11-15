import { useId, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useCreateCategory } from '../api/categories'
import { errorText } from '../api/client'
import type { Category } from '../api/types'
import { CategoryIcon } from './ui/CategoryIcon'
import { CATEGORY_ICON_KEYS, categoryIconLabel } from './ui/categoryIcons'
import { Dialog } from './ui/Dialog'

/**
 * R32: "Oberkategorie: keine / …". Only active top-level categories can be a parent, never the category itself; a
 * category with subcategories cannot get one, so the caller does not show the field then.
 */
export function ParentSelect({
  id,
  value,
  onChange,
  categories,
  exclude,
  disabled,
  noneLabel = 'keine',
}: {
  /** The option for no parent; "keine" in the row menu, "– keine –" in the window of a new category. */
  noneLabel?: string
  id: string
  value: number | null
  onChange: (parentId: number | null) => void
  categories: Category[]
  exclude?: number
  disabled?: boolean
}) {
  const parents = categories.filter(
    (c) => c.parentId === null && c.id !== exclude && (!c.archived || c.id === value),
  )
  return (
    <select
      id={id}
      value={value === null ? '' : String(value)}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value === '' ? null : Number(e.target.value))}
    >
      <option value="">{noneLabel}</option>
      {parents.map((c) => (
        <option key={c.id} value={c.id}>
          {c.name}
        </option>
      ))}
    </select>
  )
}

/** The fixed list of icons as a grid of buttons (US28); the current one is pressed. Escape closes it. */
export function IconPicker({ icon, onPick, onClose }: { icon: string; onPick: (icon: string) => void; onClose: () => void }) {
  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      onClose()
    }
  }

  return (
    <div className="icon-picker" role="group" aria-label="Icon wählen" onKeyDown={handleKeyDown}>
      {CATEGORY_ICON_KEYS.map((key) => (
        <button
          key={key}
          type="button"
          aria-label={categoryIconLabel(key)}
          title={categoryIconLabel(key)}
          aria-pressed={key === icon}
          onClick={() => onPick(key)}
        >
          <CategoryIcon icon={key} />
        </button>
      ))}
    </div>
  )
}

/** The icon a new category starts with, as in the backend (US28). */
const DEFAULT_ICON = 'tag'

/**
 * Decision 66: the one window to create a category, from the settings and from every category selection: name and
 * icon (US28). Only the settings choose a parent ("Unterkategorie von", R32); a selection knows it: none for a
 * category, the chosen one for a subcategory. A subcategory starts with the icon of its parent until the user picks one
 * (US37). The new category appears at its alphabetical place (decision 38); onCreated gets it, e.g. to choose it.
 */
export function CategoryDialog({
  categories,
  title,
  parentId: initialParent = null,
  chooseParent = false,
  onCreated,
  onClose,
}: {
  /** Shows "Unterkategorie von (optional)" (settings); without it the category gets parentId. */
  chooseParent?: boolean
  /** All categories of the user, for the choice of the Oberkategorie. */
  categories: Category[]
  /** e.g. "Neue Kategorie für REWE"; without it "Neue Kategorie" or "Neue Unterkategorie". */
  title?: string
  parentId?: number | null
  onCreated?: (category: Category) => void
  onClose: () => void
}) {
  const create = useCreateCategory()
  const [name, setName] = useState('')
  const [parentId, setParentId] = useState<number | null>(initialParent)
  const [pickedIcon, setPickedIcon] = useState<string | null>(null)
  const icon = pickedIcon ?? categories.find((c) => c.id === parentId)?.icon ?? DEFAULT_ICON
  const [picking, setPicking] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const nameId = useId()
  const parentFieldId = useId()
  const iconId = useId()

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (name.trim() === '') {
      setError('Der Name ist Pflicht.')
      return
    }
    setError(null)
    try {
      const category = await create.mutateAsync({ name: name.trim(), icon, parentId })
      onCreated?.(category)
      onClose()
    } catch (e) {
      setError(errorText(e, 'Anlegen fehlgeschlagen.'))
    }
  }

  return (
    <Dialog title={title ?? (initialParent === null ? 'Neue Kategorie' : 'Neue Unterkategorie')} onClose={onClose}>
      <form className="booking-form" noValidate onSubmit={(e) => void handleSubmit(e)}>
        <div className="field">
          <label htmlFor={nameId}>Name</label>
          <input
            id={nameId}
            value={name}
            maxLength={50}
            onChange={(e) => setName(e.target.value)}
            aria-invalid={error ? true : undefined}
          />
        </div>
        <div className="field-row">
          {chooseParent && (
            <div className="field">
              <label htmlFor={parentFieldId}>Unterkategorie von (optional)</label>
              <ParentSelect
                id={parentFieldId}
                value={parentId}
                onChange={setParentId}
                categories={categories}
                noneLabel="– keine –"
              />
            </div>
          )}
          <div className="field">
            <span id={iconId}>Icon</span>
            <button
              type="button"
              className="icon-button category-icon-button"
              aria-labelledby={iconId}
              aria-description={categoryIconLabel(icon)}
              aria-expanded={picking}
              onClick={() => setPicking(!picking)}
            >
              <CategoryIcon icon={icon} />
            </button>
          </div>
        </div>
        {picking && (
          <IconPicker
            icon={icon}
            onPick={(picked) => {
              setPickedIcon(picked)
              setPicking(false)
            }}
            onClose={() => setPicking(false)}
          />
        )}
        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}
        <div className="dialog-actions">
          <div />
          <div className="dialog-buttons">
            <button type="button" onClick={onClose}>
              Abbrechen
            </button>
            <button type="submit" className="primary" disabled={create.isPending}>
              Anlegen
            </button>
          </div>
        </div>
      </form>
    </Dialog>
  )
}
