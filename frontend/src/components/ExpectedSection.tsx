import { useEffect, useRef, useState } from 'react'
import { errorText } from '../api/client'
import { usePayTerm, useSkipTerm, useTerms, useUnpayTerm, useUnskipTerm } from '../api/terms'
import type { Category, Term } from '../api/types'
import { formatDate, formatEuro, formatSigned } from '../format'
import { BookingDialog } from './BookingDialog'
import { Dialog } from './ui/Dialog'
import { Toast } from './ui/Toast'

/** "03.11." */
function formatDayMonth(isoDate: string): string {
  return `${isoDate.slice(8, 10)}.${isoDate.slice(5, 7)}.`
}

interface Props {
  /** First and last day of the month whose terms are shown (ISO dates). */
  from: string
  to: string
  categories: Category[]
  /** Scroll the section into view, e.g. for "#/transaktionen/erwartet". */
  focus?: boolean
}

/**
 * Concept 4.2: what is still expected this month. Shown are terms that have no booking yet, ticked-off ones and
 * skipped ones; terms that a booking already fulfils are in the list above. An automatic item says when it is
 * entered (P6); the others have a circle to tick off (P7), a tick to undo, and a tap opens the choices.
 */
export function ExpectedSection({ from, to, categories, focus = false }: Props) {
  const terms = useTerms(from, to)
  const pay = usePayTerm()
  const unpay = useUnpayTerm()
  const skip = useSkipTerm()
  const unskip = useUnskipTerm()
  const [chosen, setChosen] = useState<Term | null>(null)
  const [other, setOther] = useState<Term | null>(null)
  const [message, setMessage] = useState<{ text: string; undo?: () => void } | null>(null)
  const [error, setError] = useState<string | null>(null)
  const ref = useRef<HTMLElement>(null)

  useEffect(() => {
    if (focus && terms.data) {
      ref.current?.scrollIntoView?.({ block: 'start' })
    }
  }, [focus, terms.data])

  if (!terms.data) {
    return terms.error ? <p className="error">Erwartete Zahlungen konnten nicht geladen werden.</p> : null
  }
  const shown = terms.data.filter(
    (term) =>
      term.status === 'EXPECTED' ||
      term.status === 'MISSED' ||
      term.status === 'SKIPPED' ||
      (term.status === 'FULFILLED' && term.linked && term.entryMode !== 'AUTO'),
  )

  async function act(action: () => Promise<unknown>, text: string, undo?: () => void) {
    setError(null)
    try {
      await action()
      setChosen(null)
      setMessage({ text, undo })
    } catch (e) {
      setError(errorText(e, 'Das hat nicht geklappt.'))
    }
  }

  const ref_ = (term: Term) => ({ itemId: term.itemId, date: term.date })
  const payTerm = (term: Term) =>
    act(() => pay.mutateAsync(ref_(term)), `„${term.name}“ als bezahlt eingetragen.`, () =>
      void act(() => unpay.mutateAsync(ref_(term)), 'Abhaken rückgängig gemacht.'),
    )
  const unpayTerm = (term: Term) => act(() => unpay.mutateAsync(ref_(term)), 'Abhaken rückgängig gemacht.')
  const skipTerm = (term: Term) =>
    act(() => skip.mutateAsync(ref_(term)), `„${term.name}“ fällt diesen Monat weg.`, () =>
      void act(() => unskip.mutateAsync(ref_(term)), 'Wieder erwartet.'),
    )

  return (
    <section className="card expected" aria-label="Erwartet" ref={ref} id="erwartet">
      <h2>Erwartet bis {formatDate(to)}</h2>
      {shown.length === 0 && <p className="hint">Nichts mehr offen in diesem Monat.</p>}
      <ul className="expected-list">
        {shown.map((term) => {
          const paid = term.status === 'FULFILLED'
          const auto = term.entryMode === 'AUTO'
          return (
            <li key={`${term.itemKey}-${term.date}`} className={`expected-row ${term.status.toLowerCase()}`}>
              {!auto && term.status !== 'SKIPPED' && (
                <button
                  type="button"
                  className={paid ? 'tick done' : 'tick'}
                  aria-label={paid ? `Abhaken von ${term.name} rückgängig machen` : `${term.name} abhaken`}
                  aria-pressed={paid}
                  onClick={() => void (paid ? unpayTerm(term) : payTerm(term))}
                >
                  {paid ? '✓' : ''}
                </button>
              )}
              <button type="button" className="expected-main" onClick={() => setChosen(term)}>
                <span className="tx-description">{term.name}</span>
                <span className="tx-meta">
                  {term.status === 'SKIPPED'
                    ? 'fällt diesen Monat weg'
                    : paid
                      ? 'bezahlt'
                      : auto
                        ? `wird am ${formatDate(term.dueDate)} eingetragen`
                        : term.status === 'MISSED'
                          ? `Überfällig seit ${formatDayMonth(term.dueDate)}`
                          : `fällig am ${formatDate(term.dueDate)}`}
                </span>
              </button>
              <span className={term.amount > 0 ? 'tx-amount positive' : 'tx-amount'}>{formatSigned(term.amount)}</span>
            </li>
          )
        })}
      </ul>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {chosen && (
        <Dialog title={chosen.name} onClose={() => setChosen(null)}>
          <p className="hint">
            {formatEuro(Math.abs(chosen.amount))} · fällig am {formatDate(chosen.dueDate)}
          </p>
          <div className="term-choices">
            {chosen.status === 'FULFILLED' ? (
              <button type="button" onClick={() => void unpayTerm(chosen)}>
                Rückgängig: nicht bezahlt
              </button>
            ) : chosen.status === 'SKIPPED' ? (
              <button type="button" onClick={() => void act(() => unskip.mutateAsync(ref_(chosen)), 'Wieder erwartet.')}>
                Rückgängig: wieder erwarten
              </button>
            ) : (
              <>
                <button type="button" onClick={() => void payTerm(chosen)}>
                  Ist bezahlt: als Buchung eintragen
                </button>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => {
                    setOther(chosen)
                    setChosen(null)
                  }}
                >
                  Anderer Betrag oder anderes Datum
                </button>
                <button type="button" className="secondary" onClick={() => void skipTerm(chosen)}>
                  Diesen Monat fällt es weg
                </button>
              </>
            )}
          </div>
        </Dialog>
      )}
      {other && (
        <BookingDialog
          categories={categories}
          entry={null}
          defaultDate={other.dueDate}
          term={{
            itemId: other.itemId,
            date: other.date,
            name: other.name,
            amount: other.amount,
            categoryId: other.categoryId,
            accountId: other.accountId,
          }}
          onClose={() => setOther(null)}
          onDone={(text) => {
            setOther(null)
            setMessage({ text })
          }}
        />
      )}
      {message && (
        <div className="undo-line" role="status">
          <span>{message.text}</span>
          {message.undo && (
            <button
              type="button"
              className="link-button"
              onClick={() => {
                const undo = message.undo
                setMessage(null)
                undo?.()
              }}
            >
              Rückgängig
            </button>
          )}
        </div>
      )}
      {message && !message.undo && <Toast message={message.text} onDone={() => setMessage(null)} />}
    </section>
  )
}
