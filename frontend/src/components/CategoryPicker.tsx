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
  /** Accessible name of the first field when it has no visible label (import). */
  ariaLabel?: string
  /**
   * Compact (import rows and cards): the second field has no visible label but an accessible name, and its first
   * option says "– keine Unterkategorie –".
   */
  subAriaLabel?: string
  invalid?: boolean
  disabled?: boolean
  /** Accessible name of the name field of a new top-level category. */
  createLabel?: string
  onCreated?: (category: Category) => void
}

/**
 * US37 (14.11.2026, decided with the user): two fields, "Kategorie" with the top-level categories and below it
 * "Unterkategorie (optional)" with the subcategories of the chosen one. "– keine –" books on the category itself;
 * "+ Neue Unterkategorie …" creates one below the chosen category. The value is always the one category the booking
 * gets, so the caller does not change. Used everywhere a category is chosen for a booking or an item (dialogs and,
 * since the second review, the import); only the filters keep one list.
 */
export function CategoryPicker({
  value,
  onChange,
  categories,
  placeholder,
  leading,
  labelledBy,
  ariaLabel,
  subAriaLabel,
  invalid,
  disabled,
  createLabel,
  onCreated,
}: Props) {
  const subLabel = useId()
  const chosen = categories.find((c) => String(c.id) === value)
  const main = chosen?.parentId != null ? String(chosen.parentId) : value
  const mainCategory = categories.find((c) => String(c.id) === main)
  const subcategories = mainCategory ? categories.filter((c) => c.parentId === mainCategory.id) : []
  const compact = subAriaLabel !== undefined

  const sub = mainCategory && (
    <CategorySelect
      labelledBy={compact ? undefined : subLabel}
      ariaLabel={compact ? subAriaLabel : undefined}
      value={chosen?.parentId != null ? value : ''}
      onChange={(next) => onChange(next === '' ? main : next)}
      categories={subcategories}
      placeholder={compact ? '– keine Unterkategorie –' : '– keine –'}
      parentId={mainCategory.id}
      disabled={disabled}
      createLabel={`Neue Unterkategorie von ${mainCategory.name}`}
      onCreated={onCreated}
    />
  )

  // Compact: both fields form one column, so the subcategory stands right below its category in any row (#120 review)
  const Wrapper = compact ? 'span' : 'div'
  return (
    <Wrapper className={compact ? 'category-picker compact' : 'category-picker'}>
      <CategorySelect
        labelledBy={labelledBy}
        ariaLabel={ariaLabel}
        value={main}
        onChange={onChange}
        categories={categories.filter((c) => c.parentId === null)}
        placeholder={placeholder}
        leading={leading}
        invalid={invalid}
        disabled={disabled}
        createLabel={createLabel}
        onCreated={onCreated}
      />
      {sub &&
        (compact ? (
          sub
        ) : (
          <div className="category-sub">
            <span id={subLabel} className="category-sub-label">
              Unterkategorie (optional)
            </span>
            {sub}
          </div>
        ))}
    </Wrapper>
  )
}
