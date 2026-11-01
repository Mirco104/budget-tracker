import { useState } from 'react'
import { errorText } from '../api/client'
import { useCreateInvitation } from '../api/invitations'
import type { Invitation } from '../api/types'
import { formatDateTime } from '../format'

/** Opens the admin's own mail program with the link; the app itself sends no e-mail (decision 19). */
function mailtoLink(invitation: Invitation, senderName: string): string {
  const subject = 'Einladung zum Budget-Tracker'
  const body = [
    'Hallo,',
    '',
    'ich lade dich zum Budget-Tracker ein. Über diesen Link legst du dein Konto an:',
    invitation.link,
    '',
    `Der Link ist einmal nutzbar und gültig bis ${formatDateTime(invitation.expiresAt)}.`,
    '',
    'Viele Grüße',
    senderName,
  ].join('\n')
  return `mailto:?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`
}

/** Settings › Einladungen, only for admins (US15). The app sends no e-mail; the admin passes the link on. */
export function InvitationSettings({ senderName }: { senderName: string }) {
  const create = useCreateInvitation()
  const [invitation, setInvitation] = useState<Invitation | null>(null)
  const [copied, setCopied] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleCreate() {
    setError(null)
    setCopied(false)
    try {
      setInvitation(await create.mutateAsync())
    } catch (e) {
      setError(errorText(e, 'Der Link konnte nicht erzeugt werden.'))
    }
  }

  async function handleCopy(link: string) {
    try {
      await navigator.clipboard.writeText(link)
      setCopied(true)
    } catch {
      setError('Kopieren nicht möglich. Bitte den Link markieren und kopieren.')
    }
  }

  return (
    <section>
      <h3>Einladungen</h3>
      <p className="hint">
        Ein Link ist einmal nutzbar und 7 Tage gültig. Schicke ihn der Person selbst, per Messenger oder mit
        deinem Mailprogramm.
      </p>
      <button type="button" disabled={create.isPending} onClick={() => void handleCreate()}>
        Einladungslink erzeugen
      </button>
      {invitation && (
        <div className="invitation-link">
          <input aria-label="Einladungslink" readOnly value={invitation.link} onFocus={(e) => e.target.select()} />
          <button type="button" onClick={() => void handleCopy(invitation.link)}>
            {copied ? 'Kopiert' : 'Kopieren'}
          </button>
          <a className="button-link" href={mailtoLink(invitation, senderName)}>
            Per E-Mail senden
          </a>
          <span className="hint">Gültig bis {formatDateTime(invitation.expiresAt)}</span>
        </div>
      )}
      {error && <p className="error">{error}</p>}
    </section>
  )
}
