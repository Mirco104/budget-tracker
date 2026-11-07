import { useState, type ChangeEvent, type FormEvent } from 'react'
import { useBankAccounts } from '../api/bankAccounts'
import { useCategories, useCreateCategory } from '../api/categories'
import { errorText } from '../api/client'
import {
  useCommitImport,
  useDeleteImport,
  useImports,
  usePreviewImport,
  type ImportBatch,
  type ImportEntry,
  type ImportPreview,
  type ImportRequest,
} from '../api/imports'
import type { BankAccount, Category } from '../api/types'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { Icon } from '../components/ui/Icon'
import { PageHeader } from '../components/ui/PageHeader'
import { Segmented } from '../components/ui/Segmented'
import { currentMonth, formatDate, formatDateTime, formatSigned } from '../format'
import { ROUTES, transactionsPeriodHref } from '../router'
import { byName } from '../sort'

/** The value of the category field for a transfer between own accounts (R27). */
const TRANSFER = 'transfer'
/** The value of the account field for a new account. */
const NEW_ACCOUNT = 'new'
/** The value of the category field that opens the field for a new category. */
const NEW_CATEGORY = 'new-category'

/**
 * What the user chose for one row: import it or not, its category ('' while open, TRANSFER for a transfer) and whether
 * it replaces the planned occurrence the preview found (R28).
 */
interface Choice {
  selected: boolean
  category: string
  replace: boolean
}

type View = 'all' | 'open' | 'skipped'

function importable(entry: ImportEntry): boolean {
  return entry.status === 'NEW' || entry.status === 'POSSIBLE_DUPLICATE'
}

/**
 * R30, US33: a certain suggestion or a transfer is preset; an uncertain one is offered next to the field, and the
 * user chooses the category. A possible duplicate is not imported unless the user ticks it.
 */
function initialChoices(preview: ImportPreview): Record<string, Choice> {
  const choices: Record<string, Choice> = {}
  for (const entry of preview.entries) {
    if (!importable(entry) || entry.fingerprint === null) {
      continue
    }
    const category = entry.transfer ? TRANSFER : entry.certain && entry.categoryId !== null ? String(entry.categoryId) : ''
    choices[entry.fingerprint] = { selected: entry.status === 'NEW', category, replace: entry.replaces !== null }
  }
  return choices
}

function initialAccounts(preview: ImportPreview, accounts: BankAccount[]): Record<string, string> {
  const result: Record<string, string> = {}
  for (const source of preview.sources) {
    const known = accounts.some((account) => account.id === source.accountId)
    result[source.key] = known ? String(source.accountId) : NEW_ACCOUNT
  }
  return result
}

