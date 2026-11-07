import { useState, type ChangeEvent, type FormEvent } from 'react'
import { useBankAccounts } from '../api/bankAccounts'
import { useCategories, useCreateCategory } from '../api/categories'
import { useCreateRecurringTransaction } from '../api/recurring'
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
  type ImportSeries,
} from '../api/imports'
import type { BankAccount, Category } from '../api/types'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { Icon } from '../components/ui/Icon'
import { PageHeader } from '../components/ui/PageHeader'
import { Segmented } from '../components/ui/Segmented'
import { currentMonth, formatDate, formatDateTime, formatSigned } from '../format'
import { ROUTES, transactionsPeriodHref } from '../router'

/** The value of the category field for a transfer between own accounts (R27). */
const TRANSFER = 'transfer'
/** The value of the account field for a new account. */
const NEW_ACCOUNT = 'new'
/** The value of the category field that opens the field for a new category. */
const NEW_CATEGORY = 'new-category'

/**
 * What the user chose for one row: import it or not, its category ('' while open, TRANSFER for a transfer) and whether
 * it replaces the planned occurrence the preview found (R28); without an own choice it does (also after the preview was
 * loaded again, e.g. once a series was created).
 */
interface Choice {
  selected: boolean
  category: string
  replace?: boolean
}

/** A suggestion for a row as the value of its category field: a transfer (R27) or a category (R28, R29). */
interface RowSuggestion {
  value: string
  name: string
  icon: string | null
}

/**
 * The suggestion of a row: a recognized transfer (R27); else the category the user chose in this preview for a row with
 * the same key (R29), which only suggests and never sets (after the test, 07.11.2026); else the suggestion of the
 * backend. A transfer the user chose is never passed on, because the same counterparty (e.g. a payment service) can
 * stand for both.
 */
function suggestionOf(entry: ImportEntry, categories: Category[], chosenByKey: Record<string, string>): RowSuggestion | null {
  if (entry.transfer) {
    return { value: TRANSFER, name: 'Umbuchung', icon: null }
  }
  const chosen = entry.suggestionKey ? chosenByKey[entry.suggestionKey] : undefined
  const id = chosen !== undefined ? Number(chosen) : entry.categoryId
  const category = categories.find((c) => c.id === id)
  return category ? { value: String(category.id), name: category.name, icon: category.icon } : null
}

type View = 'all' | 'open' | 'skipped'

function importable(entry: ImportEntry): boolean {
  return entry.status === 'NEW' || entry.status === 'POSSIBLE_DUPLICATE'
}

/**
 * R30, US33, decision 46: no category is preset, not even a certain suggestion or a transfer; the suggestion is offered
 * next to the field. A possible duplicate is not imported unless the user ticks it.
 */
function initialChoices(preview: ImportPreview): Record<string, Choice> {
  const choices: Record<string, Choice> = {}
  for (const entry of preview.entries) {
    if (!importable(entry) || entry.fingerprint === null) {
      continue
    }
    choices[entry.fingerprint] = { selected: entry.status === 'NEW', category: '' }
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
  suggested,
  onChange,
}: {
  entry: ImportEntry
  choice: Choice
  categories: Category[]
  suggested: RowSuggestion | null
  onChange: (choice: Choice) => void
}) {
  const description = entry.description ?? ''
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
                checked={choice.replace ?? true}
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
        {suggested && open && (
          <button type="button" className="chip" onClick={() => onChange({ ...choice, category: suggested.value })}>
            {suggested.icon && <CategoryIcon icon={suggested.icon} size={14} />}
            Vorschlag: {suggested.name}
          </button>
        )}
      </span>
    </li>
  )
}

/**
 * R31: a card for rows that look like a monthly series. The category of the series is only a suggestion; once the
 * series exists, the page loads the preview again and the rows replace its occurrences (R28).
 */
