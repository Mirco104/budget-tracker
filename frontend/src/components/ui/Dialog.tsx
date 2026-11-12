import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { Icon } from './Icon'

interface Props {
  title: string
  onClose: () => void
  children: ReactNode
}

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea, [href]'

/**
 * Modal dialog; on phones a sheet from the bottom (US27). Escape and the close button close it, Tab stays inside,
 * and the focus goes back to where it was when the dialog closes.
 */
export function Dialog({ title, onClose, children }: Props) {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)
  const close = useRef(onClose)

  useEffect(() => {
    close.current = onClose
  }, [onClose])

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    // The first field, not the close button, so typing can start right away
    const first = panel.current?.querySelector<HTMLElement>(
      'input:not([type="radio"]):not([disabled]), select, textarea',
    )
    first?.focus()
    return () => opener?.focus?.()
  }, [])

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape') {
      event.stopPropagation()
      close.current()
      return
    }
    if (event.key !== 'Tab' || !panel.current) {
      return
    }
    const items = Array.from(panel.current.querySelectorAll<HTMLElement>(FOCUSABLE))
    if (items.length === 0) {
      return
    }
    const first = items[0]
    const last = items[items.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <div
      className="overlay"
      onMouseDown={(event) => {
        // A click on the dimmed background closes, a click inside does not
        if (event.target === event.currentTarget) {
          close.current()
        }
      }}
    >
      <div
        ref={panel}
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onKeyDown={handleKeyDown}
      >
        {/* Stays at the top while the content scrolls, so the title and the close button are always in reach */}
        <div className="dialog-top">
          <div className="grabber" aria-hidden="true" />
          <div className="dialog-head">
            <h2 id={titleId}>{title}</h2>
            <button type="button" className="icon-button" aria-label="Schließen" onClick={() => close.current()}>
              <Icon name="close" />
            </button>
          </div>
        </div>
        {children}
      </div>
    </div>
  )
}