/** One row of the preview: tick, date, description, amount, category and the hints of R27, R29 and R30. */
function PreviewRow({
  entry,
  choice,
  categories,
  onChange,
}: {
  entry: ImportEntry
  choice: Choice
  categories: Category[]
  onChange: (choice: Choice) => void
}) {
  const description = entry.description ?? ''
  const suggested = categories.find((category) => category.id === entry.categoryId)
  const offerSuggestion = !entry.transfer && !entry.certain && suggested && choice.category === ''
  const open = choice.selected && choice.category === ''
  const createCategory = useCreateCategory()
  const [newName, setNewName] = useState<string | null>(null)
  const [newError, setNewError] = useState<string | null>(null)

  // A category that is missing is created right here and chosen for the row (after the first test, 07.11.2026)
  async function handleCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = (newName ?? '').trim()
    if (name === '') {
      setNewError('Der Name ist Pflicht.')
      return
    }
    setNewError(null)
    try {
      const created = await createCategory.mutateAsync({ name })
      setNewName(null)
      onChange({ ...choice, category: String(created.id) })
    } catch (e) {
      setNewError(errorText(e, 'Anlegen fehlgeschlagen.'))
    }
  }

  return (
    <li className={`import-row${choice.selected ? '' : ' unselected'}${open ? ' open' : ''}`} aria-label={description}>
      <input
        type="checkbox"
        className="import-check"
        aria-label={`${description} importieren`}
        checked={choice.selected}
        onChange={(e) => onChange({ ...choice, selected: e.target.checked })}
      />
      <span className="import-date">{entry.date && formatDate(entry.date)}</span>
      <span className="import-main">
        <span className="import-description">{description}</span>
        <span className="import-hints">
          {entry.bankCategory && <span className="hint">{entry.bankCategory}</span>}
          {entry.transfer && (
            <span className="badge">
              {entry.transferCertain ? 'Umbuchung auf ein eigenes Konto' : 'Umbuchung erkannt'}
            </span>
          )}
          {entry.transferPeerId !== null && choice.category === TRANSFER && (
            <span className="badge">Gegenbuchung wird ebenfalls zur Umbuchung</span>
          )}
          {entry.replaces && choice.category !== TRANSFER && (
            <label className="badge replace">
              <input
                type="checkbox"
                checked={choice.replace}
                disabled={!choice.selected}
                onChange={(e) => onChange({ ...choice, replace: e.target.checked })}
              />
              ersetzt „{entry.replaces.description}“ vom {formatDate(entry.replaces.date)}
            </label>
          )}
          {entry.duplicateOf && (
            <span className="badge warning">
              Schon von Hand gebucht? „{entry.duplicateOf.description}“ vom {formatDate(entry.duplicateOf.date)}
            </span>
          )}
        </span>
      </span>
      <span className={entry.amount !== null && entry.amount > 0 ? 'import-amount positive' : 'import-amount'}>
        {entry.amount !== null && formatSigned(entry.amount)}
      </span>
      <span className="import-category">
        <select
          aria-label={`Kategorie für ${description}`}
          value={choice.category}
          disabled={!choice.selected}
          aria-invalid={open ? true : undefined}
          onChange={(e) => {
            if (e.target.value === NEW_CATEGORY) {
              setNewName('')
              setNewError(null)
            } else {
              onChange({ ...choice, category: e.target.value })
            }
          }}
        >
          <option value="">Kategorie wählen …</option>
          <option value={TRANSFER}>Umbuchung (zählt nicht)</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
          <option value={NEW_CATEGORY}>+ Neue Kategorie …</option>
        </select>
        {newName !== null && (
          <form className="import-new-category" noValidate onSubmit={(e) => void handleCreate(e)}>
            <input
              aria-label={`Neue Kategorie für ${description}`}
              placeholder="Name der Kategorie"
              // Opened on purpose from the selection
              autoFocus
              maxLength={50}
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onKeyDown={(e) => e.key === 'Escape' && setNewName(null)}
              aria-invalid={newError ? true : undefined}
            />
            <button type="submit" disabled={createCategory.isPending}>
              Anlegen
            </button>
            <button type="button" onClick={() => setNewName(null)}>
              Abbrechen
            </button>
            {newError && (
              <span className="error" role="alert">
                {newError}
              </span>
            )}
          </form>
        )}
        {offerSuggestion && choice.selected && (
          <button
            type="button"
            className="chip"
            onClick={() => onChange({ ...choice, category: String(suggested.id) })}
          >
            <CategoryIcon icon={suggested.icon} size={14} />
            Vorschlag: {suggested.name}
          </button>
        )}
      </span>
    </li>
  )
}

