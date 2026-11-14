import { useState } from 'react'
import { errorText } from '../api/client'
import {
  useSaveBankCategoryMappings,
  type BankCategoryMappingInput,
  type ImportBankCategory,
  type ImportProfile,
} from '../api/imports'
import type { Category } from '../api/types'
import { CategorySelect } from './CategorySelect'

/** The value of the option "+ Neu anlegen: …" (the name of the bank). */
const BANK = 'bank'

/** The two fields of one bank category (US38): a top-level category or BANK, and a subcategory, BANK or '' for none. */
interface Choice {
  parent: string
  child: string
}

function sameName(a: string, b: string): boolean {
  return a.trim().toLowerCase() === b.trim().toLowerCase()
}

/** The child field after the parent changed: an existing subcategory with the name of the bank, else "Neu anlegen". */
function childFor(item: ImportBankCategory, parent: string, categories: Category[]): string {
  if (item.child === null) {
    return ''
  }
  const existing = categories.find((c) => String(c.parentId) === parent && !c.archived && sameName(c.name, item.child as string))
  return existing ? String(existing.id) : BANK
}

/** Preset from the stored mapping, else from the match (R33): an existing or guessed category, else "Neu anlegen". */
function initialChoice(item: ImportBankCategory, categories: Category[]): Choice {
  if (item.mappedCategoryId !== null) {
    const mapped = categories.find((c) => c.id === item.mappedCategoryId)
    if (mapped?.parentId != null) {
      return { parent: String(mapped.parentId), child: String(mapped.id) }
    }
    return { parent: String(item.mappedCategoryId), child: '' }
  }
  if (item.parentCategoryId === null) {
    return { parent: BANK, child: item.child === null ? '' : BANK }
  }
  const parent = String(item.parentCategoryId)
  if (item.child === null) {
    return { parent, child: '' }
  }
  return { parent, child: item.childCategoryId !== null ? String(item.childCategoryId) : BANK }
}

/** The mapping the two fields store, or null while no category is chosen. */
function mappingOf(item: ImportBankCategory, choice: Choice): BankCategoryMappingInput | null {
  const { bankCategory } = item
  if (choice.parent === BANK) {
    return { bankCategory, parentName: item.parent, ...(choice.child === BANK && item.child ? { name: item.child } : {}) }
  }
  if (choice.parent === '') {
    return null
  }
  if (choice.child === BANK && item.child) {
    return { bankCategory, parentId: Number(choice.parent), name: item.child }
  }
  return { bankCategory, categoryId: Number(choice.child === '' ? choice.parent : choice.child) }
}

function pathOf(categories: Category[], id: number | null): string {
  return categories.find((c) => c.id === id)?.path ?? ''
}

/** "vermutet: …" for a guessed match, so a fuzzy mapping is recognisable (US38). */
function guessOf(item: ImportBankCategory, categories: Category[]): string | null {
  if (item.child !== null && item.childStatus === 'GUESSED' && item.childCategoryId !== null) {
    return pathOf(categories, item.childCategoryId)
  }
  return item.parentStatus === 'GUESSED' ? pathOf(categories, item.parentCategoryId) : null
}

function BankCategoryRow({
  item,
  choice,
  categories,
  applied,
  pending,
  onChange,
  onApply,
  onEdit,
}: {
  item: ImportBankCategory
  choice: Choice
  categories: Category[]
  /** The category taken over, while the row shows it. */
  applied: number | undefined
  pending: boolean
  onChange: (choice: Choice) => void
  onApply: () => void
  onEdit: () => void
}) {
  const guess = guessOf(item, categories)
  const bankChild = item.child !== null && (
    <option value={BANK}>+ Neu anlegen: {item.child}</option>
  )
  const name = (
    <span className="bank-category-name">
      {item.bankCategory} <span className="hint">({item.rows === 1 ? '1 Zeile' : `${item.rows} Zeilen`})</span>
      {guess && applied === undefined && <span className="hint bank-category-guess">vermutet: {guess}</span>}
    </span>
  )

  if (applied !== undefined) {
    return (
      <li className="bank-category" aria-label={item.bankCategory}>
        {name}
        <span className="bank-category-applied">✓ {pathOf(categories, applied)}</span>
        <button type="button" onClick={onEdit}>
          Ändern
        </button>
      </li>
    )
  }

  return (
    <li className="bank-category" aria-label={item.bankCategory}>
      {name}
      <span className="bank-category-fields">
        <span className="category-sub-label" aria-hidden="true">
          Kategorie
        </span>
        <CategorySelect
          ariaLabel={`Kategorie für ${item.bankCategory}`}
          value={choice.parent}
          onChange={(parent) => onChange({ parent, child: childFor(item, parent, categories) })}
          categories={categories.filter((c) => c.parentId === null)}
          placeholder="Kategorie wählen …"
          leading={<option value={BANK}>+ Neu anlegen: {item.parent}</option>}
          createLabel={`Neue Kategorie für ${item.bankCategory}`}
        />
        <span className="category-sub-label" aria-hidden="true">
          Unterkategorie (optional)
        </span>
        {choice.parent === BANK || choice.parent === '' ? (
          <select
            aria-label={`Unterkategorie für ${item.bankCategory}`}
            value={choice.child}
            disabled={choice.parent === ''}
            onChange={(e) => onChange({ ...choice, child: e.target.value })}
          >
            <option value="">– keine –</option>
            {bankChild}
          </select>
        ) : (
          <CategorySelect
            ariaLabel={`Unterkategorie für ${item.bankCategory}`}
            value={choice.child}
            onChange={(child) => onChange({ ...choice, child })}
            categories={categories.filter((c) => String(c.parentId) === choice.parent)}
            placeholder="– keine –"
            leading={bankChild}
            parentId={Number(choice.parent)}
            createLabel={`Neue Unterkategorie für ${item.bankCategory}`}
          />
        )}
      </span>
      <button type="button" disabled={pending || mappingOf(item, choice) === null} onClick={onApply}>
        Übernehmen
      </button>
    </li>
  )
}

