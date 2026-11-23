import { formatEuro } from '../format'

/** Field test after 0.3.0: after "Als Puffer in Sonstiges" the step says what happened and can be taken back. */
export function BufferDone({ amount, busy, onUndo }: { amount: number; busy: boolean; onUndo: () => void }) {
  return (
    <p className="plan-done buffer-done" role="status">
      ✓ {formatEuro(amount)} als Puffer in „Sonstiges“.{' '}
      <button type="button" className="link-button" onClick={onUndo} disabled={busy}>
        Rückgängig
      </button>
    </p>
  )
}
