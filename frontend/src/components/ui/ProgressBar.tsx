interface Props {
  /** Share of the target already used, 0..1; values above 1 fill the bar completely. */
  value: number
  /** Over the target: the bar turns to the warning color. */
  over?: boolean
}

/** A thin bar for "Ist von Soll"; decorative, the numbers stand next to it. */
export function ProgressBar({ value, over = false }: Props) {
  const width = Math.max(0, Math.min(1, value)) * 100
  return (
    <div className={`progress${over ? ' over' : ''}`} aria-hidden="true" data-testid="progress">
      <div className="progress-fill" style={{ width: `${width}%` }} />
    </div>
  )
}