/**
 * R33, US38: the card "Kategorien der Bank". It lists the bank categories of the file without a mapping, each with the
 * two category fields preset from the match ("+ Neu anlegen: …" where nothing fits), and folded the known mappings to
 * change them. "Übernehmen" in a row, or "Alle übernehmen" for all open rows, creates the new categories, stores the
 * mappings and hands the category of each bank category to the page. A row not taken over stores nothing.
 */
export function BankCategoryCard({
  profile,
  items,
  categories,
  onApplied,
}: {
  profile: ImportProfile
  items: ImportBankCategory[]
  categories: Category[]
  /** The category per bank category for the rows of the page. */
  onApplied: (categoryByBankCategory: Record<string, number>) => void
}) {
  const save = useSaveBankCategoryMappings()
  const [choices, setChoices] = useState<Record<string, Choice>>(() =>
    Object.fromEntries(items.map((item) => [item.bankCategory, initialChoice(item, categories)])),
  )
  const [applied, setApplied] = useState<Record<string, number>>({})
  const [error, setError] = useState<string | null>(null)

  const open = items.filter((item) => item.mappedCategoryId === null)
  const known = items.filter((item) => item.mappedCategoryId !== null)
  if (open.length === 0) {
    return null
  }

  const waiting = open.filter((item) => applied[item.bankCategory] === undefined)
  const mappings = waiting
    .map((item) => mappingOf(item, choices[item.bankCategory]))
    .filter((m): m is BankCategoryMappingInput => m !== null)
  const created = mappings.filter((m) => m.categoryId === undefined).length
  const assigned = mappings.length - created

  async function apply(toApply: ImportBankCategory[]) {
    setError(null)
    const changed: BankCategoryMappingInput[] = []
    const result: Record<string, number> = {}
    for (const item of toApply) {
      const mapping = mappingOf(item, choices[item.bankCategory])
      if (!mapping) {
        continue
      }
      // A known mapping is only sent when it changes
      if (item.mappedCategoryId !== null && mapping.categoryId === item.mappedCategoryId) {
        result[item.bankCategory] = item.mappedCategoryId
      } else {
        changed.push(mapping)
      }
    }
    try {
      const stored = changed.length > 0 ? await save.mutateAsync({ profile, mappings: changed }) : []
      for (const entry of stored) {
        result[entry.bankCategory] = entry.categoryId
      }
      onApplied(result)
      setApplied((current) => ({ ...current, ...result }))
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
    }
  }

  const row = (item: ImportBankCategory) => (
    <BankCategoryRow
      key={item.bankCategory}
      item={item}
      choice={choices[item.bankCategory]}
      categories={categories}
      applied={applied[item.bankCategory]}
      pending={save.isPending}
      onChange={(choice) => setChoices((current) => ({ ...current, [item.bankCategory]: choice }))}
      onApply={() => void apply([item])}
      onEdit={() => {
        const category = categories.find((c) => c.id === applied[item.bankCategory])
        if (category) {
          setChoices((current) => ({
            ...current,
            [item.bankCategory]:
              category.parentId === null
                ? { parent: String(category.id), child: '' }
                : { parent: String(category.parentId), child: String(category.id) },
          }))
        }
        setApplied((current) => {
          const next = { ...current }
          delete next[item.bankCategory]
          return next
        })
      }}
    />
  )

  return (
    <section className="card import-series bank-categories" aria-label="Kategorien der Bank">
      <p>
        {waiting.length === 0
          ? 'Alle Kategorien der Bank sind übernommen.'
          : `Die Datei enthält ${waiting.length === 1 ? '1 Kategorie' : `${waiting.length} Kategorien`} der Bank: ${created} ${created === 1 ? 'wird' : 'werden'} neu angelegt, ${assigned} deinen Kategorien zugeordnet.`}
      </p>
      <details>
        <summary>Kategorien der Bank anzeigen</summary>
        <ul className="bank-category-list">{open.map(row)}</ul>
      </details>
      {known.length > 0 && (
        <details>
          <summary>Bekannte Zuordnungen ({known.length})</summary>
          <ul className="bank-category-list">{known.map(row)}</ul>
        </details>
      )}
      {mappings.length > 0 && (
        <div className="import-series-actions">
          <button type="button" className="primary" disabled={save.isPending} onClick={() => void apply(waiting)}>
            Alle übernehmen ({mappings.length})
          </button>
          <span className="hint">Neue Kategorien werden sofort angelegt; deine Wahl gilt auch für spätere Importe.</span>
        </div>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </section>
  )
}
