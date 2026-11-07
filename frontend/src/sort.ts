// Display order of selection lists only, no business rules.

const collator = new Intl.Collator('de-DE', { sensitivity: 'base', numeric: true })

/** Alphabetically by name, ignoring case and accents ("Ärzte" next to "Arzt"), for category selections. */
export function byName<T extends { name: string }>(items: readonly T[]): T[] {
  return [...items].sort((a, b) => collator.compare(a.name, b.name))
}
