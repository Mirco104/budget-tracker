import { useEffect, useId, useState, type ChangeEvent } from 'react'
import { useBankAccounts, useCreateBankAccount } from '../api/bankAccounts'
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
  type ImportSeries,
  type ImportSource,
} from '../api/imports'
import type { BankAccount, Category } from '../api/types'
import { TransactionFilters } from '../components/TransactionFilters'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { Icon } from '../components/ui/Icon'
import { InlineCreate } from '../components/ui/InlineCreate'
import { PageHeader } from '../components/ui/PageHeader'
import { Segmented } from '../components/ui/Segmented'
import { facetCounts, IMPORT_FILTERS, IMPORT_KINDS, type Filters } from '../filters'
import { currentMonth, formatDate, formatDateTime, formatSigned } from '../format'
import { ROUTES, transactionsPeriodHref } from '../router'
import { Loading } from '../components/ui/Loading'

/** The value of the category field for a transfer between own accounts (R27). */
const TRANSFER = 'transfer'
/** The value of the account field for a new account with the suggested name, created when the rows are taken over. */
const NEW_ACCOUNT = 'new'
/** The value of the account field that opens the field for a new account with an own name (08.11.2026). */
const NEW_NAMED_ACCOUNT = 'new-named'
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

/**
 * R27: the counterpart of an uncertain transfer like a booking, so the user can judge it without line numbers (code
 * review, 08.11.2026): „Auf Pocket“, +100,00 € vom 04.11.2026 (Pocket Urlaub, in dieser Datei).
 */
function peerText(peer: NonNullable<ImportEntry['transferPeer']>): string {
  const amount = peer.amount !== null ? `, ${formatSigned(peer.amount)}` : ''
  const date = peer.date ? ` vom ${formatDate(peer.date)}` : ''
  const where = peer.line !== null ? `${peer.accountName ?? ''}, in dieser Datei` : (peer.accountName ?? '')
  return `„${peer.description ?? ''}“${amount}${date} (${where})`
}

type View = 'all' | 'skipped'

