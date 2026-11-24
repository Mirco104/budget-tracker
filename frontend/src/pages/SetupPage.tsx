import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useChangeSetup, useChangeStage } from '../api/account'
import { useAccounts } from '../api/accounts'
import { useTransactionRange } from '../api/transactions'
import { useCategories, useCreateCategory } from '../api/categories'
import { errorText } from '../api/client'
import { useCreateSavingGoal, useSavingGoals } from '../api/goals'
import { useDismissNotification } from '../api/notifications'
import { useBudgetSuggestions, usePlan, usePlanSuggestions, type PlanSuggestion } from '../api/plan'
import type { AccountLine, Category, EntryMode, IstStage, Me, Plan, PlanGroup, PlannedItem } from '../api/types'
import { AccountDialog } from '../components/AccountDialog'
import { BufferDone } from '../components/BufferDone'
import { useBufferToOther } from '../components/useBufferToOther'
import { PlannedItemDialog } from '../components/PlannedItemDialog'
import { SavingGoalDialog } from '../components/SavingGoalDialog'
import { StageChoice } from '../components/StageChoice'
import { StageQuestions } from '../components/StageQuestions'
import { StageSwitchDialog } from '../components/StageSwitchDialog'
import { CategoryIcon } from '../components/ui/CategoryIcon'
import { ICONS } from '../components/ui/categoryIcons'
import { Icon } from '../components/ui/Icon'
import { PlanSankey } from '../components/PlanSankey'
import { currentMonth, formatEuro } from '../format'
import { IMPORT_ROUTE, ROUTES, useHash } from '../router'
import { CHECKS, needsSwitchDialog, recommendStage, stageName } from '../stages'
import { clearSetupSession, markSetupOpen, readSetupImport, type SetupImport } from '../setupImport'
import { AccountRow } from './AccountsPage'
import { GroupSection } from './PlanningPage'

const STEPS = [
  'Start',
  'Tracking-Stufe',
  'Import',
  'Konten',
  'Einkommen',
  'Fixkosten',
  'Variable Kosten (Budgets)',
  'Sparen',
  'Sparziele',
  'Fertig',
] as const
const LAST = STEPS.length - 1
const ICON_SVG = {
  width: 18,
  height: 18,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
  focusable: false,
}

// Decision 88: an icon per step (also in the stepper on top) and one line instead of a lead text
const STEP_ICONS: ReactNode[] = [
  <Icon key="0" name="flag" size={18} />,
  <Icon key="1" name="search" size={18} />,
  <Icon key="import" name="upload" size={18} />,
  <svg key="2" {...ICON_SVG}>{ICONS.landmark.path}</svg>,
  <svg key="3" {...ICON_SVG}>{ICONS.briefcase.path}</svg>,
  <svg key="4" {...ICON_SVG}>{ICONS.receipt.path}</svg>,
  <svg key="5" {...ICON_SVG}>{ICONS.cart.path}</svg>,
  <svg key="6" {...ICON_SVG}>{ICONS.coins.path}</svg>,
  <svg key="7" {...ICON_SVG}>{ICONS.trend.path}</svg>,
  <Icon key="8" name="check" size={18} />,
]
const STEP_LINES: Record<number, string> = {
  1: 'Wie genau willst du sehen, was du ausgibst?',
  2: 'Kontoauszug (CSV) importieren?',
  3: 'Wo liegt dein Geld?',
  4: 'Was kommt jeden Monat rein?',
  5: 'Was geht jeden Monat fest ab?',
  6: 'Wie viel willst du für variable Kosten ausgeben?',
  7: 'Wie viel legst du regelmäßig zur Seite?',
  8: 'Wofür sparst du? Freiwillig.',
}
const ANSWERS_KEY = 'setup-stage-answers'
// The way chosen on the step Import, in the browser session (also after the import page)
const PATH_KEY = 'setup-path'

/**
 * Decision 94: the way of the step Import: a new bank file, the stored bookings (only for a user with bookings) or
 * entering the plan by hand (only without bookings).
 */
type SetupPath = 'import' | 'stored' | 'plan'

function readPath(): SetupPath | null {
  try {
    const stored = window.sessionStorage.getItem(PATH_KEY)
    return stored === 'import' || stored === 'stored' || stored === 'plan' ? stored : null
  } catch {
    return null
  }
}

function writePath(path: SetupPath) {
  try {
    window.sessionStorage.setItem(PATH_KEY, path)
  } catch {
    // only a convenience
  }
}
// Decision 91: the key prefix of a suggestion from a file read in the setup
const FILE_KEY = 'file:'

interface Answers {
  wish: IstStage | null
  time: IstStage | null
  /** Review 23.11.2026: the user chose a stage by hand; a changed answer then does not choose for them */
  manual?: boolean
}

function readAnswers(): Answers {
  try {
    const stored = JSON.parse(window.sessionStorage.getItem(ANSWERS_KEY) ?? 'null') as Answers | null
    return stored ?? { wish: null, time: null }
  } catch {
    return { wish: null, time: null }
  }
}

function writeAnswers(answers: Answers) {
  try {
    window.sessionStorage.setItem(ANSWERS_KEY, JSON.stringify(answers))
  } catch {
    // only a convenience; without storage the questions start empty again
  }
}
const REACHED_KEY = 'setup-reached'
const SONSTIGES = 'Sonstiges'

/** A typical item of "Häufig dazu": name, example amount, category by name and its rhythm (display data only). */
interface Typical {
  name: string
  amount: number
  category: string
  /** Decision 88: the icon of the tile */
  icon: string
  day?: number
  rhythm?: string
}

