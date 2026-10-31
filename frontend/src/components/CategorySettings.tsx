import { useState, type FocusEvent, type FormEvent } from 'react'
import { useCategories, useCreateCategory, useDeleteCategory, useUpdateCategory } from '../api/categories'
import { ApiError } from '../api/client'
import type { Category, CategoryUpdate } from '../api/types'
import { currentMonth } from '../format'

/** First field message of a 400, otherwise the problem detail (e.g. the 409 texts). */
function errorText(error: unknown, fallback: string): string {
  if (error instanceof ApiError && error.problem.errors?.length) {
    return error.problem.errors[0].message
  }
  return error instanceof Error ? error.message : fallback
}

function NewCategoryForm() {
  const create = useCreateCategory()
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (name.trim() === '') {
      setError('Der Name ist Pflicht.')
      return
    }
    setError(null)
    try {
      await create.mutateAsync({ name: name.trim() })
      setName('')
    } catch (e) {
      setError(errorText(e, 'Anlegen fehlgeschlagen.'))
    }
  }

  return (
    <form className="category-new" noValidate onSubmit={handleSubmit}>
      <label>
        Neue Kategorie
        <input
          value={name}
          maxLength={50}
          onChange={(e) => setName(e.target.value)}
          aria-invalid={error ? true : undefined}
        />
      </label>
      <button type="submit" disabled={create.isPending}>
        Hinzufügen
      </button>
      {error && <p className="error">{error}</p>}
    </form>
  )
}

interface RowProps {
  category: Category
  previous?: Category
  next?: Category
}

/** One category: the name is saved when the focus leaves the row or on Enter, like the targets (US2). */
function CategoryRow({ category, previous, next }: RowProps) {
  const update = useUpdateCategory()
  const remove = useDeleteCategory()
  const [name, setName] = useState(category.name)
  const [error, setError] = useState<string | null>(null)
  const busy = update.isPending || remove.isPending

  async function send(changes: Partial<CategoryUpdate>) {
    setError(null)
    try {
      await update.mutateAsync({
        id: category.id,
        input: { name: category.name, sortOrder: category.sortOrder, archived: category.archived, ...changes },
      })
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
    }
  }

  function saveName() {
    if (name.trim() === category.name) {
      return
    }
    if (name.trim() === '') {
      setError('Der Name ist Pflicht.')
      return
    }
    void send({ name: name.trim() })
  }

  async function handleDelete() {
    if (!window.confirm(`Kategorie „${category.name}“ löschen?`)) {
      return
    }
    setError(null)
    try {
      await remove.mutateAsync(category.id)
    } catch (e) {
      setError(errorText(e, 'Löschen fehlgeschlagen.'))
    }
  }

  function handleBlur(event: FocusEvent<HTMLFormElement>) {
    if (!event.currentTarget.contains(event.relatedTarget)) {
      saveName()
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    saveName()
  }

  return (
    <form
      className={category.archived ? 'category-row archived' : 'category-row'}
      aria-label={category.name}
      noValidate
      onSubmit={handleSubmit}
      onBlur={handleBlur}
    >
      <input
        aria-label="Name"
        value={name}
        maxLength={50}
        enterKeyHint="done"
        onChange={(e) => setName(e.target.value)}
        aria-invalid={error ? true : undefined}
      />
      <span className="category-actions">
        {!category.archived && (
          <>
            <button
              type="button"
              aria-label="Nach oben"
              disabled={busy || !previous}
              onClick={() => previous && void send({ sortOrder: previous.sortOrder })}
            >
              ↑
            </button>
            <button
              type="button"
              aria-label="Nach unten"
              disabled={busy || !next}
              onClick={() => next && void send({ sortOrder: next.sortOrder })}
            >
              ↓
            </button>
          </>
        )}
        <button type="button" disabled={busy} onClick={() => void send({ archived: !category.archived })}>
          {category.archived ? 'Reaktivieren' : 'Archivieren'}
        </button>
        <button type="button" disabled={busy} onClick={() => void handleDelete()}>
          Löschen
        </button>
      </span>
      {error && <p className="error">{error}</p>}
    </form>
  )
}

/** Settings › Kategorien (US10): create, rename, reorder, archive and delete. */
export function CategorySettings() {
  const categories = useCategories(currentMonth())

  if (categories.isPending) {
    return <p>Lade Kategorien …</p>
  }
  if (categories.error) {
    return <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>
  }

  const active = categories.data.filter((c) => !c.archived)
  const archived = categories.data.filter((c) => c.archived)

  return (
    <section>
      <h3>Kategorien</h3>
      <p className="hint">
        Namen werden gespeichert, sobald du ein Feld verlässt oder Enter drückst. Löschen geht nur, solange eine
        Kategorie unbenutzt ist; sonst archiviere sie. Archivierte Kategorien fehlen im Formular und in der Planung,
        vergangene Monate zeigen sie weiter.
      </p>
      <NewCategoryForm />
      <div className="category-list">
        {active.map((category, index) => (
          <CategoryRow
            key={`${category.id}-${category.name}`}
            category={category}
            previous={active[index - 1]}
            next={active[index + 1]}
          />
        ))}
      </div>
      {archived.length > 0 && (
        <>
          <h4>Archiviert</h4>
          <div className="category-list">
            {archived.map((category) => (
              <CategoryRow key={`${category.id}-${category.name}`} category={category} />
            ))}
          </div>
        </>
      )}
    </section>
  )
}
