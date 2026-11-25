/** Ticket #138: "Alle aufklappen" or, once every row is open, "Alle zuklappen"; only for at least two rows. */
export function ToggleAll({ count, allOpen, onToggle }: { count: number; allOpen: boolean; onToggle: () => void }) {
  if (count < 2) {
    return null
  }
  return (
    <div className="toggle-all">
      <button type="button" className="link-button" onClick={onToggle}>
        {allOpen ? 'Alle zuklappen' : 'Alle aufklappen'}
      </button>
    </div>
  )
}