function SeriesCard({
  series,
  categories,
  accountId,
  onCreated,
  onIgnore,
}: {
  series: ImportSeries
  categories: Category[]
  accountId: number | null
  onCreated: () => void
  onIgnore: () => void
}) {
  const create = useCreateRecurringTransaction()
  const [categoryId, setCategoryId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const suggested = categories.find((c) => c.id === series.categoryId)

  async function handleCreate() {
    setError(null)
    try {
      await create.mutateAsync({
        amount: series.amount,
        description: series.description,
        categoryId: Number(categoryId),
        interval: 'MONTHLY',
        startDate: series.startDate,
        endDate: null,
        ...(accountId === null ? {} : { accountId }),
      })
      onCreated()
    } catch (e) {
      setError(errorText(e, 'Anlegen fehlgeschlagen.'))
    }
  }

  const label = `Serie „${series.description}“`
  return (
    <section className="card import-series" aria-label={label}>
      <p>
        Sieht nach einer monatlichen Serie aus: „{series.description}“, {formatSigned(series.amount)} ab{' '}
        {formatDate(series.startDate)} ({series.lines.length} Buchungen in der Datei).
      </p>
      <div className="import-series-actions">
        <select aria-label={`Kategorie für ${label}`} value={categoryId} onChange={(e) => setCategoryId(e.target.value)}>
          <option value="">Kategorie wählen …</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
        {suggested && categoryId === '' && (
          <button type="button" className="chip" onClick={() => setCategoryId(String(suggested.id))}>
            <CategoryIcon icon={suggested.icon} size={14} />
            Vorschlag: {suggested.name}
          </button>
        )}
        <button type="button" className="primary" disabled={categoryId === '' || create.isPending} onClick={() => void handleCreate()}>
          Als Serie anlegen
        </button>
        <button type="button" onClick={onIgnore}>
          Ignorieren
        </button>
      </div>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
    </section>
  )
}

/** The preview of a file: the accounts of its sources and its rows, until the user takes them over (US33). */
function Preview({
  preview,
  accounts,
  categories,
  onDone,
  onPartlyDone,
  onSeriesCreated,
  onCancel,
}: {
  preview: ImportPreview
  accounts: BankAccount[]
  categories: Category[]
  onDone: (message: string, batch: ImportBatch | null) => void
  /** "Fertige übernehmen": the rows with these fingerprints are stored, the rest stays in the preview. */
  onPartlyDone: (fingerprints: string[]) => void
  onSeriesCreated: () => void
  onCancel: () => void
}) {
  const commit = useCommitImport()
  const [choices, setChoices] = useState(() => initialChoices(preview))
  const [sourceAccounts, setSourceAccounts] = useState(() => initialAccounts(preview, accounts))
  const [view, setView] = useState<View>('all')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [ignoredSeries, setIgnoredSeries] = useState<string[]>([])
  // The last category the user chose per key; it is a suggestion for the other rows with that key
  const [chosenByKey, setChosenByKey] = useState<Record<string, string>>({})

  const rows = preview.entries.filter(importable)
  const notImportable = preview.entries.filter((entry) => !importable(entry))
  const selected = rows.filter((entry) => entry.fingerprint !== null && choices[entry.fingerprint].selected)
  const open = selected.filter((entry) => choices[entry.fingerprint as string].category === '')
  const finished = selected.filter((entry) => choices[entry.fingerprint as string].category !== '')
  const suggestionFor = (entry: ImportEntry) => suggestionOf(entry, categories, chosenByKey)
  const withSuggestion = open.filter((entry) => suggestionFor(entry) !== null)
  const activeAccounts = accounts.filter((account) => !account.archived)
  const shown = view === 'open' ? open : rows
  const series = preview.series.filter((s) => !ignoredSeries.includes(s.lines.join()))

  // US33: a category chosen for a row becomes the suggestion of the open rows with the same key (R29); a transfer does not
  function change(entry: ImportEntry, next: Choice) {
    setChoices((current) => ({ ...current, [entry.fingerprint as string]: next }))
    const chose = next.category !== '' && next.category !== choices[entry.fingerprint as string].category
    if (chose && next.category !== TRANSFER && entry.suggestionKey) {
      setChosenByKey((current) => ({ ...current, [entry.suggestionKey as string]: next.category }))
    }
  }

  // Decision 46: one click takes over the shown suggestion of every open row
  function acceptSuggestions() {
    setChoices((current) => {
      const updated = { ...current }
      for (const entry of withSuggestion) {
        const suggestion = suggestionFor(entry)
        if (suggestion) {
          updated[entry.fingerprint as string] = { ...updated[entry.fingerprint as string], category: suggestion.value }
        }
      }
      return updated
    })
  }

  function request(entries: ImportEntry[]): ImportRequest {
    return {
      profile: preview.profile,
      sources: preview.sources.map((source) => {
        const chosen = sourceAccounts[source.key]
        return chosen === NEW_ACCOUNT
          ? { key: source.key, newAccountName: source.suggestedName }
          : { key: source.key, accountId: Number(chosen) }
      }),
      rows: entries.map((entry) => {
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
          ...(!transfer && (choice.replace ?? true) && entry.replaces
            ? { replaces: { recurringId: entry.replaces.recurringId, date: entry.replaces.date } }
            : {}),
        }
      }),
    }
  }

  function bookings(count: number): string {
    return count === 1 ? '1 Buchung' : `${count} Buchungen`
  }

  async function handleCommit() {
    setError(null)
    try {
      const result = await commit.mutateAsync(request(selected))
      const message =
        result.created === 0
          ? 'Nichts Neues: alle gewählten Zeilen waren schon importiert.'
          : `${bookings(result.created)} übernommen.`
      onDone(message, result.batch)
    } catch (e) {
      setError(errorText(e, 'Übernehmen fehlgeschlagen.'))
    }
  }

  // Decision 46: the rows with a category are stored now; the rest stays, and a later upload of the same file shows
  // only the rest thanks to the fingerprint (R30)
  async function handlePartialCommit() {
    setError(null)
    setNotice(null)
    try {
      const result = await commit.mutateAsync(request(finished))
      onPartlyDone(finished.map((entry) => entry.fingerprint as string))
      setNotice(`${bookings(result.created)} übernommen; die übrigen Zeilen bleiben hier.`)
    } catch (e) {
      setError(errorText(e, 'Übernehmen fehlgeschlagen.'))
    }
  }

  function accountOf(source: string): number | null {
    const chosen = sourceAccounts[source]
    return chosen === undefined || chosen === NEW_ACCOUNT ? null : Number(chosen)
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

      {series.map((s) => (
        <SeriesCard
          key={s.lines.join()}
          series={s}
          categories={categories}
          accountId={accountOf(s.source)}
          onCreated={onSeriesCreated}
          onIgnore={() => setIgnoredSeries([...ignoredSeries, s.lines.join()])}
        />
      ))}

      {notice && (
        <p className="success" role="status">
          {notice}
        </p>
      )}

      {rows.length > 0 && (
        <div className="import-toolbar">
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
          <button type="button" disabled={withSuggestion.length === 0} onClick={acceptSuggestions}>
            Alle Vorschläge übernehmen ({withSuggestion.length})
          </button>
        </div>
      )}

      {view !== 'skipped' && (
        <ul className="import-list" aria-label="Neue Zeilen">
          {shown.map((entry) => (
            <PreviewRow
              key={entry.fingerprint}
              entry={entry}
              choice={choices[entry.fingerprint as string]}
              categories={categories}
              suggested={suggestionFor(entry)}
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
        {open.length > 0 && (
          <button type="button" disabled={finished.length === 0 || commit.isPending} onClick={() => void handlePartialCommit()}>
            Fertige übernehmen ({finished.length})
          </button>
        )}
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
 * Transaktionen › Importieren (US33, US34): upload a CSV file of C24, Trade Republic, N26 or the Sparkasse, check the preview and
 * take the rows over; earlier imports can be undone.
 */
export function ImportPage() {
  const accounts = useBankAccounts()
  const categories = useCategories(currentMonth())
  const imports = useImports()
  const upload = usePreviewImport()
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  // The uploaded file, to read it again once a series was created (R31)
  const [file, setFile] = useState<File | null>(null)
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
    setFile(file)
    try {
      setPreview(await upload.mutateAsync(file))
    } catch (e) {
      setError(errorText(e, 'Die Datei konnte nicht gelesen werden.'))
    }
  }

  async function reload() {
    if (!file) {
      return
    }
    try {
      setPreview(await upload.mutateAsync(file))
    } catch (e) {
      setError(errorText(e, 'Die Datei konnte nicht gelesen werden.'))
    }
  }

  // "Fertige übernehmen": the stored rows now count as imported (R30)
  function markImported(fingerprints: string[]) {
    setPreview((current) => {
      if (!current) {
        return current
      }
      const stored = new Set(fingerprints)
      return {
        ...current,
        newRows: current.newRows - fingerprints.length,
        duplicates: current.duplicates + fingerprints.length,
        entries: current.entries.map((entry) =>
          entry.fingerprint !== null && stored.has(entry.fingerprint)
            ? { ...entry, status: 'DUPLICATE' as const, reason: 'eben übernommen' }
            : entry,
        ),
      }
    })
  }

  // Already alphabetical from the backend (decision 38)
  const activeCategories = (categories.data ?? []).filter((category) => !category.archived)
  const ready = accounts.data !== undefined && categories.data !== undefined

  return (
    <section>
      <PageHeader title="Importieren">
        {preview && (
          <button type="button" onClick={() => setPreview(null)}>
            Abbrechen
          </button>
        )}
        <a className="button-link" href={ROUTES.transactions}>
          Zu den Transaktionen
        </a>
      </PageHeader>

      {!preview && (
        <section className="card import-upload" aria-label="Datei wählen">
          <p>
            CSV-Export deiner Bank hochladen. Unterstützt werden C24, Trade Republic, N26 und die Sparkasse (alle vier
            CSV-Exporte). Die Datei wird nur gelesen
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
          onPartlyDone={markImported}
          onSeriesCreated={() => void reload()}
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
