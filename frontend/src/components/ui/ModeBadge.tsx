import { useEffect } from 'react'
import { useMode } from '../../api/health'

const LABELS: Record<string, string> = {
  dev: 'Entwicklung (dev)',
  sample: 'Beispieldaten (sample)',
}

/**
 * Ticket #82: names the local mode next to the brand and in the tab title, so a test database is not mistaken for the
 * real one. Production has no mode and shows nothing.
 */
export function ModeBadge() {
  const mode = useMode()

  useEffect(() => {
    if (mode === null) {
      return
    }
    const title = document.title
    document.title = `[${mode}] ${title}`
    return () => {
      document.title = title
    }
  }, [mode])

  if (mode === null) {
    return null
  }
  return (
    <span className="mode-badge" title="Lokaler Modus: Diese Daten sind nicht die echten.">
      <span className="mode-long">{LABELS[mode] ?? mode}</span>
      <span className="mode-short">{mode}</span>
    </span>
  )
}
