import { useState } from 'react'

/**
 * Ticket #138: which rows of a list grouped by category are open. The list holds it instead of each row, so one
 * button opens or closes them all; a row that leaves the list is forgotten.
 */
export function useOpenRows(keys: string[]) {
  const [open, setOpen] = useState<Set<string>>(() => new Set())
  const allOpen = keys.length > 0 && keys.every((key) => open.has(key))
  return {
    isOpen: (key: string) => open.has(key),
    toggle: (key: string) =>
      setOpen((current) => {
        const next = new Set(current)
        if (next.has(key)) {
          next.delete(key)
        } else {
          next.add(key)
        }
        return next
      }),
    allOpen,
    toggleAll: () => setOpen(allOpen ? new Set() : new Set(keys)),
  }
}
