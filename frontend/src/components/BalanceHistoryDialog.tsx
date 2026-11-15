import { useState } from 'react'
import { useBalanceHistory, useDeleteBalance } from '../api/accounts'
import { errorText } from '../api/client'
import type { BalanceLine } from '../api/types'
import { formatDate, formatEuro, formatSigned } from '../format'
import { Dialog } from './ui/Dialog'
import { Loading } from './ui/Loading'

/**
 * Ticket #135 (decision 68): the balances from the bank of one account, newest first, each with the difference to
 * the app balance on its day (P11). A wrong one can only be deleted; a new one is entered in the window of the account.
 */
export function BalanceHistoryDialog({
  accountId,
  accountName,
  onClose,
}: {
  accountId: number
  accountName: string
  onClose: () => void
}) {
  const history = useBalanceHistory(accountId)
  const remove = useDeleteBalance()
  const [confirming, setConfirming] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleDelete(line: BalanceLine) {
    setError(null)
    try {
      await remove.mutateAsync({ accountId, balanceId: line.id })
      setConfirming(null)
    } catch (e) {
      setError(errorText(e, 'Löschen fehlgeschlagen.'))
    }
  }

  const lines = history.data ?? []
  return (
    <Dialog title={`Kontostände von ${accountName}`} onClose={onClose}>
      <p className="hint">
        Die Differenz zeigt, wie weit die App am Stichtag vom Kontostand der Bank abwich. Ein falscher Eintrag lässt sich
        löschen; danach rechnet die App wieder ab dem Kontostand davor.
      </p>
      {history.isPending ? (
        <Loading text="Kontostände werden geladen …" />
      ) : history.isError ? (
        <p className="error" role="alert">
          {errorText(history.error, 'Die Kontostände konnten nicht geladen werden.')}
        </p>
      ) : lines.length === 0 ? (
        <p className="hint">Noch kein Kontostand eingetragen.</p>
      ) : (
        <ul className="item-list" aria-label="Kontostände">
          {lines.map((line) => (
            <li key={line.id} className="account-row balance-row">
              <span className="item-main">
                <span className="item-name">{formatDate(line.date)}</span>
                <span className="tx-meta">
                  {line.difference === 0
                    ? '✓ stimmte mit der App überein'
                    : `App ${formatEuro(line.appBalance)} · Differenz ${formatSigned(line.difference)}`}
                </span>
              </span>
              <span className="item-amount">{formatEuro(line.amount)}</span>
              {confirming === line.id ? (
                <span className="dialog-buttons" role="group" aria-label="Löschen bestätigen">
                  <button type="button" onClick={() => setConfirming(null)} disabled={remove.isPending}>
                    Abbrechen
                  </button>
                  <button
                    type="button"
                    className="danger"
                    onClick={() => void handleDelete(line)}
                    disabled={remove.isPending}
                  >
                    Löschen
                  </button>
                </span>
              ) : (
                <button
                  type="button"
                  className="danger"
                  aria-label={`Kontostand vom ${formatDate(line.date)} löschen`}
                  onClick={() => setConfirming(line.id)}
                  disabled={remove.isPending}
                >
                  Löschen
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <div className="dialog-actions">
        <span />
        <div className="dialog-buttons">
          <button type="button" onClick={onClose}>
            Schließen
          </button>
        </div>
      </div>
    </Dialog>
  )
}
