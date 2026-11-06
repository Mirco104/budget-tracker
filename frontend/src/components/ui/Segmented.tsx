interface Option<T extends string> {
  value: T
  label: string
  /** A disabled option cannot be chosen, e.g. % without a base (US2). */
  disabled?: boolean
}

interface Props<T extends string> {
  /** Name of the group for screen readers, e.g. "Einheit". */
  label: string
  options: Option<T>[]
  value: T
  onChange: (value: T) => void
  className?: string
}

/** A small switch between a few options (€ / %, Ausgabe / Einnahme); the chosen one is pressed. */
export function Segmented<T extends string>({ label, options, value, onChange, className }: Props<T>) {
  return (
    <div className={className ? `segmented ${className}` : 'segmented'} role="group" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          aria-pressed={option.value === value}
          disabled={option.disabled}
          onClick={() => {
            if (option.value !== value) {
              onChange(option.value)
            }
          }}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}
