/**
 * R37, stage 1–3: instead of the category the kind of a booking: without category (counts as income or expense),
 * transfer (counts nowhere) or Sparen. The values match the category field ('' and the values of transfer and saving).
 */
export function KindSelect({
  value,
  onChange,
  transferValue,
  savingValue,
  ariaLabel,
  labelledBy,
  disabled = false,
  withKinds = true,
}: {
  value: string
  onChange: (value: string) => void
  transferValue: string
  savingValue: string
  ariaLabel?: string
  labelledBy?: string
  disabled?: boolean
  /** False when a booking cannot be a transfer, e.g. one that fulfils a term. */
  withKinds?: boolean
}) {
  const current = value === transferValue || value === savingValue ? value : ''
  return (
    <select
      aria-label={ariaLabel}
      aria-labelledby={labelledBy}
      value={current}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
    >
      <option value="">Einnahme oder Ausgabe</option>
      {withKinds && <option value={transferValue}>Umbuchung (zählt nicht)</option>}
      {withKinds && <option value={savingValue}>Sparen (zählt als Sparen)</option>}
    </select>
  )
}
