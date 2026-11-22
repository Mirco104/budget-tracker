import { useMemo, useState, type ReactNode } from 'react'
import { useChangeSetup, useChangeStage } from '../api/account'
import { useAccounts } from '../api/accounts'
import { useCategories, useCreateCategory } from '../api/categories'
import { errorText } from '../api/client'
import { useCreateSavingGoal, useSavingGoals } from '../api/goals'
import { useImports } from '../api/imports'
import { useDismissNotification } from '../api/notifications'
import { useBudgetSuggestions, useCreatePlannedItem, usePlan, usePlanSuggestions, useUpdatePlannedItem } from '../api/plan'
import type { AccountLine, Category, EntryMode, IstStage, Me, Plan, PlanGroup, PlannedItem } from '../api/types'
import { AccountDialog } from '../components/AccountDialog'
import { PlannedItemDialog } from '../components/PlannedItemDialog'
import { SavingGoalDialog } from '../components/SavingGoalDialog'
import { StageChoice } from '../components/StageChoice'
import { PlanSankey } from '../components/PlanSankey'
import { currentMonth, formatEuro } from '../format'
import { IMPORT_ROUTE, ROUTES, useHash } from '../router'
import { STAGES, stageName } from '../stages'
import { AccountRow } from './AccountsPage'
import { GroupSection } from './PlanningPage'

const STEPS = [
  'Start',
  'Ist verfolgen',
  'Konten',
  'Einkommen',
  'Fixkosten',
  'Variable Kosten (Budgets)',
  'Sparen',
  'Sparziele',
  'Fertig',
] as const
const LAST = STEPS.length - 1
const REACHED_KEY = 'setup-reached'
const SONSTIGES = 'Sonstiges'

/** A typical item of "Häufig dazu": name, example amount, category by name and its rhythm (display data only). */
interface Typical {
  name: string
  amount: number
  category: string
  day?: number
  rhythm?: string
}

