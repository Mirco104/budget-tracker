import { useRef, useState } from 'react'
import { useCategories, useDeleteCategory, useUpdateCategory } from '../api/categories'
import { errorText } from '../api/client'
import type { Category, CategoryUpdate } from '../api/types'
import { currentMonth } from '../format'
import { CategoryDialog, IconPicker, ParentSelect } from './CategoryDialog'
import { CategoryIcon } from './ui/CategoryIcon'
import { categoryIconLabel } from './ui/categoryIcons'
import { Icon } from './ui/Icon'
import { RowMenu } from './ui/RowMenu'
import { Loading } from './ui/Loading'

type Mode = 'view' | 'rename' | 'icon' | 'parent' | 'confirmDelete'

/**
 * One active category: icon, name and the menu "⋯" (US10, US28). A subcategory stands indented below its parent, and
 * "Oberkategorie ändern" moves it (R32).
 */
function CategoryRow({ category, categories }: { category: Category; categories: Category[] }) {
  const update = useUpdateCategory()
  const remove = useDeleteCategory()
  const [mode, setMode] = useState<Mode>('view')
  const [menuOpen, setMenuOpen] = useState(false)
  const [name, setName] = useState(category.name)
  const [error, setError] = useState<string | null>(null)
  const menuButton = useRef<HTMLButtonElement>(null)
  const busy = update.isPending || remove.isPending
  const hasChildren = categories.some((c) => c.parentId === category.id)

  async function send(changes: Partial<CategoryUpdate>): Promise<boolean> {
    setError(null)
    try {
      await update.mutateAsync({
        id: category.id,
        input: {
          name: category.name,
          archived: category.archived,
          icon: category.icon,
          parentId: category.parentId,
          ...changes,
        },
      })
      return true
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
      return false
    }
  }

  function closeMenu() {
    setMenuOpen(false)
    menuButton.current?.focus()
  }

  function choose(action: () => void) {
    setMenuOpen(false)
    action()
  }

  // Enter submits and the following blur must not send the name a second time
  const saving = useRef(false)

  async function saveName() {
    if (saving.current) {
      return
    }
    const trimmed = name.trim()
    if (trimmed === category.name) {
      setMode('view')
      return
    }
    if (trimmed === '') {
      setError('Der Name ist Pflicht.')
      return
    }
    saving.current = true
    const saved = await send({ name: trimmed })
    saving.current = false
    if (saved) {
      setMode('view')
    }
  }

  async function handleDelete() {
    setError(null)
    try {
      await remove.mutateAsync(category.id)
    } catch (e) {
      setError(errorText(e, 'Löschen fehlgeschlagen.'))
      setMode('view')
    }
  }

  return (
    <li className={category.parentId === null ? 'category-row' : 'category-row sub'} aria-label={category.path}>
      <div className="category-line">
        <button
          type="button"
          className="icon-button category-icon-button"
          aria-label={`Icon ändern (${categoryIconLabel(category.icon)})`}
          aria-expanded={mode === 'icon'}
          disabled={busy}
          onClick={() => setMode(mode === 'icon' ? 'view' : 'icon')}
        >
          <CategoryIcon icon={category.icon} />
        </button>

        {mode === 'rename' ? (
          <form
            className="rename-form"
            noValidate
            onSubmit={(event) => {
              event.preventDefault()
              void saveName()
            }}
          >
            <input
              aria-label={`Neuer Name für ${category.name}`}
              // Opened on purpose from the menu
              autoFocus
              value={name}
              maxLength={50}
              enterKeyHint="done"
              onChange={(e) => setName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Escape') {
                  setName(category.name)
                  setError(null)
                  setMode('view')
                }
              }}
              onBlur={() => void saveName()}
              aria-invalid={error ? true : undefined}
            />
          </form>
        ) : (
          <span className="category-name">{category.name}</span>
        )}

        <div className="row-menu-anchor">
          <button
            ref={menuButton}
            type="button"
            className="icon-button"
            aria-label={`Aktionen für ${category.name}`}
            aria-expanded={menuOpen}
            disabled={busy}
            onClick={() => setMenuOpen(!menuOpen)}
          >
            <Icon name="more" />
          </button>
          {menuOpen && (
            <RowMenu label={`Aktionen für ${category.name}`} onClose={closeMenu}>
              <button type="button" className="menu-item" onClick={() => choose(() => setMode('rename'))}>
                Umbenennen
              </button>
              <button type="button" className="menu-item" onClick={() => choose(() => setMode('icon'))}>
                Icon ändern
              </button>
              {!hasChildren && (
                <button type="button" className="menu-item" onClick={() => choose(() => setMode('parent'))}>
                  Oberkategorie ändern
                </button>
              )}
              <button type="button" className="menu-item" onClick={() => choose(() => void send({ archived: true }))}>
                Archivieren
              </button>
              <button
                type="button"
                className="menu-item danger"
                onClick={() => choose(() => setMode('confirmDelete'))}
              >
                Löschen
              </button>
            </RowMenu>
          )}
        </div>
      </div>

      {mode === 'icon' && (
        <IconPicker
          icon={category.icon}
          onPick={(icon) => {
            setMode('view')
            if (icon !== category.icon) {
              void send({ icon })
            }
          }}
          onClose={() => setMode('view')}
        />
      )}
      {mode === 'parent' && (
        <div className="dialog-actions confirm" role="group" aria-label={`Oberkategorie für ${category.name}`}>
          <label htmlFor={`parent-${category.id}`}>Oberkategorie</label>
          <ParentSelect
            id={`parent-${category.id}`}
            value={category.parentId}
            exclude={category.id}
            categories={categories}
            disabled={busy}
            onChange={(parentId) => {
              void send({ parentId }).then((saved) => saved && setMode('view'))
            }}
          />
          <div className="dialog-buttons">
            <button type="button" onClick={() => setMode('view')} disabled={busy}>
              Fertig
            </button>
          </div>
        </div>
      )}
      {mode === 'confirmDelete' && (
        <div className="dialog-actions confirm" role="group" aria-label="Löschen bestätigen">
          <span>„{category.name}“ löschen? Das geht nur, solange sie unbenutzt ist.</span>
          <div className="dialog-buttons">
            <button type="button" onClick={() => setMode('view')} disabled={busy}>
              Abbrechen
            </button>
            <button type="button" className="danger" onClick={() => void handleDelete()} disabled={busy}>
              Endgültig löschen
            </button>
          </div>
        </div>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}

/** An archived category: it can come back, or be deleted while unused (US10). */
function ArchivedRow({ category }: { category: Category }) {
  const update = useUpdateCategory()
  const remove = useDeleteCategory()
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const busy = update.isPending || remove.isPending

  async function restore() {
    setError(null)
    try {
      await update.mutateAsync({
        id: category.id,
        input: { name: category.name, archived: false, icon: category.icon, parentId: category.parentId },
      })
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
    }
  }

  async function handleDelete() {
    setError(null)
    try {
      await remove.mutateAsync(category.id)
    } catch (e) {
      setError(errorText(e, 'Löschen fehlgeschlagen.'))
      setConfirming(false)
    }
  }

  return (
    <li className="category-row archived" aria-label={category.path}>
      <div className="category-line">
        <CategoryIcon icon={category.icon} />
        <span className="category-name">{category.path}</span>
        {!confirming && (
          <>
            <button type="button" disabled={busy} onClick={() => void restore()}>
              Wiederherstellen
            </button>
            <button type="button" className="danger" disabled={busy} onClick={() => setConfirming(true)}>
              Löschen
            </button>
          </>
        )}
      </div>
      {confirming && (
        <div className="dialog-actions confirm" role="group" aria-label="Löschen bestätigen">
          <span>„{category.name}“ löschen? Das geht nur, solange sie unbenutzt ist.</span>
          <div className="dialog-buttons">
            <button type="button" onClick={() => setConfirming(false)} disabled={busy}>
              Abbrechen
            </button>
            <button type="button" className="danger" onClick={() => void handleDelete()} disabled={busy}>
              Endgültig löschen
            </button>
          </div>
        </div>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}

/** "+ Kategorie" opens the window "Neue Kategorie" (decision 66), the same as everywhere else. */
function NewCategoryButton({ categories }: { categories: Category[] }) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" className="add-category" onClick={() => setOpen(true)}>
        <Icon name="plus" size={18} />
        Kategorie
      </button>
      {open && <CategoryDialog categories={categories} chooseParent onClose={() => setOpen(false)} />}
    </>
  )
}

/**
 * Settings › Kategorien (US10, US28, US37): alphabetical (decision 38), each category followed by its subcategories
 * (R32), per row a menu "⋯".
 */
export function CategorySettings() {
  const categories = useCategories(currentMonth())

  if (categories.isPending) {
    return <Loading text="Lade Kategorien …" />
  }
  if (categories.error) {
    return <p className="error">Kategorien konnten nicht geladen werden: {categories.error.message}</p>
  }

  const active = categories.data.filter((c) => !c.archived)
  const archived = categories.data.filter((c) => c.archived)

  return (
    <>
      <section className="card settings-card" aria-labelledby="categories-title">
        <div className="settings-card-head">
          <div>
            <h2 id="categories-title">Kategorien</h2>
            <p className="hint">
              Kategorien stehen überall alphabetisch, Unterkategorien direkt unter ihrer Oberkategorie. Umbenennen, Icon
              ändern, Oberkategorie ändern, Archivieren und Löschen findest du im Menü „⋯“. Übersicht und Planung zählen
              Unterkategorien bei ihrer Oberkategorie mit.
            </p>
          </div>
          <NewCategoryButton categories={categories.data} />
        </div>
        <ul className="category-list">
          {active.map((category) => (
            <CategoryRow key={`${category.id}-${category.name}`} category={category} categories={categories.data} />
          ))}
        </ul>
      </section>

      {archived.length > 0 && (
        <section className="card settings-card" aria-labelledby="archived-title">
          <h2 id="archived-title">Archiviert ({archived.length})</h2>
          <p className="hint">
            Fehlen in den Formularen und in der Planung; vergangene Monate zeigen sie weiter.
          </p>
          <ul className="category-list">
            {archived.map((category) => (
              <ArchivedRow key={`${category.id}-${category.name}`} category={category} />
            ))}
          </ul>
        </section>
      )}
    </>
  )
}
