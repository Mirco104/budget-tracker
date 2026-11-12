import { useIsFetching, useIsMutating } from '@tanstack/react-query'

/**
 * Ticket #78: what a page shows while its data loads. The text is there at once for screen readers; the block only
 * fades in after a short moment (CSS), so a fast answer does not flicker.
 */
export function Loading({ text, inline = false }: { text: string; inline?: boolean }) {
  return (
    <p className={`loading${inline ? ' inline' : ''}`} role="status">
      <span className="spinner" aria-hidden="true" />
      {text}
    </p>
  )
}

/**
 * Ticket #78: a thin bar under the header while anything talks to the server (loading again, saving, an import). It
 * shows only when that takes longer than a moment (CSS delay).
 */
export function LoadingBar() {
  const fetching = useIsFetching()
  const mutating = useIsMutating()
  if (fetching + mutating === 0) {
    return null
  }
  return <div className="loading-bar" role="progressbar" aria-label="Lädt …" />
}