const TYPICAL: Record<PlanGroup, Typical[]> = {
  INCOME: [
    { name: 'Gehalt', amount: 2800, category: 'Gehalt/Lohn (netto)', day: 28 },
    { name: 'Kindergeld', amount: 255, category: 'Staatliche Leistungen', day: 15 },
    { name: 'Nebenjob', amount: 450, category: 'Nebenjob', day: 1 },
  ],
  FIXED: [
    { name: 'Miete', amount: 850, category: 'Wohnen', day: 1 },
    { name: 'Strom', amount: 65, category: 'Nebenkosten', day: 5 },
    { name: 'Internet', amount: 35, category: 'Nebenkosten', day: 10 },
    { name: 'Handy', amount: 15, category: 'Nebenkosten', day: 15 },
    { name: 'Rundfunkbeitrag', amount: 55.08, category: 'Nebenkosten', day: 15, rhythm: 'MONTH:3' },
    { name: 'Haftpflichtversicherung', amount: 60, category: 'Versicherungen', day: 1, rhythm: 'MONTH:12' },
    { name: 'Hausratversicherung', amount: 90, category: 'Versicherungen', day: 1, rhythm: 'MONTH:12' },
    { name: 'Kfz-Versicherung', amount: 420, category: 'Versicherungen', day: 1, rhythm: 'MONTH:12' },
    { name: 'Deutschlandticket', amount: 58, category: 'Mobilität', day: 1 },
    { name: 'Streaming', amount: 13, category: 'Freizeit', day: 18 },
    { name: 'Fitnessstudio', amount: 30, category: 'Freizeit', day: 1 },
  ],
  BUDGET: [
    { name: 'Lebensmittel', amount: 350, category: 'Lebensmittel' },
    { name: 'Freizeit', amount: 120, category: 'Freizeit' },
    { name: 'Persönlicher Bedarf', amount: 60, category: 'Persönlicher Bedarf' },
    { name: 'Mobilität', amount: 60, category: 'Mobilität' },
    { name: 'Geschenke', amount: 25, category: 'Geschenke' },
    { name: SONSTIGES, amount: 80, category: SONSTIGES },
  ],
  SAVING: [
    { name: 'Notgroschen', amount: 100, category: '', day: 1 },
    { name: 'ETF-Sparplan', amount: 150, category: '', day: 1 },
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

/** Decision 83: income and fixed costs created in the wizard are entered automatically. */
function wizardEntry(group: PlanGroup): EntryMode | undefined {
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
  const imports = useImports()
  const changeSetup = useChangeSetup()
  const [path, setPath] = useState<'plan' | 'import' | null>(null)
  const [error, setError] = useState<string | null>(null)

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
  const imported = (imports.data?.length ?? 0) > 0

  function complete(at: number): boolean {
    if (at === 0) {
      return existing || imported || path === 'plan'
    }
    if (at === 2) {
      return (
        accountLines.length > 0 &&
        (me.istStage !== 2 || accountLines.filter((a) => a.role === 'EVERYDAY').every((a) => a.statementDate !== null))
      )
    }
    if (at === 3) {
      return (data?.income ?? 0) > 0
    }
    if (at === 4) {
      return (data?.items ?? []).some((item) => item.group === 'FIXED')
    }
    if (at === 5) {
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
        return <StartStep existing={existing} imported={imported} importCount={imports.data?.length ?? 0} path={path} onPath={setPath} />
      case 1:
        return <StageStep me={me} existing={existing} />
      case 2:
        return <AccountsStep me={me} accounts={accountLines} />
      case 3:
      case 4:
      case 5:
      case 6:
        return <PlanStep step={step} plan={data} categories={categories.data} month={month} accounts={accountLines} />
      case 7:
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
              {label}
            </a>
          ) : (
            <span key={label} className={i === step ? 'now' : i < step ? 'done' : undefined} aria-current={i === step ? 'step' : undefined}>
              {label}
            </span>
          ),
        )}
      </nav>
      {step > 0 && step < LAST && <p className="setup-count">Schritt {step} von {LAST - 1}</p>}
      {body}
      {!complete(step) && step > 0 && step < LAST && <p className="setup-missing">{missingText(step, me.istStage)}</p>}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="setup-foot">
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

function missingText(step: number, stage: IstStage): string {
  if (step === 2) {
    return stage === 2
      ? 'Trag für jedes Alltagskonto den Kontostand ein, um weiterzumachen.'
      : 'Leg mindestens ein Konto an, um weiterzumachen.'
  }
  if (step === 3) {
    return 'Trag mindestens ein Einkommen über 0 € ein, um weiterzumachen.'
  }
  if (step === 4) {
    return 'Trag mindestens eine Fixkosten-Zahlung ein, um weiterzumachen.'
  }
  return 'Trag mindestens ein Budget ein, um weiterzumachen.'
}

function Lead({ title, info, children }: { title: string; info?: string; children?: ReactNode }) {
  return (
    <div className="setup-lead">
      <h1>{title}</h1>
      {info && <p className="setup-info">{info}</p>}
      {children && <p className="tx-meta">{children}</p>}
    </div>
  )
}

function StartStep({
  existing,
  imported,
  importCount,
  path,
  onPath,
}: {
  existing: boolean
  imported: boolean
  importCount: number
  path: 'plan' | 'import' | null
  onPath: (path: 'plan' | 'import') => void
}) {
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
      <h2>Womit fängst du an?</h2>
      <div className="setup-choices">
        <button type="button" className={path === 'import' || imported ? 'setup-choice on' : 'setup-choice'} onClick={() => onPath('import')}>
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
          <span className="tx-meta">Du trägst deine Beträge selbst ein. Wir geben typische Posten vor.</span>
        </button>
      </div>
      {(path === 'import' || imported) && (
        <div className="card">
          {imported ? (
            <p>
              <strong>Kontoauszug gelesen</strong> ({importCount === 1 ? '1 Import' : `${importCount} Importe`}). Die Buchungen
              bleiben gespeichert, die Vorschläge siehst du in den nächsten Schritten.
            </p>
          ) : (
            <p className="tx-meta">
              Mindestens 3 Monate, besser 6 bis 12. C24, Sparkasse, Deutsche Bank, VR-Bank, N26, Trade Republic; mehrere
              Dateien und Konten sind möglich. Nach dem Import kommst du hierher zurück.
            </p>
          )}
          <div>
            <a className="button-link" href={IMPORT_ROUTE}>
              {imported ? 'Weitere Datei importieren' : 'Kontoauszug importieren'}
            </a>
          </div>
        </div>
      )}
    </>
  )
}

function SetupOutline() {
  return (
    <div className="card setup-outline">
      <h3>Das richten wir zusammen ein</h3>
      <dl>
        <dt>Ist verfolgen</dt>
        <dd>Wie genau du später nachsehen willst, was du wirklich ausgibst</dd>
        <dt>Konten</dt>
        <dd>Girokonto, Sparkonto, Depot …</dd>
        <dt>Einkommen</dt>
        <dd>Was jeden Monat reinkommt, z. B. das Gehalt</dd>
        <dt>Fixkosten</dt>
        <dd>Was regelmäßig abgeht, z. B. Miete, Strom, Versicherungen</dd>
        <dt>Variable Kosten (Budgets)</dt>
        <dd>Wie viel du für den Alltag einplanst, z. B. Lebensmittel</dd>
        <dt>Sparen</dt>
        <dd>Regelmäßige Sparraten und, wenn du willst, Sparziele</dd>
      </dl>
    </div>
  )
}

function StageStep({ me, existing }: { me: Me; existing: boolean }) {
  const changeStage = useChangeStage()
  const [error, setError] = useState<string | null>(null)
  return (
    <>
      <Lead title="Wie genau willst du verfolgen, was du ausgibst?" info="Davon hängt ab, was die App in den nächsten Schritten von dir braucht.">
        Du kannst die Stufe jederzeit unter Einstellungen › Ist verfolgen ändern. Ein Wechsel ändert nur die Anzeige, nie deine
        Daten.{existing ? ' Stufe 4 entspricht dem, wie du die App bisher genutzt hast.' : ''}
      </Lead>
      <StageChoice
        value={me.istStage}
        recommended={2}
        disabled={changeStage.isPending}
        onChange={(stage) => {
          setError(null)
          changeStage.mutate(stage, { onError: (e) => setError(errorText(e, 'Die Stufe konnte nicht gespeichert werden.')) })
        }}
      />
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
      <Lead title="Welche Konten hast du?" info="Konten sind die Orte, an denen dein Geld liegt.">
        Leg das Konto an, von dem du deinen Alltag bezahlst. Hast du ein Sparkonto, Tagesgeld oder Depot, leg es gleich mit an,
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
  3: {
    group: 'INCOME',
    title: 'Was kommt rein?',
    info: 'Das Geld, das jeden Monat reinkommt.',
    text: 'Dein Einkommen in einem normalen Monat. Klick auf einen Posten, um ihn zu ändern.',
  },
  4: {
    group: 'FIXED',
    title: 'Was geht fest ab?',
    info: 'Zahlungen, die regelmäßig in gleicher Höhe abgehen.',
    text: 'Seltene Zahlungen trägst du mit ihrem echten Betrag ein (z. B. 60 € jährlich); die App rechnet aus, wie viel das im Monat ist, und legt eine Rücklage an. Die App trägt Fixkosten an ihrem Tag selbst ein; kommt später ein Import, ersetzt die echte Buchung den Eintrag.',
  },
  5: {
    group: 'BUDGET',
    title: 'Wie viel für den Alltag?',
    info: 'Geld für Ausgaben, die jeden Monat anders sind.',
    text: 'Variable Kosten sind Ausgaben, die jeden Monat anders sind, z. B. Lebensmittel. Je Kategorie planst du einen oder mehrere Posten, z. B. unter Lebensmittel „Wocheneinkauf“ und „Wochenmarkt“.',
  },
  6: {
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
}: {
  step: number
  plan: Plan
  categories: Category[]
  month: string
  accounts: AccountLine[]
}) {
  const { group, title, info, text } = PLAN_STEPS[step]
  const suggestions = usePlanSuggestions()
  const budgetSuggestions = useBudgetSuggestions()
  const dismiss = useDismissNotification()
  const createCategory = useCreateCategory()
  const createItem = useCreatePlannedItem()
  const updateItem = useUpdatePlannedItem()
  const [dialog, setDialog] = useState<DialogState | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [typicalOpen, setTypicalOpen] = useState<boolean | null>(null)
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
          entryMode: wizardEntry(group),
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

  // P4 "Als Puffer in Sonstiges": the rest goes to the budget of the catch-all category
  async function toOther() {
    setError(null)
    try {
      const categoryId = (await categoryNamed(SONSTIGES)) as number
      const existing = plan.items.find((item) => item.group === 'BUDGET' && item.categoryId === categoryId)
      if (existing) {
        await updateItem.mutateAsync({
          id: existing.id,
          input: {
            name: existing.name,
            amount: -(existing.monthlyShare + plan.unplanned),
            categoryId,
            accountId: existing.accountId,
            rhythmUnit: null,
            rhythmCount: null,
            anchorDate: null,
            entryMode: existing.entryMode,
            validFrom: month,
          },
        })
      } else {
        await createItem.mutateAsync({
          name: SONSTIGES,
          amount: -plan.unplanned,
          categoryId,
          rhythmUnit: null,
          rhythmCount: null,
          anchorDate: null,
          validFrom: month,
        })
      }
    } catch (e) {
      setError(errorText(e, 'Speichern fehlgeschlagen.'))
    }
  }

  const names = new Set(plan.items.map((item) => item.name.trim().toLowerCase()))
  const typical = TYPICAL[group].filter((t) => !names.has(t.name.toLowerCase()))
  const groupSuggestions = (suggestions.data ?? []).filter((s) => s.group === group)
  const hasSuggestions = groupSuggestions.length > 0 || (group === 'BUDGET' && (budgetSuggestions.data?.length ?? 0) > 0)
  // With suggestions from the bookings the typical items stay folded until asked for
  const showTypical = typicalOpen ?? !hasSuggestions
  const iconOf = (id: number) => categories.find((c) => c.id === id)?.icon ?? 'tag'

  return (
    <>
      <Lead title={title} info={info}>
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
            <button type="button" onClick={() => void toOther()} disabled={createItem.isPending || updateItem.isPending}>
              Als Puffer in „Sonstiges“
            </button>
          </div>
        </div>
      )}
      <GroupSection
        title={title === 'Was kommt rein?' ? 'Einkommen' : STEPS[step]}
        hint={info}
        group={group}
        plan={plan}
        onEdit={(item) => setDialog({ item, group })}
        onAdd={() => setDialog({ item: null, group, preset: { entryMode: wizardEntry(group) } })}
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
              entryMode: wizardEntry(group),
            },
          })
        }
        onHideSuggestion={(s) => dismiss.mutate(s.key)}
        iconOf={iconOf}
      />
      {group === 'INCOME' && plan.averageIncome !== null && (
        <p className="hint">Schwankt ein Einkommen, plane im Zweifel mit dem kleineren Betrag. Dann bleibt am Monatsende eher etwas übrig, als dass etwas fehlt.</p>
      )}
      {group === 'BUDGET' && (budgetSuggestions.data?.length ?? 0) > 0 && (
        <section className="card plan-suggestions setup-suggestions" aria-label="Vorschläge aus deinen Buchungen">
          <h3>Vorschläge aus deinen Buchungen</h3>
          <p className="tx-meta">Dein Durchschnitt je Kategorie in den letzten Monaten. Was du übernimmst, wird ein Budget dieser Kategorie.</p>
          <ul className="item-list">
            {(budgetSuggestions.data ?? []).map((s) => (
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
            <ul className="item-list">
              {typical.map((t) => (
                <li key={t.name} className="suggestion-row">
                  <span className="item-main">
                    <span className="item-name">{t.name}</span>
                    <span className="tx-meta">
                      {t.rhythm ? RHYTHM_LABEL[t.rhythm] : group === 'BUDGET' ? 'ohne festen Termin' : `monatlich${t.day ? ` · am ${t.day}.` : ''}`}
                      {t.category ? ` · ${t.category}` : ''}
                    </span>
                  </span>
                  <span className="item-amount">
                    <span className="tx-meta">z. B. </span>
                    {formatEuro(t.amount)}
                  </span>
                  <span className="suggestion-actions">
                    <button type="button" className="link-button" onClick={() => void openTypical(t)} disabled={createCategory.isPending}>
                      Hinzufügen
                    </button>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      {step >= 4 && <SetupSum plan={plan} upto={step} />}
      {dialog && (
        <PlannedItemDialog
          categories={categories}
          item={dialog.item}
          month={month}
          group={dialog.group}
          preset={dialog.preset}
          onClose={() => setDialog(null)}
          onDone={() => {
            if (dialog.suggestionKey) {
              dismiss.mutate(dialog.suggestionKey)
            }
            setDialog(null)
          }}
        />
      )}
    </>
  )
}

/** The plan so far: Einkommen − Fixkosten − Budgets − Sparen, later steps "ausstehend" (US40). */
function SetupSum({ plan, upto }: { plan: Plan; upto: number }) {
  const pending = <span className="tx-meta">ausstehend</span>
  const rest = upto === 4 ? plan.income - plan.fixed : upto === 5 ? plan.income - plan.fixed - plan.budgets : plan.unplanned
  const label = rest < 0 ? 'Überplant' : upto === 4 ? 'Bleibt für variable Kosten und Sparen' : upto === 5 ? 'Bleibt fürs Sparen' : 'Nicht verplant'
  return (
    <dl className="card setup-sum" aria-label="Dein Plan bisher">
      <div>
        <dt>Einkommen</dt>
        <dd className="income">{formatEuro(plan.income)}</dd>
      </div>
      <div>
        <dt>− Fixkosten (Monatsanteile)</dt>
        <dd>{formatEuro(plan.fixed)}</dd>
      </div>
      <div>
        <dt>− Variable Kosten (Budgets)</dt>
        <dd>{upto >= 5 ? formatEuro(plan.budgets) : pending}</dd>
      </div>
      <div>
        <dt>
          − Sparen
          {upto >= 6 && plan.savingRate !== null && <span className="tx-meta"> (Sparquote {plan.savingRate.toLocaleString('de-DE')} %)</span>}
        </dt>
        <dd className="saving">{upto >= 6 ? formatEuro(plan.saving) : pending}</dd>
      </div>
      <div className={rest < 0 ? 'total over' : 'total'}>
        <dt>{label}</dt>
        <dd>{formatEuro(Math.abs(rest))}</dd>
      </div>
    </dl>
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
      <Lead title="Hast du Sparziele?" info="Wofür du sparst. Freiwillig.">
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
  const bell = 'in deinen Benachrichtigungen (Glocke oben rechts)'
  const rest = goals.length > 0 ? ' Bleibt etwas übrig, kannst du es dort einem Sparziel zuordnen.' : ''
  const routine: [string, string][] = [
    [
      'Am Monatsanfang',
      stage === 1
        ? 'Nichts. Die Übersicht zeigt den Plan des neuen Monats.'
        : stage === 2
          ? `Du trägst den Kontostand vom letzten Tag des Vormonats ein; eine Benachrichtigung dazu findest du ${bell}. Danach siehst du, was du variabel ausgegeben hast.${rest}`
          : `Was im Vormonat übrig geblieben ist, findest du ${bell}.${rest}`,
    ],
    [
      'Im Monat',
      stage === 1
        ? 'Nichts.'
        : stage === 2
          ? 'Nichts. Einkommen und Fixkosten trägt die App selbst ein. Deine Sparraten hakst du ab.'
          : stage === 3
            ? 'Ausgaben erfassen (Betrag und Text) oder ab und zu importieren. Fixkosten trägt die App selbst ein.'
            : 'Ausgaben erfassen oder importieren und einer Kategorie zuordnen. „Alle Vorschläge übernehmen“ erledigt das meist mit einem Klick.',
    ],
    ['Wenn sich etwas ändert', 'Planung öffnen und den Posten anpassen, z. B. eine neue Miete ab Januar.'],
  ]
  const byGroup = (group: PlanGroup) => plan.items.filter((item) => item.group === group)
  const info = STAGES[stage - 1]
  return (
    <>
      <Lead title="Fertig! So läuft dein Monat.">
        Du hast Stufe {stage} gewählt: {stageName(stage)}.
      </Lead>
      <div className="card setup-routine">
        <dl>
          {routine.map(([when, what]) => (
            <div key={when}>
              <dt>{when}</dt>
              <dd>{what}</dd>
            </div>
          ))}
        </dl>
      </div>
      <section className="card" aria-label="Dein Monat im Plan">
        <div className="plan-group-head">
          <h2>Dein Monat im Plan</h2>
          {plan.savingRate !== null && <span className="tx-meta">Sparquote {plan.savingRate.toLocaleString('de-DE')} %</span>}
        </div>
        <PlanSankey plan={plan} />
        {plan.unplanned < 0 && <p className="warn-text">Überplant um {formatEuro(-plan.unplanned)}. Senke Fixkosten, Budgets oder Sparraten.</p>}
        <p className="tx-meta">Seltene Fixkosten zählen mit ihrem Monatsanteil (Rücklage).</p>
      </section>
      <h2>Deine Einrichtung</h2>
      <p className="tx-meta">Mit „ändern“ springst du zu dem Schritt zurück.</p>
      <div className="setup-done-grid">
        <DoneCard step={1} title="Ist verfolgen" wide>
          <p>
            <strong>
              Stufe {stage}: {info.name}
            </strong>
          </p>
          <p className="tx-meta">
            Aufwand: <strong>{info.effort}</strong> · Genauigkeit: <strong>{info.accuracy}</strong>
          </p>
        </DoneCard>
        <DoneCard step={2} title="Konten">
          <DoneList rows={accounts.map((a) => [a.name, a.statementDate ? formatEuro(a.balance) : 'kein Stand'])} empty="Kein Konto." />
        </DoneCard>
        <DoneCard step={3} title="Einkommen">
          <DoneList rows={byGroup('INCOME').map((i) => [i.name, formatEuro(i.monthlyShare)])} empty="Kein Einkommen." />
        </DoneCard>
        <DoneCard step={4} title="Fixkosten">
          <p className="tx-meta">
            {byGroup('FIXED').length} Posten, {formatEuro(plan.fixed)} im Monat
          </p>
          <DoneList rows={byGroup('FIXED').map((i) => [i.name, formatEuro(i.monthlyShare)])} empty="Keine Fixkosten." />
        </DoneCard>
        <DoneCard step={5} title="Variable Kosten (Budgets)">
          <p className="tx-meta">{formatEuro(plan.budgets)} im Monat</p>
          <DoneList
            rows={plan.categories.filter((c) => c.group === 'BUDGET').map((c) => [c.categoryName, formatEuro(c.total)])}
            empty="Keine Budgets."
          />
        </DoneCard>
        <DoneCard step={6} title="Sparen">
          <DoneList rows={byGroup('SAVING').map((i) => [i.name, formatEuro(i.monthlyShare)])} empty="Keine Sparrate geplant." />
        </DoneCard>
        <DoneCard step={7} title="Sparziele">
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
