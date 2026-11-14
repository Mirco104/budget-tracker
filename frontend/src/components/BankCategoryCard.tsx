import { useState } from 'react'
import { errorText } from '../api/client'
import {
  useSaveBankCategoryMappings,
  type BankCategoryMappingInput,
  type ImportBankCategory,
  type ImportProfile,
} from '../api/imports'
import type { Category } from '../api/types'
import { CategoryPicker } from './CategoryPicker'

/** What the user chose for one bank category (US38). */
type Kind = 'suggest' | 'new' | 'own' | 'skip'

interface Choice {
  kind: Kind
  /** The own category for "own", as string. */
  own: string
}

/**
 * The mapping the suggestion of the match stores, or null without a suggestion: an existing or guessed category, or a
 * new child below a guessed parent (R33).
 */
function suggestion(item: ImportBankCategory): BankCategoryMappingInput | null {
  if (item.child === null) {
    return item.parentCategoryId === null ? null : { bankCategory: item.bankCategory, categoryId: item.parentCategoryId }
  }
  if (item.childCategoryId !== null) {
    return { bankCategory: item.bankCategory, categoryId: item.childCategoryId }
  }
  if (item.parentStatus === 'GUESSED' && item.parentCategoryId !== null) {
    return { bankCategory: item.bankCategory, parentId: item.parentCategoryId, name: item.child }
  }
  return null
}

/** "Neu anlegen": the names of the bank; below an existing parent only the child is new. */
function creation(item: ImportBankCategory): BankCategoryMappingInput | null {
  if (item.parentStatus === 'EXISTING' && item.parentCategoryId !== null) {
    return item.child === null || item.childStatus === 'EXISTING'
      ? null
      : { bankCategory: item.bankCategory, parentId: item.parentCategoryId, name: item.child }
  }
  return { bankCategory: item.bankCategory, parentName: item.parent, ...(item.child ? { name: item.child } : {}) }
}

function initialChoice(item: ImportBankCategory): Choice {
  if (item.mappedCategoryId !== null) {
    return { kind: 'own', own: String(item.mappedCategoryId) }
  }
  if (item.childStatus === 'NEW' && item.parentStatus === 'EXISTING') {
    return { kind: 'new', own: '' }
  }
  return { kind: suggestion(item) ? 'suggest' : 'new', own: '' }
}

function mappingOf(item: ImportBankCategory, choice: Choice): BankCategoryMappingInput | null {
  switch (choice.kind) {
    case 'suggest':
      return suggestion(item)
    case 'new':
      return creation(item)
    case 'own':
      return choice.own === '' ? null : { bankCategory: item.bankCategory, categoryId: Number(choice.own) }
    default:
      return null
  }
}

function pathOf(categories: Category[], id: number | null): string {
  return categories.find((c) => c.id === id)?.path ?? ''
}

/** The text of a suggestion: "wird … zugeordnet" for an equal name, "Meinst du …?" for a guess (US38). */
function suggestionLabel(item: ImportBankCategory, categories: Category[]): string {
  if (item.child !== null && item.childCategoryId === null) {
    return `Meinst du ${pathOf(categories, item.parentCategoryId)}? Dann wird ${pathOf(categories, item.parentCategoryId)} › ${item.child} angelegt`
  }
  const id = item.child === null ? item.parentCategoryId : item.childCategoryId
  const guessed = item.child === null ? item.parentStatus === 'GUESSED' : item.childStatus === 'GUESSED' || item.parentStatus === 'GUESSED'
  return guessed ? `Meinst du ${pathOf(categories, id)}?` : `wird ${pathOf(categories, id)} zugeordnet`
}

function creationLabel(item: ImportBankCategory, categories: Category[]): string {
  const create = creation(item)
  if (create?.parentId !== undefined) {
    return `Neu anlegen: ${pathOf(categories, create.parentId)} › ${create.name}`
  }
  return `Neu anlegen: ${item.child ? `${item.parent} › ${item.child}` : item.parent}`
}