const TYPICAL: Record<PlanGroup, Typical[]> = {
  INCOME: [
    { name: 'Gehalt', amount: 2800, category: 'Gehalt/Lohn (netto)', day: 28, icon: 'briefcase' },
    { name: 'Kindergeld', amount: 255, category: 'Staatliche Leistungen', day: 15, icon: 'heart' },
    { name: 'Nebenjob', amount: 450, category: 'Nebenjob', day: 1, icon: 'laptop' },
  ],
  FIXED: [
    { name: 'Miete', amount: 850, category: 'Wohnen', day: 1, icon: 'home' },
    { name: 'Strom', amount: 65, category: 'Nebenkosten', day: 5, icon: 'bolt' },
    { name: 'Internet', amount: 35, category: 'Nebenkosten', day: 10, icon: 'laptop' },
    { name: 'Handy', amount: 15, category: 'Nebenkosten', day: 15, icon: 'pulse' },
    { name: 'Rundfunkbeitrag', amount: 55.08, category: 'Nebenkosten', day: 15, rhythm: 'MONTH:3', icon: 'music' },
    { name: 'Haftpflichtversicherung', amount: 60, category: 'Versicherungen', day: 1, rhythm: 'MONTH:12', icon: 'shield' },
    { name: 'Hausratversicherung', amount: 90, category: 'Versicherungen', day: 1, rhythm: 'MONTH:12', icon: 'shield' },
    { name: 'Kfz-Versicherung', amount: 420, category: 'Versicherungen', day: 1, rhythm: 'MONTH:12', icon: 'car' },
    { name: 'Deutschlandticket', amount: 58, category: 'Mobilität', day: 1, icon: 'suitcase' },
    { name: 'Streaming', amount: 13, category: 'Freizeit', day: 18, icon: 'music' },
    { name: 'Fitnessstudio', amount: 30, category: 'Freizeit', day: 1, icon: 'heart' },
  ],
  BUDGET: [
    { name: 'Lebensmittel', amount: 350, category: 'Lebensmittel', icon: 'cart' },
    { name: 'Freizeit', amount: 120, category: 'Freizeit', icon: 'music' },
    { name: 'Persönlicher Bedarf', amount: 60, category: 'Persönlicher Bedarf', icon: 'bag' },
    { name: 'Mobilität', amount: 60, category: 'Mobilität', icon: 'car' },
    { name: 'Geschenke', amount: 25, category: 'Geschenke', icon: 'gift' },
    { name: SONSTIGES, amount: 80, category: SONSTIGES, icon: 'tag' },
  ],
  SAVING: [
    { name: 'Notgroschen', amount: 100, category: '', day: 1, icon: 'shield' },
    { name: 'ETF-Sparplan', amount: 150, category: '', day: 1, icon: 'trend' },
  ],
}

const GOAL_SUGGESTIONS: { name: string; target: number | null }[] = [
  { name: 'Notgroschen', target: 5000 },
  { name: 'Urlaub', target: 1500 },
  { name: 'Neues Auto', target: null },
]

const RHYTHM_LABEL: Record<string, string> = { 'MONTH:3': 'vierteljährlich', 'MONTH:12': 'jährlich' }

type DialogState = {
  item: PlannedItem | null
  group: PlanGroup
  preset?: {
    name?: string
    amount?: number
    categoryId?: number
    accountId?: number
    day?: number
    rhythm?: string
    entryMode?: EntryMode
  }
  suggestionKey?: string
}

function readReached(): number {
  try {
    return Number(window.sessionStorage.getItem(REACHED_KEY) ?? '0') || 0
  } catch {
    return 0
  }
}

function writeReached(step: number) {
  try {
    window.sessionStorage.setItem(REACHED_KEY, String(step))
  } catch {
    // only a convenience; without storage the stepper goes back only
  }
}

// Outside the component: the address bar is the state of the wizard (US29)
function openStep(step: number) {
  window.location.hash = `${ROUTES.setup}/${step}`
  window.scrollTo(0, 0)
}

function openOverview() {
  window.location.hash = ROUTES.overview
}

/** Decision 83: income and fixed costs created in the wizard are entered automatically; decision 89: never in stage 1. */
function wizardEntry(group: PlanGroup, stage: IstStage): EntryMode | undefined {
  if (stage === 1) {
    return 'MANUAL'
  }
  return group === 'INCOME' || group === 'FIXED' ? 'AUTO' : undefined
}

/**
 * US40, US41: the setup wizard. Every step works on the real data with the parts of the page Planung (decision 82):
 * accounts, income, fixed costs, budgets and saving are planned items; nothing is stored apart from them except the
 * stage and "setup done".
 */
