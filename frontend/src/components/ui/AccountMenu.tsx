import { useEffect, useRef, useState } from 'react'
import type { Me } from '../../api/types'
import { Icon } from './Icon'

interface Props {
  me: Me
  settingsHref: string
  onSignOut: () => void
  signingOut: boolean
}

/** "Person A" -> "PA", "Anna" -> "AN" */
function initials(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean)
  const letters = words.length > 1 ? words[0][0] + words[1][0] : (words[0] ?? '?').slice(0, 2)
  return letters.toUpperCase()
}

/** Avatar with a menu for the settings and signing out (US29). */
export function AccountMenu({ me, settingsHref, onSignOut, signingOut }: Props) {
  const [open, setOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) {
      return
    }
    function handlePointer(event: MouseEvent) {
      if (root.current && !root.current.contains(event.target as Node)) {
        setOpen(false)
      }
    }
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handlePointer)
    document.addEventListener('keydown', handleKey)
    return () => {
      document.removeEventListener('mousedown', handlePointer)
      document.removeEventListener('keydown', handleKey)
    }
  }, [open])

  return (
    <div className="account-menu" ref={root}>
      <button
        type="button"
        className="avatar"
        aria-label="Konto und Einstellungen"
        aria-expanded={open}
        aria-controls="account-menu"
        onClick={() => setOpen(!open)}
      >
        {initials(me.displayName)}
      </button>
      {open && (
        <div className="menu" id="account-menu">
          <div className="menu-head">
            <strong>{me.displayName}</strong>
            <span className="hint">{me.email}</span>
          </div>
          <a className="menu-item" href={settingsHref} onClick={() => setOpen(false)}>
            <Icon name="settings" />
            Einstellungen
          </a>
          <button type="button" className="menu-item danger" disabled={signingOut} onClick={onSignOut}>
            <Icon name="logout" />
            Abmelden
          </button>
        </div>
      )}
    </div>
  )
}
