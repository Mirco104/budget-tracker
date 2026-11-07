import { useEffect, useRef, type ReactNode } from 'react'

/** The "⋯" menu of a settings row; focuses its first item and closes on Escape and on a click outside. */
export function RowMenu({ label, onClose, children }: { label: string; onClose: () => void; children: ReactNode }) {
  const root = useRef<HTMLDivElement>(null)
  const close = useRef(onClose)

  useEffect(() => {
    close.current = onClose
  }, [onClose])

  useEffect(() => {
    root.current?.querySelector<HTMLElement>('button:not([disabled])')?.focus()
    function handlePointer(event: MouseEvent) {
      if (root.current && !root.current.contains(event.target as Node)) {
        close.current()
      }
    }
    document.addEventListener('mousedown', handlePointer)
    return () => document.removeEventListener('mousedown', handlePointer)
  }, [])

  return (
    <div
      ref={root}
      className="menu row-menu"
      role="group"
      aria-label={label}
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.stopPropagation()
          close.current()
        }
      }}
    >
      {children}
    </div>
  )
}
