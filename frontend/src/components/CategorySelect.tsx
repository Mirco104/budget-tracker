import { useState, type ReactNode } from 'react'
import { useCreateCategory } from '../api/categories'
import type { Category } from '../api/types'
import { InlineCreate } from './ui/InlineCreate'

const NEW = 'new'

interface Props {
  /** The chosen category id as string, '' for none (or a value of one of the leading options). */
  value: string
  onChange: (value: string) => void
  categories: Category[]
  /** The first option with the value '', e.g. "Kategorie wählen …". */
  placeholder: string
  /** Options between the placeholder and the categories, e.g. Umbuchung. */
  leading?: ReactNode
  ariaLabel?: string
  labelledBy?: string
  invalid?: boolean
  disabled?: boolean
  /** Accessible name of the name field of a new category. */
  createLabel?: string
  /** Called with a category created here, before it is chosen. */
  onCreated?: (category: Category) => void
}

/**
 * 27f4: the one category selection of the app. It hides archived categories (except the chosen one) and ends with
 * "+ Neue Kategorie …", which creates the category right here and chooses it (08.11.2026).
 */
export function CategorySelect({
  value,
  onChange,
  categories,
  placeholder,
  leading,
  ariaLabel,
  labelledBy,
  invalid,
  disabled,
  createLabel = 'Neue Kategorie',
  onCreated,
}: Props) {
  const create = useCreateCategory()
  const [creating, setCreating] = useState(false)
  // A category created here, until the list of the caller contains it
  const [created, setCreated] = useState<Category[]>([])
  const all = [...categories, ...created.filter((c) => !categories.some((l) => l.id === c.id))]
  const options = all.filter((c) => !c.archived || String(c.id) === value)

  async function handleCreate(name: string) {
    const category = await create.mutateAsync({ name })
    setCreated((current) => [...current, category])
    setCreating(false)
    onCreated?.(category)
    onChange(String(category.id))
  }

  return (
    <>
      <select
        aria-label={ariaLabel}
        aria-labelledby={labelledBy}
        value={value}
        disabled={disabled}
        aria-invalid={invalid ? true : undefined}
        onChange={(e) => {
          if (e.target.value === NEW) {
            setCreating(true)
          } else {
            setCreating(false)
            onChange(e.target.value)
          }
        }}
      >
        <option value="">{placeholder}</option>
        {leading}
        {/* R32: the backend sends each parent followed by its subcategories; a subcategory shows its path */}
        {options.map((c) => (
          <option key={c.id} value={c.id}>
            {c.path}
          </option>
        ))}
        <option value={NEW}>+ Neue Kategorie …</option>
      </select>
      {creating && (
        <InlineCreate
          label={createLabel}
          placeholder="Name der Kategorie"
          onCreate={handleCreate}
          onCancel={() => setCreating(false)}
        />
      )}
    </>
  )
}