export function SetupPage({ me }: { me: Me }) {
  const hash = useHash()
  const month = currentMonth()
  const plan = usePlan(month)
  const categories = useCategories(month)
  const accounts = useAccounts()
  const goals = useSavingGoals()
  const changeSetup = useChangeSetup()
  const [path, setPathState] = useState<SetupPath | null>(readPath)
  function setPath(next: SetupPath) {
    setPathState(next)
    writePath(next)
  }
  // Decision 94: a user with bookings gets suggestions from them instead of entering the plan by hand
  const range = useTransactionRange()
  const hasBookings = (range.data?.from ?? null) !== null
  // Decision 86: the answers to the two questions of the step "Tracking-Stufe", only in the browser session
  const [answers, setAnswersState] = useState<Answers>(readAnswers)
  function setAnswers(next: Answers) {
    setAnswersState(next)
    writeAnswers(next)
  }
  const [error, setError] = useState<string | null>(null)

  // Review 23.11.2026: the import page knows the wizard is open, also when the setup was done before
  useEffect(() => markSetupOpen(), [])

  const stepFromHash = Number(hash.slice(ROUTES.setup.length + 1))
  const step = Number.isInteger(stepFromHash) && stepFromHash >= 0 && stepFromHash <= LAST ? stepFromHash : 0
  const [stored, setReached] = useState(readReached)
  // A step opened by its address (e.g. „ändern“ on the last page) counts as reached
  const reached = Math.max(stored, step)

  const accountLines: AccountLine[] = useMemo(
    () => (accounts.data ?? []).flatMap((group) => group.accounts).filter((a) => !a.archived),
    [accounts.data],
  )
  const data = plan.data
  const existing = (data?.items.length ?? 0) > 0
  // Decision 91: the suggestions of a file read in this setup (also in stage 1, where nothing is stored)
  const fromFile = readSetupImport()

  function complete(at: number): boolean {
    if (at === 1) {
      return answers.wish !== null && answers.time !== null
    }
    // Decision 91: the way comes after the stage
    if (at === 2) {
      // Decision 94: only the chosen way unlocks the step; the import way once a file was read in this setup
      return path === 'stored' || path === 'plan' || fromFile !== null
    }
    if (at === 3) {
      return (
        accountLines.length > 0 &&
        (me.istStage !== 2 || accountLines.filter((a) => a.role === 'EVERYDAY').every((a) => a.statementDate !== null))
      )
    }
    if (at === 4) {
      return (data?.income ?? 0) > 0
    }
    if (at === 5) {
      return (data?.items ?? []).some((item) => item.group === 'FIXED')
    }
    if (at === 6) {
      return (data?.items ?? []).some((item) => item.group === 'BUDGET')
    }
    return true
  }

  // The furthest step the stepper may jump to: reached already, never past an incomplete step
  let maxJump = reached
  for (let i = 0; i < reached; i++) {
    if (!complete(i)) {
      maxJump = Math.max(i, step)
      break
    }
  }
  if (!complete(step)) {
    maxJump = Math.min(maxJump, step)
  }

  function go(to: number) {
    const next = Math.max(reached, to)
    setReached(next)
    writeReached(next)
    openStep(to)
  }

  async function finish() {
    setError(null)
    try {
      await changeSetup.mutateAsync(true)
      writeReached(0)
      clearSetupSession([ANSWERS_KEY, PATH_KEY])
      openOverview()
    } catch (e) {
      setError(errorText(e, 'Die Einrichtung konnte nicht abgeschlossen werden.'))
    }
  }

  if (plan.isPending || categories.isPending || accounts.isPending) {
    return <p className="hint">Lade Einrichtung …</p>
  }
  if (!data || !categories.data) {
    return <p className="error">Die Einrichtung konnte nicht geladen werden.</p>
  }

  const body = (() => {
    switch (step) {
      case 0:
        return <StartStep existing={existing} />
      case 1:
        return <StageStep me={me} existing={existing} answers={answers} onAnswers={setAnswers} />
      case 2:
        return (
          <ImportStep
            stage={me.istStage}
            existing={existing}
            hasBookings={hasBookings}
            summary={fromFile}
            path={path}
            onPath={setPath}
          />
        )
      case 3:
        return <AccountsStep me={me} accounts={accountLines} />
      case 4:
      case 5:
      case 6:
      case 7:
        return (
          <PlanStep
            step={step}
            plan={data}
            categories={categories.data}
            month={month}
            accounts={accountLines}
            stage={me.istStage}
            fromFile={path === 'stored' || path === 'plan' ? null : fromFile}
          />
        )
      case 8:
        return <GoalsStep />
      default:
        return (
          <DoneStep
            me={me}
            plan={data}
            accounts={accountLines}
            goals={(goals.data ?? []).map((g) => g.name)}
          />
        )
    }
  })()

  return (
    <section className="setup">
      <nav className="setup-steps" aria-label="Schritte der Einrichtung">
        {STEPS.map((label, i) =>
          i !== step && i <= maxJump ? (
            <a key={label} className="done" href={`${ROUTES.setup}/${i}`} onClick={(e) => { e.preventDefault(); go(i) }}>
              <span className="step-icon">{STEP_ICONS[i]}</span>
              {label}
            </a>
          ) : (
            <span key={label} className={i === step ? 'now' : i < step ? 'done' : undefined} aria-current={i === step ? 'step' : undefined}>
              <span className="step-icon">{STEP_ICONS[i]}</span>
              {label}
            </span>
          ),
        )}
      </nav>
      {step > 0 && step < LAST && <p className="setup-count">Schritt {step} von {LAST - 1}</p>}
      {body}
      {!complete(step) && missingText(step, me.istStage, hasBookings) !== null && (
        <p className="setup-missing">{missingText(step, me.istStage, hasBookings)}</p>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="setup-foot">
        {step >= 4 && step <= 8 && <PlanFillBar plan={data} step={step} />}
        {step > 0 ? (
          <button type="button" onClick={() => go(step - 1)}>
            Zurück
          </button>
        ) : (
          <button type="button" className="link-button muted" onClick={() => void finish()} disabled={changeSetup.isPending}>
            Später einrichten
          </button>
        )}
        {step < LAST ? (
          <button type="button" className="primary" disabled={!complete(step)} onClick={() => go(step + 1)}>
            {step === LAST - 1 ? 'Eingaben überprüfen' : 'Weiter'}
          </button>
        ) : (
          <button type="button" className="primary" onClick={() => void finish()} disabled={changeSetup.isPending}>
            Einrichtung abschließen
          </button>
        )}
      </div>
    </section>
  )
}

// Review 23.11.2026: every step has its own text; the Tracking-Stufe says on the page what is missing
function missingText(step: number, stage: IstStage, hasBookings: boolean): string | null {
  if (step === 1) {
    return null
  }
  if (step === 2) {
    return hasBookings
      ? 'Importiere einen Kontoauszug oder wähle „Vorschläge aus meinen Buchungen“, um weiterzumachen.'
      : 'Importiere einen Kontoauszug oder wähle „Plan selbst eintragen“, um weiterzumachen.'
  }
  if (step === 3) {
    return stage === 2
      ? 'Trag für jedes Alltagskonto den Kontostand ein, um weiterzumachen.'
      : 'Leg mindestens ein Konto an, um weiterzumachen.'
  }
  if (step === 4) {
    return 'Trag mindestens ein Einkommen über 0 € ein, um weiterzumachen.'
  }
  if (step === 5) {
    return 'Trag mindestens eine Fixkosten-Zahlung ein, um weiterzumachen.'
  }
  if (step === 6) {
    return 'Trag mindestens ein Budget ein, um weiterzumachen.'
  }
  return null
}

/** Decision 88: with a step the lead is its icon and one line; the explanation folds behind "Mehr dazu". */
function Lead({ title, step, info, children }: { title: string; step?: number; info?: string; children?: ReactNode }) {
  if (step !== undefined) {
    return (
      <div className="setup-lead with-icon">
        <span className="setup-step-icon" aria-hidden="true">
          {STEP_ICONS[step]}
        </span>
        <div>
          <h1>{STEP_LINES[step]}</h1>
          {(info || children) && (
            <details className="setup-more">
              <summary>
                <Icon name="info" size={16} /> Mehr dazu
              </summary>
              {info && <p>{info}</p>}
              {children && <p className="tx-meta">{children}</p>}
            </details>
          )}
        </div>
      </div>
    )
  }
  return (
    <div className="setup-lead">
      <h1>{title}</h1>
      {info && <p className="setup-info">{info}</p>}
      {children && <p className="tx-meta">{children}</p>}
    </div>
  )
}

function StartStep({ existing }: { existing: boolean }) {
  if (existing) {
    return (
      <>
        <Lead title="Willkommen zurück! Lass uns deinen Plan prüfen.">
          Deine bisherige Planung ist übernommen: Konten, Posten und Sparziele stehen schon in den Schritten. Du prüfst
          jeden Schritt, ergänzt, was fehlt, und wählst, wie genau du dein Ist verfolgst.
        </Lead>
        <SetupOutline />
      </>
    )
  }
  return (
    <>
      <Lead title="Willkommen! Lass uns deinen Monat planen.">
        Du planst einmal einen normalen Monat. Das dauert etwa 10 Minuten, und du kannst später alles ändern.
      </Lead>
      <SetupOutline />
    </>
  )
}

// Decision 91: what the import keeps per stage (display texts)
const IMPORT_TAKES: [string, string, string, string][] = [
  ['Nur Plan', 'keine, die Datei liefert nur Vorschläge für den Plan', '–', 'aus der Datei, wenn die Bank ihn liefert'],
  ['Kontostand', 'nur die regelmäßigen (Einkommen, Fixkosten, Sparraten)', 'vom Posten', 'aus der Datei oder im Schritt Konten'],
  ['Buchungen', 'alle', 'keine, nur „Zählt als“', 'aus der Datei oder im Schritt Konten'],
  ['Buchungen mit Kategorien', 'alle', 'Vorschlag je Zeile', 'aus der Datei oder im Schritt Konten'],
]

/**
 * Decision 91: the way after the stage: a bank file suggests the plan, or the user enters it. After an import the
 * step says what the stage keeps of it.
 */
function ImportStep({
  stage,
  existing,
  hasBookings,
  summary,
  path,
  onPath,
}: {
  stage: IstStage
  existing: boolean
  hasBookings: boolean
  summary: SetupImport | null
  path: SetupPath | null
  onPath: (path: SetupPath) => void
}) {
  // After the import page the file way stays chosen even when the session lost the choice
  const fileWay = path === 'import' || (path === null && summary !== null)
  const sentence =
    summary === null
      ? null
      : stage === 1
        ? 'Keine Buchung wurde gespeichert. Aus der Datei kommen nur die Vorschläge in den nächsten Schritten.'
        : stage === 2
          ? `Nur die regelmäßigen Buchungen werden übernommen (${summary.recurring} von ${summary.rows}). Die übrigen Ausgaben zählen in Stufe 2 über den Kontostand.`
          : stage === 3
            ? `Alle ${summary.rows} Buchungen werden übernommen, ohne Kategorie.`
            : `Alle ${summary.rows} Buchungen werden übernommen, jede mit einem Kategorie-Vorschlag.`
  return (
    <>
      <Lead step={2} title="Kontoauszug (CSV) importieren?" info="Mit Kontoauszug schlägt die App deinen Plan vor, ohne trägst du ihn selbst ein.">
        Mit einem Kontoauszug schlägt die App Einkommen, Fixkosten und variable Kosten vor. Was sie dabei als Buchung
        speichert, hängt von deiner Stufe ab (Stufe {stage}: {stageName(stage)}).
        {existing ? ' Deine bisherige Planung bleibt; ein Import ergänzt nur Vorschläge.' : ''}
      </Lead>
      {/* Decision 94: a user with bookings chooses between them and a new file; everyone else as before */}
      {hasBookings ? (
        <div className="setup-choices">
          <button type="button" className={path === 'stored' ? 'setup-choice on' : 'setup-choice'} onClick={() => onPath('stored')}>
            <span className="badge planned">Empfohlen</span>
            <strong>Vorschläge aus meinen Buchungen</strong>
            <span className="tx-meta">
              Deine gespeicherten Buchungen schlagen Einkommen, Fixkosten und Budgets vor. Es wird nichts neu importiert.
            </span>
          </button>
          <button type="button" className={fileWay ? 'setup-choice on' : 'setup-choice'} onClick={() => onPath('import')}>
            <span className="badge">CSV-Datei</span>
            <strong>Neuen Kontoauszug importieren</strong>
            <span className="tx-meta">
              Die Datei schlägt den Plan vor; deine Stufe bestimmt, was davon gespeichert wird. Weiter geht es nach dem Import.
            </span>
          </button>
        </div>
      ) : (
        <div className="setup-choices">
          <button type="button" className={fileWay ? 'setup-choice on' : 'setup-choice'} onClick={() => onPath('import')}>
            <span className="badge planned">Empfohlen</span>
            <strong>Plan aus Kontoauszügen vorschlagen</strong>
            <span className="tx-meta">
              Du lädst deine Kontoauszüge als CSV-Datei hoch, mindestens 3 Monate, besser 6 bis 12. Die App schlägt Einkommen,
              Fixkosten und Budgets vor, du prüfst jeden Vorschlag.
            </span>
          </button>
          <button type="button" className={path === 'plan' ? 'setup-choice on' : 'setup-choice'} onClick={() => onPath('plan')}>
            <span className="badge">Ohne Kontoauszug</span>
            <strong>Plan selbst eintragen</strong>
            <span className="tx-meta">
              Du trägst deine Beträge selbst ein. Wir geben typische Posten vor. Kontoauszüge kannst du später jederzeit
              importieren.
            </span>
          </button>
        </div>
      )}
      {fileWay && (
        <section className="card setup-import" aria-label="Kontoauszug">
          {summary === null ? (
            <p className="tx-meta">
              Mindestens 3 Monate, besser 6 bis 12. C24, Sparkasse, Deutsche Bank, VR-Bank, N26, Trade Republic, PayPal; mehrere
              Dateien und Konten sind möglich. Nach dem Import kommst du hierher zurück.
            </p>
          ) : (
            <p>
              <strong>Kontoauszug gelesen.</strong> {sentence}
            </p>
          )}
          <div className="table-wrap">
            <table className="setup-takes">
              <thead>
                <tr>
                  <th>Stufe</th>
                  <th>Buchungen</th>
                  <th>Kategorie</th>
                  <th>Kontostand</th>
                </tr>
              </thead>
              <tbody>
                {IMPORT_TAKES.map(([name, bookings, category, balance], i) => (
                  <tr key={name} className={i + 1 === stage ? 'on' : undefined}>
                    <td>
                      <strong>{i + 1}</strong> {name}
                      {i + 1 === stage && <span className="badge planned">deine Stufe</span>}
                    </td>
                    <td>{bookings}</td>
                    <td>{category}</td>
                    <td>{balance}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="tx-meta">
            Wechselst du später auf eine höhere Stufe, importierst du die Datei noch einmal. Doppelte Buchungen erkennt die App.
          </p>
          <div>
            <a className="button-link" href={IMPORT_ROUTE}>
              {summary === null ? 'Kontoauszug importieren' : 'Weitere Datei importieren'}
            </a>
          </div>
        </section>
      )}
    </>
  )
}

function SetupOutline() {
  const rows: [number, string, string][] = [
    [1, 'Tracking-Stufe', 'Wie genau du später nachsehen willst, was du wirklich ausgibst'],
    [2, 'Import', 'Ein Kontoauszug als CSV-Datei schlägt deinen Plan vor, oder du trägst ihn selbst ein'],
    [3, 'Konten', 'Girokonto, Sparkonto, Depot …'],
    [4, 'Einkommen', 'Was jeden Monat reinkommt, z. B. das Gehalt'],
    [5, 'Fixkosten', 'Was regelmäßig abgeht, z. B. Miete, Strom, Versicherungen'],
    [6, 'Variable Kosten (Budgets)', 'Wie viel du für variable Kosten einplanst, z. B. Lebensmittel'],
    [7, 'Sparen', 'Regelmäßige Sparraten und, wenn du willst, Sparziele'],
  ]
  return (
    <div className="card setup-outline">
      <h3>Das richten wir zusammen ein</h3>
      <dl>
        {rows.map(([step, title, text]) => (
          <div key={title}>
            <dt>
              <span className="step-icon" aria-hidden="true">
                {STEP_ICONS[step]}
              </span>
              {title}
            </dt>
            <dd>{text}</dd>
          </div>
        ))}
      </dl>
    </div>
  )
}

function StageStep({
  me,
  existing,
  answers,
  onAnswers,
}: {
  me: Me
  existing: boolean
  answers: Answers
  onAnswers: (answers: Answers) => void
}) {
  const changeStage = useChangeStage()
  const [error, setError] = useState<string | null>(null)
  const [asking, setAsking] = useState<IstStage | null>(null)
  const recommended = answers.wish !== null && answers.time !== null ? recommendStage(answers.wish, answers.time) : null

  // Decision 90: with items a switch from or to stage 1 asks first; without items the stage changes directly
  function choose(stage: IstStage) {
    setError(null)
    if (stage === me.istStage) {
      return
    }
    switchTo(stage)
  }

  // A stage chosen by hand stays, even when an answer changes later
  function chooseByHand(stage: IstStage) {
    onAnswers({ ...answers, manual: true })
    choose(stage)
  }

  function switchTo(stage: IstStage) {
    if (existing && needsSwitchDialog(me.istStage, stage)) {
      setAsking(stage)
      return
    }
    changeStage.mutate({ stage }, { onError: (e) => setError(errorText(e, 'Die Stufe konnte nicht gespeichert werden.')) })
  }

  // Decision 86: once both questions are answered the recommended stage is chosen (a switch that asks first waits)
  function answer(next: Answers) {
    onAnswers(next)
    if (next.wish !== null && next.time !== null && !next.manual) {
      const stage = recommendStage(next.wish, next.time)
      if (!(existing && needsSwitchDialog(me.istStage, stage))) {
        choose(stage)
      }
    }
  }

  return (
    <>
      <Lead step={1} title="Wie genau willst du verfolgen, was du ausgibst?" info="Davon hängt ab, was die App in den nächsten Schritten von dir braucht.">
        Der Plan sagt, wie viel du ausgeben willst; das Ist zeigt, wie viel du wirklich ausgegeben hast. Beantworte zwei
        Fragen, dann empfehlen wir dir eine Stufe. Du kannst jede Stufe wählen und sie jederzeit unter Einstellungen › Ist
        verfolgen ändern; deine Daten bleiben erhalten.{existing ? ' Stufe 4 entspricht dem, wie du die App bisher genutzt hast.' : ''}
      </Lead>
      <StageQuestions
        wish={answers.wish}
        time={answers.time}
        onWish={(wish) => answer({ ...answers, wish })}
        onTime={(time) => answer({ ...answers, time })}
      />
      {recommended === null ? (
        <p className="tx-meta">Beantworte beide Fragen, dann zeigen wir dir die Stufen und unsere Empfehlung.</p>
      ) : (
        <>
          <h2>Deine Stufe</h2>
          <StageChoice value={me.istStage} recommended={recommended} disabled={changeStage.isPending} onChange={chooseByHand} />
        </>
      )}
      {asking !== null && <StageSwitchDialog from={me.istStage} to={asking} onClose={() => setAsking(null)} />}
      {error && <p className="error">{error}</p>}
    </>
  )
}

function AccountsStep({ me, accounts }: { me: Me; accounts: AccountLine[] }) {
  const [open, setOpen] = useState<AccountLine | 'new' | null>(null)
  const note =
    me.istStage === 1
      ? 'In Stufe 1 brauchst du keinen Kontostand. Trägst du einen ein, zeigt die Seite Konten ihn an.'
      : me.istStage === 2
        ? 'In Stufe 2 brauchst du den Kontostand deines Alltagskontos: Mit ihm rechnet die App am Monatsende aus, was du variabel ausgegeben hast. Für Spar- und Depotkonten ist er freiwillig.'
        : 'Der Kontostand ist freiwillig. Mit ihm zeigt die Seite Konten, ob die App und deine Bank denselben Stand haben.'
  return (
    <>
      <Lead step={3} title="Welche Konten hast du?" info="Konten sind die Orte, an denen dein Geld liegt.">
        Leg das Konto an, von dem du deine laufenden Ausgaben bezahlst. Hast du ein Sparkonto, Tagesgeld oder Depot, leg es gleich mit an,
        dann kannst du Sparraten darauf planen.
      </Lead>
      <p className="hint">{note}</p>
      <section className="card plan-group" aria-label="Konten">
        <div className="plan-group-head">
          <h2>Konten</h2>
        </div>
        {accounts.length === 0 ? (
          <p className="hint">Noch kein Konto.</p>
        ) : (
          <ul className="item-list">
            {accounts.map((account) => (
              <AccountRow key={account.id} account={account} onOpen={() => setOpen(account)} />
            ))}
          </ul>
        )}
        <button type="button" className="link-button" onClick={() => setOpen('new')}>
          + Konto
        </button>
      </section>
      {open && <AccountDialog account={open === 'new' ? null : open} onClose={() => setOpen(null)} />}
    </>
  )
}

const PLAN_STEPS: Record<number, { group: PlanGroup; title: string; info: string; text: string }> = {
  4: {
    group: 'INCOME',
    title: 'Was kommt rein?',
    info: 'Das Geld, das jeden Monat reinkommt.',
    text: 'Dein Einkommen in einem normalen Monat. Klick auf einen Posten, um ihn zu ändern.',
  },
  5: {
    group: 'FIXED',
    title: 'Was geht fest ab?',
    info: 'Zahlungen, die regelmäßig in gleicher Höhe abgehen.',
    text: 'Seltene Zahlungen trägst du mit ihrem echten Betrag ein (z. B. 60 € jährlich); die App rechnet aus, wie viel das im Monat ist, und legt eine Rücklage an. Die App trägt Fixkosten an ihrem Tag selbst ein; kommt später ein Import, ersetzt die echte Buchung den Eintrag.',
  },
  6: {
    group: 'BUDGET',
    title: 'Wie viel für variable Kosten?',
    info: 'Geld für Ausgaben, die jeden Monat anders sind.',
    text: 'Variable Kosten sind Ausgaben, die jeden Monat anders sind, z. B. Lebensmittel. Je Kategorie planst du einen oder mehrere Posten, z. B. unter Lebensmittel „Wocheneinkauf“ und „Wochenmarkt“.',
  },
  7: {
    group: 'SAVING',
    title: 'Sparst du regelmäßig?',
    info: 'Geld, das du regelmäßig auf ein Spar- oder Depotkonto überweist.',
    text: 'Eine Sparrate ist eine feste Überweisung, z. B. jeden Monat 200 € aufs Tagesgeld oder in einen ETF-Sparplan. Du kannst auch keine Sparrate anlegen.',
  },
}

function PlanStep({
  step,
  plan,
  categories,
  month,
  accounts,
  stage,
  fromFile,
}: {
  step: number
  plan: Plan
  categories: Category[]
  month: string
  accounts: AccountLine[]
  stage: IstStage
  /** Decision 91: the suggestions of a file read in this setup; they replace those of the stored bookings */
  fromFile: SetupImport | null
}) {
  const { group, title, info, text } = PLAN_STEPS[step]
  const suggestions = usePlanSuggestions()
  const budgetSuggestions = useBudgetSuggestions()
  const dismiss = useDismissNotification()
  const createCategory = useCreateCategory()
  const [dialog, setDialog] = useState<DialogState | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [typicalOpen, setTypicalOpen] = useState<boolean | null>(null)
  // Decision 91: suggestions of the file hidden in this session (they are not stored, so hiding stays local)
  const [hiddenFile, setHiddenFile] = useState<string[]>([])
  const active = categories.filter((c) => !c.archived)

  // A category by name; created when missing (e.g. "Sonstiges" or a bank category taken over)
  async function categoryNamed(name: string): Promise<number | undefined> {
    if (name === '') {
      return undefined
    }
    const found = active.find((c) => c.name.toLowerCase() === name.toLowerCase() && c.parentId === null)
    if (found) {
      return found.id
    }
    return (await createCategory.mutateAsync({ name })).id
  }

  async function openTypical(typical: Typical) {
    setError(null)
    try {
      const categoryId = await categoryNamed(typical.category)
      const target = group === 'SAVING' ? accounts.find((a) => a.role !== 'EVERYDAY') : undefined
      setDialog({
        item: null,
        group,
        preset: {
          name: typical.name,
          amount: typical.amount,
          categoryId,
          day: typical.day,
          rhythm: typical.rhythm,
          accountId: target?.id,
          entryMode: wizardEntry(group, stage),
        },
      })
    } catch (e) {
      setError(errorText(e, 'Die Kategorie konnte nicht angelegt werden.'))
    }
  }

  async function takeBudget(name: string, categoryId: number | null, amount: number) {
    setError(null)
    try {
      setDialog({ item: null, group: 'BUDGET', preset: { name, amount, categoryId: categoryId ?? (await categoryNamed(name)) } })
    } catch (e) {
      setError(errorText(e, 'Die Kategorie konnte nicht angelegt werden.'))
    }
  }

  // P4 "Als Puffer in Sonstiges": the rest goes to the budget of the catch-all category; field test 24.11.2026:
  // "Rückgängig" takes it back while the step is open
  const buffer = useBufferToOther()
  async function toOther() {
    setError(null)
    try {
      await buffer.put(plan, (await categoryNamed(SONSTIGES)) as number, month)
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
    }
  }

  async function undoOther() {
    setError(null)
    try {
      await buffer.undo()
    } catch (e) {
      setError(errorText(e, 'Rückgängig machen fehlgeschlagen.'))
    }
  }

  const names = new Set(plan.items.map((item) => item.name.trim().toLowerCase()))
  const typical = TYPICAL[group].filter((t) => !names.has(t.name.toLowerCase()))
  // Decision 91: in the setup a file's series (R31) are the suggestions; a series of saving belongs to no group here
  const fileSuggestions: PlanSuggestion[] =
    fromFile === null
      ? []
      : fromFile.series
          .filter((s) => !s.saving)
          .map((s) => ({
            key: `${FILE_KEY}${s.source}|${s.description}|${s.amount}`,
            name: s.description,
            amount: s.amount,
            categoryId: s.categoryId,
            categoryName: categories.find((c) => c.id === s.categoryId)?.path ?? null,
            accountId: null,
            anchorDate: s.startDate,
            months: s.lines.length,
            group: s.amount > 0 ? ('INCOME' as const) : ('FIXED' as const),
          }))
          .filter((s) => !hiddenFile.includes(s.key) && !names.has(s.name.trim().toLowerCase()))
  const groupSuggestions = (fromFile === null ? (suggestions.data ?? []) : fileSuggestions).filter((s) => s.group === group)
  const budgetedCategories = new Set(plan.items.filter((item) => item.group === 'BUDGET').map((item) => item.planCategoryId))
  const budgetList =
    fromFile === null
      ? (budgetSuggestions.data ?? [])
      : fromFile.budgetSuggestions.filter((b) => b.categoryId === null || !budgetedCategories.has(b.categoryId))
  const hasSuggestions = groupSuggestions.length > 0 || (group === 'BUDGET' && budgetList.length > 0)
  // With suggestions from the bookings the typical items stay folded until asked for
  const showTypical = typicalOpen ?? !hasSuggestions
  const iconOf = (id: number) => categories.find((c) => c.id === id)?.icon ?? 'tag'

  return (
    <>
      <Lead step={step} title={title} info={info}>
        {text}
      </Lead>
      {group === 'SAVING' && plan.unplanned > 0 && (
        <div className="card unplanned-question">
          <p>
            <strong>{formatEuro(plan.unplanned)} sind noch nicht verplant.</strong> Was soll damit passieren?
          </p>
          <div className="plan-actions">
            <button
              type="button"
              className="saving-button"
              onClick={() =>
                setDialog({
                  item: null,
                  group: 'SAVING',
                  preset: { name: 'Sparen', amount: plan.unplanned, accountId: accounts.find((a) => a.role !== 'EVERYDAY')?.id },
                })
              }
            >
              Monatlich sparen
            </button>
            <button type="button" onClick={() => void toOther()} disabled={buffer.busy}>
              Als Puffer in „Sonstiges“
            </button>
          </div>
        </div>
      )}
      {group === 'SAVING' && buffer.moved !== null && plan.unplanned === 0 && (
        <BufferDone amount={buffer.moved} busy={buffer.busy} onUndo={() => void undoOther()} />
      )}
      <GroupSection
        title={title === 'Was kommt rein?' ? 'Einkommen' : STEPS[step]}
        hint={info}
        group={group}
        plan={plan}
        onEdit={(item) => setDialog({ item, group })}
        onAdd={() => setDialog({ item: null, group, preset: { entryMode: wizardEntry(group, stage) } })}
        suggestions={groupSuggestions}
        onTakeSuggestion={(s) =>
          setDialog({
            item: null,
            group,
            suggestionKey: s.key,
            preset: {
              name: s.name,
              amount: Math.abs(s.amount),
              categoryId: s.categoryId ?? undefined,
              accountId: s.accountId ?? undefined,
              day: Number(s.anchorDate.slice(8, 10)),
              entryMode: wizardEntry(group, stage),
            },
          })
        }
        onHideSuggestion={(s) => (s.key.startsWith(FILE_KEY) ? setHiddenFile((keys) => [...keys, s.key]) : dismiss.mutate(s.key))}
        iconOf={iconOf}
      />
      {group === 'INCOME' && (plan.averageIncome !== null || (fromFile?.income.average ?? null) !== null) && (
        <p className="hint">Schwankt ein Einkommen, plane im Zweifel mit dem kleineren Betrag. Dann bleibt am Monatsende eher etwas übrig, als dass etwas fehlt.</p>
      )}
      {group === 'INCOME' && fromFile !== null && fromFile.income.average !== null && (
        <div className="income-stats" aria-label="Einkommen im Kontoauszug">
          <div className="income-stat">
            <span className="income-stat-label">Durchschnitt</span>
            <span className="income-stat-value">{formatEuro(fromFile.income.average)}</span>
            <span className="tx-meta">pro Monat, {fromFile.income.months === 1 ? '1 Monat' : `${fromFile.income.months} Monate`}</span>
          </div>
          <div className="income-stat">
            <span className="income-stat-label">Mindestens</span>
            <span className="income-stat-value">{formatEuro(fromFile.income.minimum ?? 0)}</span>
            <span className="tx-meta">im schwächsten Monat</span>
          </div>
        </div>
      )}
      {group === 'BUDGET' && budgetList.length > 0 && (
        <section className="card plan-suggestions setup-suggestions" aria-label="Vorschläge aus deinen Buchungen">
          <h3>Vorschläge aus deinen Buchungen</h3>
          <p className="tx-meta">Dein Durchschnitt je Kategorie in den letzten Monaten. Was du übernimmst, wird ein Budget dieser Kategorie.</p>
          <ul className="item-list">
            {budgetList.map((s) => (
              <li key={`${s.categoryId ?? ''}|${s.bankCategory ?? ''}`} className="suggestion-row">
                <span className="item-main">
                  <span className="item-name">{s.name}</span>
                  <span className="tx-meta">
                    {s.categoryId === null ? 'Kategorie der Bank · ' : ''}Ø {formatEuro(s.average)} im Monat ·{' '}
                    {s.months === 1 ? '1 Monat' : `${s.months} Monate`}
                  </span>
                </span>
                <span className="item-amount">{formatEuro(s.proposal)}</span>
                <span className="suggestion-actions">
                  <button type="button" className="link-button" onClick={() => void takeBudget(s.name, s.categoryId, s.proposal)}>
                    Übernehmen
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
      {typical.length > 0 && (
        <section className="card plan-suggestions setup-typical" aria-label="Häufig dazu">
          <div className="plan-group-head">
            <h3>Häufig dazu</h3>
            {hasSuggestions && (
              <button type="button" className="link-button" onClick={() => setTypicalOpen(!showTypical)}>
                {showTypical ? 'ausblenden' : `anzeigen (${typical.length})`}
              </button>
            )}
          </div>
          {showTypical && (
            <div className="setup-tiles">
              {typical.map((t) => (
                <button
                  key={t.name}
                  type="button"
                  className="setup-tile"
                  aria-label={`${t.name} hinzufügen`}
                  onClick={() => void openTypical(t)}
                  disabled={createCategory.isPending}
                >
                  <CategoryIcon icon={t.icon} />
                  <strong>{t.name}</strong>
                  <span className="tx-meta">
                    z. B. {formatEuro(t.amount)}
                    {t.rhythm ? ` ${RHYTHM_LABEL[t.rhythm]}` : ''}
                  </span>
                </button>
              ))}
            </div>
          )}
        </section>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {dialog && (
        <PlannedItemDialog
          categories={categories}
          item={dialog.item}
          month={month}
          group={dialog.group}
          preset={dialog.preset}
          onClose={() => setDialog(null)}
          onDone={() => {
            if (dialog.suggestionKey && !dialog.suggestionKey.startsWith(FILE_KEY)) {
              dismiss.mutate(dialog.suggestionKey)
            }
            setDialog(null)
          }}
        />
      )}
    </>
  )
}

/**
 * Decision 88: the income as a bar that fills step by step with fixed costs, variable costs and saving (the P4 sums of
 * the plan); what is left is "Noch frei", steps still to come are "kommt noch".
 */
function PlanFillBar({ plan, step }: { plan: Plan; step: number }) {
  const parts: { key: string; label: string; value: number; from: number }[] = [
    { key: 'fixed', label: 'Fixkosten', value: plan.fixed, from: 5 },
    { key: 'budget', label: 'Variable Kosten', value: plan.budgets, from: 6 },
    { key: 'saving', label: 'Sparen', value: plan.saving, from: 7 },
  ]
  const shown = parts.filter((part) => step >= part.from)
  const used = shown.reduce((sum, part) => sum + part.value, 0)
  const rest = plan.income - used
  const scale = Math.max(plan.income, used, 0.01)
  return (
    <div className="plan-fill" aria-label="Dein Einkommen im Plan">
      {/* Field test after 0.3.0: the income heads the bar; the legend is an even grid instead of a wrapping line */}
      <div className="plan-fill-head">
        <span>Einkommen im Plan</span>
        <strong>{formatEuro(plan.income)}</strong>
      </div>
      <div className="plan-fill-bar" aria-hidden="true">
        {shown
          .filter((part) => part.value > 0)
          .map((part) => (
            <span key={part.key} className={`fill-${part.key}`} style={{ width: `${(part.value / scale) * 100}%` }} />
          ))}
      </div>
      <div className="plan-fill-legend">
        {parts.map((part) =>
          step >= part.from ? (
            <span key={part.key} className={step === part.from ? 'now' : undefined}>
              <span>
                <i className={`fill-${part.key}`} aria-hidden="true" />
                {part.label}
              </span>{' '}
              <strong>{formatEuro(part.value)}</strong>
            </span>
          ) : (
            <span key={part.key} className="later">
              <span>
                <i aria-hidden="true" />
                {part.label}
              </span>{' '}
              <span className="tx-meta">kommt noch</span>
            </span>
          ),
        )}
        {rest < 0 ? (
          <span className="over">
            <span>
              <i className="fill-over" aria-hidden="true" />
              Überplant
            </span>{' '}
            <strong>{formatEuro(-rest)}</strong>
          </span>
        ) : (
          <span>
            <span>
              <i className="fill-rest" aria-hidden="true" />
              Noch frei
            </span>{' '}
            <strong>{formatEuro(rest)}</strong>
          </span>
        )}
      </div>
    </div>
  )
}

function GoalsStep() {
  const goals = useSavingGoals()
  const create = useCreateSavingGoal()
  const [open, setOpen] = useState(false)
  const list = goals.data ?? []
  const suggestions = GOAL_SUGGESTIONS.filter((s) => !list.some((g) => g.name.toLowerCase() === s.name.toLowerCase()))
  return (
    <>
      <Lead step={8} title="Hast du Sparziele?" info="Wofür du sparst. Freiwillig.">
        Ein Sparziel ist das, wofür du sparst, z. B. ein Notgroschen oder ein Urlaub. Sparziele sind freiwillig und unabhängig von
        deinen Sparraten. Bleibt am Ende eines Monats Geld übrig, kannst du es einem Sparziel zuordnen und siehst, wie weit du bist.
      </Lead>
      <section className="card plan-group" aria-label="Deine Sparziele">
        <div className="plan-group-head">
          <h2>Deine Sparziele</h2>
        </div>
        {list.length === 0 ? (
          <p className="hint">Noch kein Sparziel. Leg ein eigenes an oder übernimm einen Vorschlag.</p>
        ) : (
          <ul className="item-list">
            {list.map((g) => (
              <li key={g.id} className="suggestion-row">
                <span className="item-main">
                  <span className="item-name">{g.name}</span>
                </span>
                <span className="item-amount">{g.targetAmount !== null ? `Ziel ${formatEuro(g.targetAmount)}` : 'ohne Zielbetrag'}</span>
              </li>
            ))}
          </ul>
        )}
        <div>
          <button type="button" className="primary" onClick={() => setOpen(true)}>
            + Eigenes Sparziel anlegen
          </button>
        </div>
      </section>
      {suggestions.length > 0 && (
        <section className="card plan-suggestions" aria-label="Vorschläge">
          <h3>Vorschläge</h3>
          <p className="tx-meta">Häufige Sparziele. Ein Klick legt sie an; Name und Betrag kannst du auf der Seite Konten ändern.</p>
          <ul className="item-list">
            {suggestions.map((s) => (
              <li key={s.name} className="suggestion-row">
                <span className="item-main">
                  <span className="item-name">{s.name}</span>
                </span>
                <span className="item-amount">{s.target !== null ? `Ziel ${formatEuro(s.target)}` : 'ohne Zielbetrag'}</span>
                <span className="suggestion-actions">
                  <button
                    type="button"
                    className="link-button"
                    disabled={create.isPending}
                    onClick={() => create.mutate({ name: s.name, targetAmount: s.target })}
                  >
                    Übernehmen
                  </button>
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
      {open && <SavingGoalDialog goal={null} onClose={() => setOpen(false)} />}
    </>
  )
}

function DoneStep({ me, plan, accounts, goals }: { me: Me; plan: Plan; accounts: AccountLine[]; goals: string[] }) {
  const stage = me.istStage
  // Decision 88: the month as a timeline with three stations, half a sentence each
  const routine: [ReactNode, string, string][] = [
    [
      <svg key="start" {...ICON_SVG}>{ICONS.receipt.path}</svg>,
      'Am Monatsanfang',
      stage === 1 ? 'Nichts zu tun.' : stage === 2 ? 'Kontostand eintragen, die Glocke erinnert dich.' : 'Rest des Vormonats ansehen.',
    ],
    [
      <Icon key="month" name="repeat" size={18} />,
      'Im Monat',
      stage === 1
        ? 'Nichts zu tun.'
        : stage === 2
          ? 'Sparraten abhaken, sonst nichts.'
          : stage === 3
            ? 'Ausgaben erfassen oder importieren.'
            : 'Ausgaben erfassen und zuordnen.',
    ],
    [<Icon key="change" name="planning" size={18} />, 'Wenn sich etwas ändert', 'Posten in der Planung anpassen.'],
  ]
  const byGroup = (group: PlanGroup) => plan.items.filter((item) => item.group === group)
  return (
    <>
      <Lead title="Fertig! So läuft dein Monat.">
        Du hast Stufe {stage} gewählt: {stageName(stage)}.
      </Lead>
      <ol className="card setup-timeline" aria-label="So läuft dein Monat">
        {routine.map(([icon, when, what]) => (
          <li key={when}>
            <span className="station-icon" aria-hidden="true">
              {icon}
            </span>
            <strong>{when}</strong>
            <span>{what}</span>
          </li>
        ))}
      </ol>
      <section className="card" aria-label="Dein Monat im Plan">
        <div className="plan-group-head">
          <h2>Dein Monat im Plan</h2>
          {plan.savingRate !== null && <span className="tx-meta">Sparquote {plan.savingRate.toLocaleString('de-DE')} %</span>}
        </div>
        <PlanSankey plan={plan} />
        {plan.unplanned < 0 && <p className="warn-text">Überplant um {formatEuro(-plan.unplanned)}. Senke Fixkosten, Budgets oder Sparraten.</p>}
        <p className="tx-meta">Seltene Fixkosten zählen mit ihrem Monatsanteil (Rücklage).</p>
      </section>
      <div className="setup-done-title">
        <h2>Deine Einrichtung</h2>
        <p className="tx-meta">Mit „ändern“ springst du zu dem Schritt zurück.</p>
      </div>
      <div className="setup-done-grid">
        <DoneCard step={1} title="Tracking-Stufe" wide>
          <p>
            <strong>
              Stufe {stage}: {stageName(stage)}
            </strong>
          </p>
          <p className="tx-meta">{CHECKS.slice(0, stage).map((check) => `✓ ${check}`).join(' · ')}</p>
        </DoneCard>
        <DoneCard step={3} title="Konten">
          <DoneList rows={accounts.map((a) => [a.name, a.statementDate ? formatEuro(a.balance) : 'kein Stand'])} empty="Kein Konto." />
        </DoneCard>
        <DoneCard step={4} title="Einkommen">
          <DoneList rows={byGroup('INCOME').map((i) => [i.name, formatEuro(i.monthlyShare)])} empty="Kein Einkommen." />
        </DoneCard>
        <DoneCard step={5} title="Fixkosten">
          <p className="tx-meta">
            {byGroup('FIXED').length} Posten, {formatEuro(plan.fixed)} im Monat
          </p>
          <DoneList rows={byGroup('FIXED').map((i) => [i.name, formatEuro(i.monthlyShare)])} empty="Keine Fixkosten." />
        </DoneCard>
        <DoneCard step={6} title="Variable Kosten (Budgets)">
          <p className="tx-meta">{formatEuro(plan.budgets)} im Monat</p>
          <DoneList
            rows={plan.categories.filter((c) => c.group === 'BUDGET').map((c) => [c.categoryName, formatEuro(c.total)])}
            empty="Keine Budgets."
          />
        </DoneCard>
        <DoneCard step={7} title="Sparen">
          <DoneList rows={byGroup('SAVING').map((i) => [i.name, formatEuro(i.monthlyShare)])} empty="Keine Sparrate geplant." />
        </DoneCard>
        <DoneCard step={8} title="Sparziele">
          <DoneList rows={goals.map((g) => [g, ''])} empty="Keine Sparziele. Du kannst jederzeit welche anlegen." />
        </DoneCard>
      </div>
    </>
  )
}

function DoneCard({ step, title, wide = false, children }: { step: number; title: string; wide?: boolean; children: ReactNode }) {
  return (
    <section className={wide ? 'card setup-done-card wide' : 'card setup-done-card'} aria-label={title}>
      <div className="plan-group-head">
        <h3>{title}</h3>
        <a href={`${ROUTES.setup}/${step}`} aria-label={`${title} ändern`}>
          ändern
        </a>
      </div>
      {children}
    </section>
  )
}

function DoneList({ rows, empty, max = 4 }: { rows: [string, string][]; empty: string; max?: number }) {
  if (rows.length === 0) {
    return <p className="tx-meta">{empty}</p>
  }
  return (
    <ul className="setup-done-list">
      {rows.slice(0, max).map(([label, value], i) => (
        <li key={`${label}-${i}`}>
          <span>{label}</span>
          <span>{value}</span>
        </li>
      ))}
      {rows.length > max && <li className="tx-meta">und {rows.length - max} weitere</li>}
    </ul>
  )
}