/** The value of the category filter for the chosen rows without a category (ticket #84). */
const WITHOUT_CATEGORY = 'open'

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
  highlighted,
  outsideFilter,
  onJumpToPeer,
  onChange,
}: {
  entry: ImportEntry
  choice: Choice
  categories: Category[]
  suggested: RowSuggestion | null
  /** Shortly true after a jump from the hint of its counterpart. */
  highlighted: boolean
  /** Shown although it does not pass the filters: edited in the list or jumped to (ticket #84). */
  outsideFilter: boolean
  onJumpToPeer: (line: number) => void
  onChange: (choice: Choice) => void
}) {
  const description = entry.description ?? ''
  const open = choice.selected && choice.category === ''
  const createCategory = useCreateCategory()
  const [creating, setCreating] = useState(false)

  // A category that is missing is created right here and chosen for the row (after the first test, 07.11.2026)
  async function handleCreate(name: string) {
    const created = await createCategory.mutateAsync({ name })
    setCreating(false)
    onChange({ ...choice, category: String(created.id) })
  }

  return (
    <li
      id={`import-line-${entry.line}`}
      className={`import-row${choice.selected ? '' : ' unselected'}${open ? ' open' : ''}${highlighted ? ' highlight' : ''}`}
      aria-label={description}
    >
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
          {outsideFilter && <span className="badge">passt nicht zum Filter</span>}
          {entry.bankCategory && <span className="hint">{entry.bankCategory}</span>}
          {entry.transfer && (
            <span className="badge">
              {entry.transferCertain ? 'Umbuchung auf ein eigenes Konto' : 'Umbuchung erkannt'}
            </span>
          )}
          {entry.transfer && !entry.transferCertain && entry.transferPeer && entry.transferPeer.line !== null && (
            <button
              type="button"
              className="link-button import-peer"
              onClick={() => onJumpToPeer(entry.transferPeer?.line as number)}
            >
              Gegenbuchung: {peerText(entry.transferPeer)}
            </button>
          )}
          {entry.transfer && !entry.transferCertain && entry.transferPeer && entry.transferPeer.line === null && (
            <span className="hint">Gegenbuchung: {peerText(entry.transferPeer)}</span>
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
              setCreating(true)
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
        {creating && (
          <InlineCreate
            label={`Neue Kategorie für ${description}`}
            placeholder="Name der Kategorie"
            onCreate={handleCreate}
            onCancel={() => setCreating(false)}
          />
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

/** A card shows its rows in the list by setting the kind filter to Wiederkehrend (ticket #84, after the test). */
function ShowInList({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className="link-button" onClick={onClick}>
      In der Liste zeigen
    </button>
  )
}

/**
 * R31: a card for rows that look like a monthly series. The category of the series is only a suggestion. "Als Serie
 * anlegen" only notes the series; it is created with the import, and its rows replace its occurrences (code review,
 * 08.11.2026), so "Abbrechen" leaves nothing behind.
 */
function SeriesCard({
  series,
  categories,
  planned,
  onShowInList,
  onPlan,
  onUnplan,
  onIgnore,
}: {
  series: ImportSeries
  categories: Category[]
  /** The category of the noted series, null while it is not noted. */
  planned: string | null
  onShowInList: () => void
  onPlan: (categoryId: string) => void
  onUnplan: () => void
  onIgnore: () => void
}) {
  const [categoryId, setCategoryId] = useState('')
  const createCategory = useCreateCategory()
  const [creating, setCreating] = useState(false)
  const suggested = categories.find((c) => c.id === series.categoryId)

  async function handleCreate(name: string) {
    const created = await createCategory.mutateAsync({ name })
    setCreating(false)
    setCategoryId(String(created.id))
  }
  const plannedName = categories.find((c) => String(c.id) === planned)?.name

  const label = `Serie „${series.description}“`
  return (
    <section className="card import-series" aria-label={label}>
      <p>
        Sieht nach einer monatlichen Serie aus: „{series.description}“, {formatSigned(series.amount)} ab{' '}
        {formatDate(series.startDate)} ({series.lines.length} Buchungen in der Datei).
      </p>
      {planned !== null ? (
        <div className="import-series-actions">
          <span className="hint">Wird beim Übernehmen als monatliche Serie angelegt ({plannedName}).</span>
          <button type="button" onClick={onUnplan}>
            Nicht anlegen
          </button>
        </div>
      ) : (
        <div className="import-series-actions">
          <select
            aria-label={`Kategorie für ${label}`}
            value={categoryId}
            onChange={(e) => (e.target.value === NEW_CATEGORY ? setCreating(true) : setCategoryId(e.target.value))}
          >
            <option value="">Kategorie wählen …</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
            <option value={NEW_CATEGORY}>+ Neue Kategorie …</option>
          </select>
          {creating && (
            <InlineCreate
              label={`Neue Kategorie für ${label}`}
              placeholder="Name der Kategorie"
              onCreate={handleCreate}
              onCancel={() => setCreating(false)}
            />
          )}
          {suggested && categoryId === '' && (
            <button type="button" className="chip" onClick={() => setCategoryId(String(suggested.id))}>
              <CategoryIcon icon={suggested.icon} size={14} />
              Vorschlag: {suggested.name}
            </button>
          )}
          <button type="button" className="primary" disabled={categoryId === ''} onClick={() => onPlan(categoryId)}>
            Als Serie anlegen
          </button>
          <button type="button" onClick={onIgnore}>
            Ignorieren
          </button>
        </div>
      )}
      {planned !== null && <ShowInList onClick={onShowInList} />}
    </section>
  )
}

/** R28: the chosen rows that replace occurrences of one existing series. */
interface Match {
  recurringId: number
  description: string
  categoryId: number
  entries: ImportEntry[]
}

/** R28: the chosen rows grouped by the series whose occurrence they replace; only series with at least 2 rows. */
function matchesOf(entries: ImportEntry[]): Match[] {
  const byId = new Map<number, Match>()
  for (const entry of entries) {
    if (!entry.replaces) {
      continue
    }
    const { recurringId, description, categoryId } = entry.replaces
    const match = byId.get(recurringId) ?? { recurringId, description, categoryId, entries: [] }
    match.entries.push(entry)
    byId.set(recurringId, match)
  }
  return [...byId.values()].filter((match) => match.entries.length >= 2)
}

/**
 * R28, US33: a card for an existing series with at least 2 matching rows; one click gives them the category of the
 * series and lets them replace their occurrence (08.11.2026). Nothing is preset (decision 46), the user still decides
 * once per series.
 */
function MatchCard({
  match,
  categories,
  open,
  accepted,
  onAccept,
  onUndo,
  onShowInList,
}: {
  match: Match
  categories: Category[]
  /** The rows of the match without the category of the series or without "ersetzt". */
  open: number
  /** The card took the rows over with "Alle übernehmen", so "Zurücknehmen" is offered. */
  accepted: boolean
  onAccept: () => void
  onUndo: () => void
  onShowInList: () => void
}) {
  const category = categories.find((c) => c.id === match.categoryId)
  const dates = match.entries.map((entry) => (entry.replaces as NonNullable<ImportEntry['replaces']>).date).sort()
  const count = match.entries.length
  return (
    <section className="card import-series" aria-label={`Treffer der Serie „${match.description}“`}>
      <p>
        {count} Buchungen ersetzen die geplante Serie „{match.description}“{category && ` (${category.name})`} vom{' '}
        {formatDate(dates[0])} bis {formatDate(dates[dates.length - 1])}.
      </p>
      <div className="import-series-actions">
        {!category ? (
          <span className="hint">Die Kategorie der Serie ist archiviert; bitte wähle je Zeile eine.</span>
        ) : open === 0 ? (
          <span className="hint">
            Alle {count} ersetzen ihr Vorkommen ({category.name}).
          </span>
        ) : (
          <button type="button" className="primary" onClick={onAccept}>
            Alle übernehmen ({open})
          </button>
        )}
        {accepted && (
          <button type="button" onClick={onUndo}>
            Zurücknehmen
          </button>
        )}
      </div>
      <ShowInList onClick={onShowInList} />
    </section>
  )
}

/**
 * The account of a source of the file: an own account, a new one with the suggested name (created when the rows are
 * taken over) or, since 08.11.2026, a new one with an own name, which is created right away and then chosen.
 */
function SourceAccount({
  source,
  accounts,
  value,
  onChange,
}: {
  source: ImportSource
  accounts: BankAccount[]
  value: string
  onChange: (value: string) => void
}) {
  const labelId = useId()
  const createAccount = useCreateBankAccount()
  const [creating, setCreating] = useState(false)

  async function handleCreate(name: string) {
    const account = await createAccount.mutateAsync({ name })
    setCreating(false)
    onChange(String(account.id))
  }

  return (
    <div className="field">
      <span id={labelId}>„{source.name}“ in der Datei</span>
      <select
        aria-labelledby={labelId}
        value={value}
        onChange={(e) => (e.target.value === NEW_NAMED_ACCOUNT ? setCreating(true) : onChange(e.target.value))}
      >
        <option value={NEW_ACCOUNT}>+ Neues Konto anlegen: {source.suggestedName}</option>
        {accounts.map((account) => (
          <option key={account.id} value={account.id}>
            {account.name}
          </option>
        ))}
        <option value={NEW_NAMED_ACCOUNT}>+ Neues Konto mit eigenem Namen …</option>
      </select>
      {creating && (
        <InlineCreate
          label={`Name des neuen Kontos für ${source.name}`}
          placeholder="Name des Kontos"
          initial={source.suggestedName}
          onCreate={handleCreate}
          onCancel={() => setCreating(false)}
        />
      )}
    </div>
  )
}

/** The preview of a file: the accounts of its sources and its rows, until the user takes them over (US33). */
function Preview({
  preview,
  accounts,
  categories,
  onDone,
  onPartlyDone,
  onReload,
  onCancel,
}: {
  preview: ImportPreview
  accounts: BankAccount[]
  categories: Category[]
  onDone: (message: string, batch: ImportBatch | null) => void
  /** "Fertige übernehmen": the rows with these fingerprints are stored, the rest stays in the preview. */
  onPartlyDone: (fingerprints: string[]) => void
  /** A part was taken over with a new series: the file is read again, so the rest meets the series (R28). */
  onReload: () => void
  onCancel: () => void
}) {
  const commit = useCommitImport()
  const [choices, setChoices] = useState(() => initialChoices(preview))
  const [sourceAccounts, setSourceAccounts] = useState(() => initialAccounts(preview, accounts))
  const [view, setView] = useState<View>('all')
  // Ticket #84: the same filters as Transaktionen, by what the user chose for each row
  // starts with Einmalig; a row edited in the list or jumped to stays shown until the filters change
  const [filters, setFilters] = useState<Filters>(IMPORT_FILTERS)
  const [pinned, setPinned] = useState<string[]>([])
  const [assignCategory, setAssignCategory] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [ignoredSeries, setIgnoredSeries] = useState<string[]>([])
  // R31: the noted series by their lines, with their category
  const [plannedSeries, setPlannedSeries] = useState<Record<string, string>>({})
  // R28: the series whose matches the user took over with the card
  const [acceptedMatches, setAcceptedMatches] = useState<number[]>([])
  // The row a hint jumped to, highlighted for a moment
  const [highlighted, setHighlighted] = useState<number | null>(null)
  // The last category the user chose per key; it is a suggestion for the other rows with that key
  const [chosenByKey, setChosenByKey] = useState<Record<string, string>>({})

  const rows = preview.entries.filter(importable)
  const notImportable = preview.entries.filter((entry) => !importable(entry))
  const selected = rows.filter((entry) => entry.fingerprint !== null && choices[entry.fingerprint].selected)
  const open = selected.filter((entry) => choices[entry.fingerprint as string].category === '')
  const finished = selected.filter((entry) => choices[entry.fingerprint as string].category !== '')
  const suggestionFor = (entry: ImportEntry) => suggestionOf(entry, categories, chosenByKey)
  // An uncertain transfer (R27) is only set by its own button, never by the bulk button (code review, 08.11.2026)
  const withSuggestion = open.filter(
    (entry) => suggestionFor(entry) !== null && !(entry.transfer && !entry.transferCertain),
  )
  const activeAccounts = accounts.filter((account) => !account.archived)
  const series = preview.series.filter((s) => !ignoredSeries.includes(s.lines.join()))
  const seriesOfEntry = (entry: ImportEntry) =>
    series.find((s) => plannedSeries[s.lines.join()] !== undefined && s.lines.includes(entry.line)) ?? null
  const matches = matchesOf(selected)
  // A row of a match is done with the category of its series and "ersetzt" ticked
  const matchOpen = (entry: ImportEntry) => {
    const choice = choices[entry.fingerprint as string]
    return choice.category !== String(entry.replaces?.categoryId) || choice.replace === false
  }

  // Wiederkehrend for a row that replaces an occurrence (R28) or belongs to a noted series (R31); an Umbuchung is a
  // category option (ticket #84), as a row is a transfer by its category
  function kindOfRow(entry: ImportEntry): string {
    const choice = choices[entry.fingerprint as string]
    const replaces = entry.replaces !== null && choice.replace !== false && choice.category !== TRANSFER
    return replaces || seriesOfEntry(entry) !== null ? 'RECURRING' : 'SINGLE'
  }

  // The value of a row for the category filter: "Ohne Kategorie" for a chosen row without one, else its category
  function categoryKey(entry: ImportEntry): string {
    const choice = choices[entry.fingerprint as string]
    return choice.selected && choice.category === '' ? WITHOUT_CATEGORY : choice.category
  }

  function matchesFilter(entry: ImportEntry, current: Filters): boolean {
    const search = current.query.trim().toLocaleLowerCase('de-DE')
    return (
      (current.category === '' || categoryKey(entry) === current.category) &&
      (current.account === '' || entry.source === current.account) &&
      (current.kind === '' || kindOfRow(entry) === current.kind) &&
      (search === '' || (entry.description ?? '').toLocaleLowerCase('de-DE').includes(search))
    )
  }
  const filterActive =
    filters.query.trim() !== '' || filters.category !== '' || filters.account !== '' || filters.kind !== ''
  const filtersChanged =
    filters.query !== IMPORT_FILTERS.query ||
    filters.category !== IMPORT_FILTERS.category ||
    filters.account !== IMPORT_FILTERS.account ||
    filters.kind !== IMPORT_FILTERS.kind
  const outsideFilter = (entry: ImportEntry) => !matchesFilter(entry, filters)
  const listed = rows.filter((entry) => !outsideFilter(entry) || pinned.includes(entry.fingerprint as string))
  // The chosen rows the filters hide; they are taken over all the same
  const hiddenSelected = selected.filter((entry) => !listed.includes(entry)).length
  // Ticket #84: the hits of each filter option with the other filters
  const counts = facetCounts(rows, filters, matchesFilter, {
    category: categoryKey,
    account: (entry) => entry.source ?? '',
    kind: kindOfRow,
  })
  // The shown chosen rows without category, for "Zuordnen"
  const assignable = filterActive
    ? listed.filter((entry) => {
        const choice = choices[entry.fingerprint as string]
        return choice.selected && choice.category === ''
      })
    : []

  // A change of the filters lets go of the pinned rows
  function changeFilters(next: Filters) {
    setFilters(next)
    setPinned([])
  }

  function pin(entry: ImportEntry) {
    const fingerprint = entry.fingerprint as string
    setPinned((current) => (current.includes(fingerprint) ? current : [...current, fingerprint]))
  }

  // R27: the hint of a transfer jumps to its counterpart in the same file; the filters stay, a hidden counterpart is
  // pinned (ticket #84, after the test)
  function jumpToPeer(line: number) {
    setView('all')
    const peer = rows.find((entry) => entry.line === line)
    if (peer && outsideFilter(peer)) {
      pin(peer)
    }
    setHighlighted(line)
  }

  const showInList = () => changeFilters({ ...filters, kind: 'RECURRING' })

  useEffect(() => {
    if (highlighted === null) {
      return
    }
    // An instant jump; the highlight shows where it went (a smooth scroll needs animation frames and is not needed)
    document.getElementById(`import-line-${highlighted}`)?.scrollIntoView?.({ block: 'center' })
    const timer = window.setTimeout(() => setHighlighted(null), 2000)
    return () => window.clearTimeout(timer)
  }, [highlighted])

  // R31: noting a series gives its rows its category
  function planSeries(s: ImportSeries, categoryId: string) {
    setPlannedSeries((current) => ({ ...current, [s.lines.join()]: categoryId }))
    setChoices((current) => {
      const updated = { ...current }
      for (const entry of rows) {
        if (s.lines.includes(entry.line) && entry.fingerprint !== null) {
          updated[entry.fingerprint] = { ...updated[entry.fingerprint], category: categoryId }
        }
      }
      return updated
    })
  }

  function unplanSeries(s: ImportSeries) {
    setPlannedSeries((current) => {
      const updated = { ...current }
      delete updated[s.lines.join()]
      return updated
    })
  }

  // US33: a category chosen for a row becomes the suggestion of the open rows with the same key (R29); a transfer does not
  function change(entry: ImportEntry, next: Choice) {
    setChoices((current) => ({ ...current, [entry.fingerprint as string]: next }))
    // The row stays in sight while it is edited, even when it no longer passes the filters
    pin(entry)
    const chose = next.category !== '' && next.category !== choices[entry.fingerprint as string].category
    if (chose && next.category !== TRANSFER && entry.suggestionKey) {
      setChosenByKey((current) => ({ ...current, [entry.suggestionKey as string]: next.category }))
    }
  }

  // Ticket #84: the shown chosen rows without category get one category at once; a category (not a transfer) becomes
  // the suggestion of the rows with the same key (R29), as a choice in a row does
  function assignShown() {
    const targets = assignable
    setChoices((current) => {
      const updated = { ...current }
      for (const entry of targets) {
        updated[entry.fingerprint as string] = { ...updated[entry.fingerprint as string], category: assignCategory }
      }
      return updated
    })
    if (assignCategory !== TRANSFER) {
      setChosenByKey((current) => {
        const updated = { ...current }
        for (const entry of targets) {
          if (entry.suggestionKey) {
            updated[entry.suggestionKey] = assignCategory
          }
        }
        return updated
      })
    }
    const name =
      assignCategory === TRANSFER ? 'Umbuchung' : (categories.find((c) => String(c.id) === assignCategory)?.name ?? '')
    setNotice(`${targets.length === 1 ? '1 Zeile' : `${targets.length} Zeilen`} „${name}“ zugeordnet.`)
    setAssignCategory('')
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

  // R28: one click gives every chosen row of a match the category of its series and lets it replace its occurrence
  function acceptMatch(match: Match) {
    setChoices((current) => {
      const updated = { ...current }
      for (const entry of match.entries) {
        const fingerprint = entry.fingerprint as string
        updated[fingerprint] = { ...updated[fingerprint], category: String(match.categoryId), replace: true }
      }
      return updated
    })
    setAcceptedMatches((current) => [...current, match.recurringId])
  }

  // The rows the card took over come back open, as at the start
  function undoMatch(match: Match) {
    const back = match.entries.filter((entry) => !matchOpen(entry))
    setChoices((current) => {
      const updated = { ...current }
      for (const entry of back) {
        const fingerprint = entry.fingerprint as string
        updated[fingerprint] = { ...updated[fingerprint], category: '' }
      }
      return updated
    })
    setAcceptedMatches((current) => current.filter((id) => id !== match.recurringId))
  }

  function request(entries: ImportEntry[]): ImportRequest {
    // R31: a noted series goes along when at least one of its rows does (no transfer)
    const inSeries = (entry: ImportEntry, s: ImportSeries) =>
      s.lines.includes(entry.line) && choices[entry.fingerprint as string].category !== TRANSFER
    const used = series.filter(
      (s) => plannedSeries[s.lines.join()] !== undefined && entries.some((entry) => inSeries(entry, s)),
    )
    return {
      ...(used.length > 0
        ? {
            series: used.map((s) => ({
              startDate: s.startDate,
              amount: s.amount,
              description: s.description,
              categoryId: Number(plannedSeries[s.lines.join()]),
              source: s.source,
            })),
          }
        : {}),
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
        const seriesIndex = used.findIndex((s) => inSeries(entry, s))
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
          ...(seriesIndex >= 0 ? { series: seriesIndex } : {}),
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
      const body = request(finished)
      const result = await commit.mutateAsync(body)
      if (body.series) {
        onReload()
      } else {
        onPartlyDone(finished.map((entry) => entry.fingerprint as string))
      }
      setNotice(`${bookings(result.created)} übernommen; die übrigen Zeilen bleiben hier.`)
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
              <SourceAccount
                key={source.key}
                source={source}
                accounts={activeAccounts}
                value={sourceAccounts[source.key]}
                onChange={(value) => setSourceAccounts((current) => ({ ...current, [source.key]: value }))}
              />
            ))}
          </div>
        )}
      </section>

      {matches.map((match) => (
        <MatchCard
          key={match.recurringId}
          match={match}
          categories={categories}
          open={match.entries.filter(matchOpen).length}
          accepted={acceptedMatches.includes(match.recurringId) && match.entries.some((entry) => !matchOpen(entry))}
          onAccept={() => acceptMatch(match)}
          onUndo={() => undoMatch(match)}
          onShowInList={showInList}
        />
      ))}

      {series.map((s) => (
        <SeriesCard
          key={s.lines.join()}
          series={s}
          categories={categories}
          planned={plannedSeries[s.lines.join()] ?? null}
          onShowInList={showInList}
          onPlan={(categoryId) => planSeries(s, categoryId)}
          onUnplan={() => unplanSeries(s)}
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
              { value: 'skipped', label: `Nicht importiert (${notImportable.length})` },
            ]}
          />
          <button type="button" disabled={withSuggestion.length === 0} onClick={acceptSuggestions}>
            Alle Vorschläge übernehmen ({withSuggestion.length})
          </button>
        </div>
      )}

      {rows.length > 0 && view !== 'skipped' && (
        <div className="filters import-filters">
          <TransactionFilters
            filters={filters}
            onChange={changeFilters}
            counts={counts}
            kinds={IMPORT_KINDS}
            categories={categories}
            accounts={preview.sources.map((source) => ({ value: source.key, label: source.name }))}
            extraCategoryOptions={[
              { value: WITHOUT_CATEGORY, label: 'Ohne Kategorie' },
              { value: TRANSFER, label: 'Umbuchung' },
            ]}
          />
          {filterActive && (
            <p className="hint import-filter-summary">
              {listed.length} von {rows.length} Zeilen
              {filtersChanged && (
                <button type="button" className="link-button" onClick={() => changeFilters(IMPORT_FILTERS)}>
                  Filter zurücksetzen
                </button>
              )}
            </p>
          )}
          {assignable.length > 0 && (
            <section className="import-assign" aria-label="Zuordnen">
              <span>
                {assignable.length === 1
                  ? 'Der angezeigten Zeile ohne Kategorie zuordnen:'
                  : `Den ${assignable.length} angezeigten Zeilen ohne Kategorie zuordnen:`}
              </span>
              <select
                aria-label="Kategorie für die angezeigten Zeilen"
                value={assignCategory}
                onChange={(e) => setAssignCategory(e.target.value)}
              >
                <option value="">Kategorie wählen …</option>
                <option value={TRANSFER}>Umbuchung (zählt nicht)</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
              <button type="button" className="primary" disabled={assignCategory === ''} onClick={assignShown}>
                Zuordnen
              </button>
            </section>
          )}
        </div>
      )}

      {view !== 'skipped' && (
        <ul className="import-list" aria-label="Neue Zeilen">
          {listed.map((entry) => (
            <PreviewRow
              key={entry.fingerprint}
              entry={entry}
              choice={choices[entry.fingerprint as string]}
              categories={categories}
              suggested={suggestionFor(entry)}
              highlighted={highlighted === entry.line}
              outsideFilter={outsideFilter(entry)}
              onJumpToPeer={jumpToPeer}
              onChange={(next) => change(entry, next)}
            />
          ))}
          {listed.length === 0 && (
            <li className="hint">
              {filterActive ? 'Keine Zeilen für diesen Filter.' : 'Keine neuen Zeilen.'}
            </li>
          )}
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
      {hiddenSelected > 0 && (
        <p className="hint import-hidden">
          {hiddenSelected === 1
            ? 'Davon ist 1 gewählte Zeile vom Filter ausgeblendet.'
            : `Davon sind ${hiddenSelected} gewählte Zeilen vom Filter ausgeblendet.`}
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
  // The uploaded file, to read it again once a part was taken over with a new series (R31)
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
          {upload.isPending && <Loading text="Lese Datei …" inline />}
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
          onReload={() => void reload()}
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
