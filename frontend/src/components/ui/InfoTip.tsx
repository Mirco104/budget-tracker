import { type CSSProperties, type ReactNode, useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { Icon } from './Icon'

const BUBBLE_WIDTH = 280
const EDGE = 16
const GAP = 6
// Below this much room under the symbol the bubble opens above it
const ROOM_BELOW = 180

/**
 * Info symbol (?) with a short explanation (decision 37). It opens while the mouse is over it or it has the focus;
 * a click (or a tap on the phone) keeps it open until a second click, a click next to it or Escape.
 * The bubble is placed fixed to the window, so a table or card with overflow hidden does not cut it off.
 */
export function InfoTip({ label, children }: { label: string; children: ReactNode }) {
  const [shown, setShown] = useState(false)
  const [pinned, setPinned] = useState(false)
  const [style, setStyle] = useState<CSSProperties>({})
  const id = useId()
  const ref = useRef<HTMLSpanElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const open = shown || pinned

  function close() {
    setShown(false)
    setPinned(false)
  }

  useLayoutEffect(() => {
    if (!open || !buttonRef.current) {
      return
    }
    const rect = buttonRef.current.getBoundingClientRect()
    const viewportWidth = document.documentElement.clientWidth || window.innerWidth
    const width = Math.min(BUBBLE_WIDTH, viewportWidth - 2 * EDGE)
    const left = Math.max(EDGE, Math.min(rect.left, viewportWidth - EDGE - width))
    const above = window.innerHeight - rect.bottom < ROOM_BELOW && rect.top > ROOM_BELOW
    setStyle(above
      ? { left, width, bottom: window.innerHeight - rect.top + GAP }
      : { left, width, top: rect.bottom + GAP })
  }, [open])

  useEffect(() => {
    if (!open) {
      return
    }
    function onPointerDown(event: PointerEvent) {
      if (!ref.current?.contains(event.target as Node)) {
        close()
      }
    }
    // A fixed bubble would stay behind while the page scrolls
    function onScroll() {
      close()
    }
    document.addEventListener('pointerdown', onPointerDown)
    window.addEventListener('scroll', onScroll, true)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      window.removeEventListener('scroll', onScroll, true)
    }
  }, [open])

  return (
    <span
      className="info-tip"
      ref={ref}
      onMouseEnter={() => setShown(true)}
      onMouseLeave={() => setShown(false)}
    >
      <button
        ref={buttonRef}
        type="button"
        className="info-tip-button"
        aria-label={`Erklärung: ${label}`}
        aria-expanded={open}
        aria-describedby={open ? id : undefined}
        onFocus={() => setShown(true)}
        onBlur={() => setShown(false)}
        onClick={() => (pinned ? close() : setPinned(true))}
        onKeyDown={(event) => {
          if (event.key === 'Escape') {
            close()
          }
        }}
      >
        <Icon name="info" size={16} />
      </button>
      {open && (
        <span role="tooltip" id={id} className="info-tip-bubble" style={style}>
          {children}
        </span>
      )}
    </span>
  )
}
