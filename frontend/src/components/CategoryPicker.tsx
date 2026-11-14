import { useId, type ReactNode } from 'react'
import type { Category } from '../api/types'
import { CategorySelect } from './CategorySelect'

interface Props {
  /** The chosen category id as string (a top-level category or a subcategory), '' or a value of a leading option. */
  value: string
  onChange: (value: string) => void
  categories: Category[]
  placeholder: string
  /** Options of the first field between the placeholder and the categories, e.g. Umbuchung. */
  leading?: ReactNode
  labelledBy?: string
  invalid?: boolean
  onCreated?: (category: Category) => void
}

/**
 * US37 (14.11.2026, decided with the user): two fields, "Kategorie" with the top-level categories and below it
 * "Unterkategorie (optional)" with the subcategories of the chosen one. "– keine –" books on the category itself;
 * "+ Neue Unterkategorie …" creates one below the chosen category. The value is always the one category the booking
 * gets, so the caller does not change.
 */
export function CategoryPicker({ value, onChange, categories, placeholder, leading, labelledBy, invalid, onCreated }: Props) {
  const subLabel = useId()
  const chosen = categories.find((c) => String(c.id) === value)
  const main = chosen?.parentId != null ? String(chosen.parentId) : value
  const mainCategory = categories.find((c) => String(c.id) === main)
  const subcategories = mainCategory ? categories.filter((c) => c.parentId === mainCategory.id) : []

  return (
    <>
      <CategorySelect
        labelledBy={labelledBy}
        value={main}
        onChange={onChange}
        categories={categories.filter((c) => c.parentId === null)}
        placeholder={placeholder}
        leading={leading}
        invalid={invalid}
        onCreated={onCreated}
      />
      {mainCategory && (
        <div className="category-sub">
          <span id={subLabel} className="category-sub-label">
            Unterkategorie (optional)
          </span>
          <CategorySelect
            labelledBy={subLabel}
            value={chosen?.parentId != null ? value : ''}
            onChange={(sub) => onChange(sub === '' ? main : sub)}
            categories={subcategories}
            placeholder="– keine –"
            parentId={mainCategory.id}
            createLabel={`Neue Unterkategorie von ${mainCategory.name}`}
            onCreated={onCreated}
          />
        </div>
      )}
    </>
  )
}
