import { createContext, useContext, useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { Icon } from './Icon'

interface Props {
  title: string
  onClose: () => void
  children: ReactNode
}

const FOCUSABLE = 'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea, [href]'

/** The titles of the dialogs this one lies on (decision 66). */
const DialogStack = createContext<string[]>([])

/**
 * Modal dialog; on phones a sheet from the bottom (US27). Escape and the close button close it, Tab stays inside,
 * and the focus goes back to where it was when the dialog closes. It lies in the body, so a dialog opened from another
 * one (decision 66, e.g. "Neue Kategorie" from the booking) lies above it: it names the dialogs below
 * ("Neue Buchung ›") and sits a bit lower, so the one below still shows.
 */
export function Dialog({ title, onClose, children }: Props) {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)
  const close = useRef(onClose)
  const below = useContext(DialogStack)
  const stacked = below.length > 0

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
    // The dialog below must not react to keys of this one
    event.stopPropagation()
    if (event.key === 'Escape') {
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

  return createPortal(
    <DialogStack.Provider value={[...below, title]}>
      <div
        className={stacked ? 'overlay stacked' : 'overlay'}
        onMouseDown={(event) => {
          event.stopPropagation()
          // A click on the dimmed background closes, a click inside does not
          if (event.target === event.currentTarget) {
            close.current()
          }
        }}
        // React passes events up the component tree, not the page: a form here must not submit the dialog below
        onSubmit={(event) => event.stopPropagation()}
        onClick={(event) => event.stopPropagation()}
      >
        <div
          ref={panel}
          className={stacked ? 'dialog stacked' : 'dialog'}
          role="dialog"
          aria-modal="true"
          aria-labelledby={titleId}
          onKeyDown={handleKeyDown}
        >
          {/* Stays at the top while the content scrolls, so the title and the close button are always in reach */}
          <div className="dialog-top">
            <div className="grabber" aria-hidden="true" />
            {stacked && (
              <p className="dialog-trail">
                <Icon name="layers" size={14} />
                <span>über {below.join(' › ')}</span>
              </p>
            )}
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
    </DialogStack.Provider>,
    document.body,
  )
}
