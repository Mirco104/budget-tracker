import { useRef, useState, type ReactNode } from 'react'

/**
 * A link to pass on (invitation US15, password reset US36): a read-only field with "Kopieren". Further actions and
 * hints come as children next to it.
 */
export function CopyLink({ link, label, children }: { link: string; label: string; children?: ReactNode }) {
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const field = useRef<HTMLInputElement>(null)

  // Copies by selecting the link field; works without the clipboard API (plain HTTP, embedded browsers)
  function copyFromField(): boolean {
    const input = field.current
    if (!input) {
      return false
    }
    input.focus()
    input.select()
    try {
      return document.execCommand('copy')
    } catch {
      return false
    }
  }

  async function handleCopy() {
    setError(null)
    let copiedNow = false
    // The clipboard API only exists in a secure context (HTTPS or localhost) and may still be denied
    if (navigator.clipboard) {
      try {
        await navigator.clipboard.writeText(link)
        copiedNow = true
      } catch {
        copiedNow = false
      }
    }
    if (!copiedNow) {
      copiedNow = copyFromField()
    }
    setCopied(copiedNow)
    if (!copiedNow) {
      setError('Kopieren nicht möglich. Der Link ist markiert, bitte mit Strg+C bzw. lange tippen kopieren.')
    }
  }

  return (
    <>
      <div className="invitation-link">
        <input ref={field} aria-label={label} readOnly value={link} onFocus={(e) => e.target.select()} />
        <button type="button" onClick={() => void handleCopy()}>
          {copied ? 'Kopiert' : 'Kopieren'}
        </button>
        {children}
      </div>
      {error && <p className="error">{error}</p>}
    </>
  )
}