function BankCategoryRow({
  item,
  choice,
  categories,
  onChange,
}: {
  item: ImportBankCategory
  choice: Choice
  categories: Category[]
  onChange: (choice: Choice) => void
}) {
  const hasSuggestion = suggestion(item) !== null
  const canCreate = creation(item) !== null
  return (
    <li className="bank-category" aria-label={item.bankCategory}>
      <span className="bank-category-name">
        {item.bankCategory} <span className="hint">({item.rows === 1 ? '1 Zeile' : `${item.rows} Zeilen`})</span>
      </span>
      <select
        aria-label={`Wahl für ${item.bankCategory}`}
        value={choice.kind}
        onChange={(e) => onChange({ ...choice, kind: e.target.value as Kind })}
      >
        {hasSuggestion && <option value="suggest">{suggestionLabel(item, categories)}</option>}
        {canCreate && <option value="new">{creationLabel(item, categories)}</option>}
        <option value="own">Eigene Kategorie wählen …</option>
        <option value="skip">Nicht übernehmen</option>
      </select>
      {choice.kind === 'own' && (
        <CategoryPicker
          ariaLabel={`Eigene Kategorie für ${item.bankCategory}`}
          subAriaLabel={`Eigene Unterkategorie für ${item.bankCategory}`}
          value={choice.own}
          onChange={(own) => onChange({ ...choice, own })}
          categories={categories}
          placeholder="Kategorie wählen …"
          createLabel={`Neue Kategorie für ${item.bankCategory}`}
        />
      )}
    </li>
  )
}

/**
 * R33, US38: the card "Kategorien der Bank". It lists the bank categories of the file without a mapping, each with
 * its choice preset from the match, and folded the known mappings to change them. "Übernehmen" creates the new
 * categories, stores the mappings and hands the category of each bank category to the page; "Nicht übernehmen" hands
 * the existing parent over, if there is one.
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
  /** The category per bank category for the rows; changed tells which ones the user changed or newly mapped. */
  onApplied: (categoryByBankCategory: Record<string, number>) => void
}) {
  const save = useSaveBankCategoryMappings()
  const [choices, setChoices] = useState<Record<string, Choice>>(() =>
    Object.fromEntries(items.map((item) => [item.bankCategory, initialChoice(item)])),
  )
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  const open = items.filter((item) => item.mappedCategoryId === null)
  const known = items.filter((item) => item.mappedCategoryId !== null)
  if (done || open.length === 0) {
    return null
  }

  const chosen = open.map((item) => ({ item, choice: choices[item.bankCategory] }))
  const created = chosen.filter(({ item, choice }) => {
    const mapping = mappingOf(item, choice)
    return mapping !== null && mapping.categoryId === undefined
  }).length
  const assigned = chosen.filter(({ item, choice }) => mappingOf(item, choice)?.categoryId !== undefined).length

  async function apply() {
    setError(null)
    const mappings: BankCategoryMappingInput[] = []
    const skipped: Record<string, number> = {}
    for (const item of items) {
      const choice = choices[item.bankCategory]
      const mapping = mappingOf(item, choice)
      if (mapping) {
        // A known mapping is only sent when it changes
        if (item.mappedCategoryId === null || mapping.categoryId !== item.mappedCategoryId) {
          mappings.push(mapping)
        }
      } else if (choice.kind === 'skip' && item.parentStatus === 'EXISTING' && item.parentCategoryId !== null) {
        skipped[item.bankCategory] = item.parentCategoryId
      }
    }
    try {
      const stored = mappings.length > 0 ? await save.mutateAsync({ profile, mappings }) : []
      const result: Record<string, number> = { ...skipped }
      for (const item of known) {
        result[item.bankCategory] = item.mappedCategoryId as number
      }
      for (const entry of stored) {
        result[entry.bankCategory] = entry.categoryId
      }
      onApplied(result)
      setDone(true)
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
      onChange={(choice) => setChoices((current) => ({ ...current, [item.bankCategory]: choice }))}
    />
  )

  return (
    <section className="card import-series bank-categories" aria-label="Kategorien der Bank">
      <p>
        Die Datei enthält {open.length === 1 ? '1 Kategorie' : `${open.length} Kategorien`} der Bank:{' '}
        {created} {created === 1 ? 'wird' : 'werden'} neu angelegt, {assigned} deinen Kategorien zugeordnet.
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
      <div className="import-series-actions">
        <button type="button" className="primary" disabled={save.isPending} onClick={() => void apply()}>
          Übernehmen
        </button>
        <span className="hint">Neue Kategorien werden sofort angelegt; deine Wahl gilt auch für spätere Importe.</span>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </section>
  )
}