/** The preview of a file: the accounts of its sources and its rows, until the user takes them over (US33). */
function Preview({
  preview,
  accounts,
  categories,
  onDone,
  onCancel,
}: {
  preview: ImportPreview
  accounts: BankAccount[]
  categories: Category[]
  onDone: (message: string, batch: ImportBatch | null) => void
  onCancel: () => void
}) {
  const commit = useCommitImport()
  const [choices, setChoices] = useState(() => initialChoices(preview))
  const [sourceAccounts, setSourceAccounts] = useState(() => initialAccounts(preview, accounts))
  const [view, setView] = useState<View>('all')
  const [error, setError] = useState<string | null>(null)

  const rows = preview.entries.filter(importable)
  const notImportable = preview.entries.filter((entry) => !importable(entry))
  const selected = rows.filter((entry) => entry.fingerprint !== null && choices[entry.fingerprint].selected)
  const open = selected.filter((entry) => choices[entry.fingerprint as string].category === '')
  const activeAccounts = accounts.filter((account) => !account.archived)
  const shown = view === 'open' ? open : rows

  // US33: a category chosen for a row also goes to the open rows with the same key (R29)
  function change(entry: ImportEntry, next: Choice) {
    setChoices((current) => {
      const updated = { ...current, [entry.fingerprint as string]: next }
      const chose = next.category !== '' && next.category !== current[entry.fingerprint as string].category
      if (chose && entry.suggestionKey) {
        for (const other of rows) {
          const otherChoice = updated[other.fingerprint as string]
          if (other !== entry && other.suggestionKey === entry.suggestionKey && otherChoice.category === '' && !other.transfer) {
            updated[other.fingerprint as string] = { ...otherChoice, category: next.category }
          }
        }
      }
      return updated
    })
  }

  async function handleCommit() {
    setError(null)
    const request: ImportRequest = {
      profile: preview.profile,
      sources: preview.sources.map((source) => {
        const chosen = sourceAccounts[source.key]
        return chosen === NEW_ACCOUNT
          ? { key: source.key, newAccountName: source.suggestedName }
          : { key: source.key, accountId: Number(chosen) }
      }),
      rows: selected.map((entry) => {
        const choice = choices[entry.fingerprint as string]
        const category = choice.category
        const transfer = category === TRANSFER
        return {
          fingerprint: entry.fingerprint as string,
          source: entry.source as string,
          date: entry.date as string,
          amount: entry.amount as number,
          description: entry.description as string,
          counterparty: entry.counterparty,
          bankCategory: entry.bankCategory,
          categoryId: transfer ? null : Number(category),
          transfer,
          ...(transfer && entry.transferPeerId !== null ? { transferPeerId: entry.transferPeerId } : {}),
          ...(!transfer && choice.replace && entry.replaces
            ? { replaces: { recurringId: entry.replaces.recurringId, date: entry.replaces.date } }
            : {}),
        }
      }),
    }
    try {
      const result = await commit.mutateAsync(request)
      const message =
        result.created === 0
          ? 'Nichts Neues: alle gewählten Zeilen waren schon importiert.'
          : `${result.created === 1 ? '1 Buchung' : `${result.created} Buchungen`} übernommen.`
      onDone(message, result.batch)
    } catch (e) {
      setError(errorText(e, 'Übernehmen fehlgeschlagen.'))
    }
  }

  return (
    <>
      <section className="card import-summary" aria-label="Datei">
        <h2>{preview.profileLabel}</h2>
        <p>
          {preview.rows === 1 ? '1 Zeile' : `${preview.rows} Zeilen`}: {preview.newRows} neu
          {preview.duplicates > 0 && `, ${preview.duplicates} schon importiert`}
          {preview.skipped > 0 && `, ${preview.skipped} nicht importierbar`}
        </p>
        {preview.sources.length > 0 && (
          <div className="import-sources">
            <h3>Konten</h3>
            {preview.sources.map((source) => (
              <label key={source.key} className="field">
                {source.name} in der Datei
                <select
                  value={sourceAccounts[source.key]}
                  onChange={(e) => setSourceAccounts({ ...sourceAccounts, [source.key]: e.target.value })}
                >
                  <option value={NEW_ACCOUNT}>Neues Konto anlegen: {source.suggestedName}</option>
                  {activeAccounts.map((account) => (
                    <option key={account.id} value={account.id}>
                      {account.name}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </div>
        )}
      </section>

      {rows.length > 0 && (
        <Segmented
          label="Zeilen"
          className="import-view"
          value={view}
          onChange={setView}
          options={[
            { value: 'all', label: `Neu (${rows.length})` },
            { value: 'open', label: `Ohne Kategorie (${open.length})` },
            { value: 'skipped', label: `Nicht importiert (${notImportable.length})` },
          ]}
        />
      )}

      {view !== 'skipped' && (
        <ul className="import-list" aria-label="Neue Zeilen">
          {shown.map((entry) => (
            <PreviewRow
              key={entry.fingerprint}
              entry={entry}
              choice={choices[entry.fingerprint as string]}
              categories={categories}
              onChange={(next) => change(entry, next)}
            />
          ))}
          {shown.length === 0 && <li className="hint">{view === 'open' ? 'Alle gewählten Zeilen haben eine Kategorie.' : 'Keine neuen Zeilen.'}</li>}
        </ul>
      )}
      {(view === 'skipped' || rows.length === 0) && notImportable.length > 0 && (
        <ul className="import-list skipped" aria-label="Nicht importierte Zeilen">
          {notImportable.map((entry) => (
            <li key={entry.line} className="import-row unselected">
              <span className="import-date">Zeile {entry.line}</span>
              <span className="import-main">
                <span className="import-description">{entry.description ?? '–'}</span>
                <span className="hint">{entry.reason}</span>
              </span>
              <span className="import-amount">{entry.amount !== null && formatSigned(entry.amount)}</span>
            </li>
          ))}
        </ul>
      )}

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="import-actions">
        <button type="button" onClick={onCancel}>
          Andere Datei
        </button>
        <button
          type="button"
          className="primary"
          disabled={selected.length === 0 || open.length > 0 || commit.isPending}
          onClick={() => void handleCommit()}
        >
          {selected.length === 1 ? '1 Buchung übernehmen' : `${selected.length} Buchungen übernehmen`}
        </button>
      </div>
      {open.length > 0 && (
        <p className="hint import-open">
          {open.length === 1 ? 'Einer Zeile fehlt' : `${open.length} Zeilen fehlt`} noch die Kategorie.
        </p>
      )}
    </>
  )
}

/** A stored import with "Rückgängig", which asks once more (US34). */
function BatchRow({ batch }: { batch: ImportBatch }) {
  const remove = useDeleteImport()
  const [confirming, setConfirming] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleDelete() {
    setError(null)
    try {
      await remove.mutateAsync(batch.id)
    } catch (e) {
      setError(errorText(e, 'Rückgängig machen fehlgeschlagen.'))
      setConfirming(false)
    }
  }

  return (
    <li className="import-batch" aria-label={`Import vom ${formatDateTime(batch.createdAt)}`}>
      <span className="import-batch-main">
        <span>
          {batch.profileLabel} · {batch.count === 1 ? '1 Buchung' : `${batch.count} Buchungen`}
        </span>
        <span className="hint">
          {formatDateTime(batch.createdAt)} · <a href={transactionsPeriodHref(batch.firstDate, batch.lastDate)}>
            {formatDate(batch.firstDate)} bis {formatDate(batch.lastDate)}
          </a>
        </span>
      </span>
      {confirming ? (
        <span className="dialog-actions confirm" role="group" aria-label="Rückgängig bestätigen">
          <span>Alle Buchungen dieses Imports löschen?</span>
          <span className="dialog-buttons">
            <button type="button" onClick={() => setConfirming(false)} disabled={remove.isPending}>
              Abbrechen
            </button>
            <button type="button" className="danger" onClick={() => void handleDelete()} disabled={remove.isPending}>
              Endgültig löschen
            </button>
          </span>
        </span>
      ) : (
        <button type="button" onClick={() => setConfirming(true)}>
          Rückgängig
        </button>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </li>
  )
}

/**
 * Transaktionen › Importieren (US33, US34): upload a CSV file of C24, Trade Republic or N26, check the preview and
 * take the rows over; earlier imports can be undone.
 */
export function ImportPage() {
  const accounts = useBankAccounts()
  const categories = useCategories(currentMonth())
  const imports = useImports()
  const upload = usePreviewImport()
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState<{ message: string; batch: ImportBatch | null } | null>(null)

  async function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) {
      return
    }
    setError(null)
    setDone(null)
    setPreview(null)
    try {
      setPreview(await upload.mutateAsync(file))
    } catch (e) {
      setError(errorText(e, 'Die Datei konnte nicht gelesen werden.'))
    }
  }

  // Alphabetical, like the booking dialog
  const activeCategories = byName((categories.data ?? []).filter((category) => !category.archived))
  const ready = accounts.data !== undefined && categories.data !== undefined

  return (
    <section>
      <PageHeader title="Importieren">
        <a className="button-link" href={ROUTES.transactions}>
          Zu den Transaktionen
        </a>
      </PageHeader>

      {!preview && (
        <section className="card import-upload" aria-label="Datei wählen">
          <p>
            CSV-Export deiner Bank hochladen. Unterstützt werden C24, Trade Republic und N26. Die Datei wird nur gelesen
            und nicht gespeichert; vorgemerkte Umsätze werden übersprungen.
          </p>
          <label className="button-link primary file-button">
            <Icon name="plus" size={18} />
            CSV-Datei wählen
            <input type="file" accept=".csv,text/csv" className="visually-hidden" onChange={(e) => void handleFile(e)} />
          </label>
          {upload.isPending && <p className="hint">Lese Datei …</p>}
          {error && (
            <p className="error" role="alert">
              {error}
            </p>
          )}
          {done && (
            <p className="success" role="status">
              {done.message}{' '}
              {done.batch && (
                <a href={transactionsPeriodHref(done.batch.firstDate, done.batch.lastDate)}>In den Transaktionen ansehen</a>
              )}
            </p>
          )}
        </section>
      )}

      {preview && ready && (
        <Preview
          key={preview.entries.map((entry) => entry.fingerprint ?? entry.line).join()}
          preview={preview}
          accounts={accounts.data}
          categories={activeCategories}
          onDone={(message, batch) => {
            setPreview(null)
            setDone({ message, batch })
          }}
          onCancel={() => setPreview(null)}
        />
      )}

      {imports.data && imports.data.length > 0 && (
        <section className="card settings-card" aria-labelledby="imports-title">
          <h2 id="imports-title">Bisherige Importe</h2>
          <ul className="import-batches">
            {imports.data.map((batch) => (
              <BatchRow key={batch.id} batch={batch} />
            ))}
          </ul>
        </section>
      )}
    </section>
  )
}
