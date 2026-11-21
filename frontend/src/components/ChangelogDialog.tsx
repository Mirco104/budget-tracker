import { Fragment, type ReactNode } from 'react'
import { APP_CHANGELOG, changelogSections } from '../version'
import { Dialog } from './ui/Dialog'

/** "**Bold:** text" as in CHANGELOG.md; other markdown is not used there. */
function inline(text: string): ReactNode {
  return text.split(/\*\*(.+?)\*\*/).map((part, index) =>
    index % 2 === 1 ? <strong key={index}>{part}</strong> : <Fragment key={index}>{part}</Fragment>,
  )
}

/** Ticket #159, decision 71: what changed in each version, from CHANGELOG.md, the newest first. */
export function ChangelogDialog({ onClose, changelog = APP_CHANGELOG }: { onClose: () => void; changelog?: string }) {
  const sections = changelogSections(changelog)
  return (
    <Dialog title="Änderungen" onClose={onClose}>
      <div className="changelog">
        {sections.map((section) => (
          <section key={section.title} aria-label={section.title}>
            <h3>{section.title}</h3>
            {section.paragraphs.map((text) => (
              <p key={text}>{inline(text)}</p>
            ))}
            {section.items.length > 0 && (
              <ul>
                {section.items.map((item) => (
                  <li key={item}>{inline(item)}</li>
                ))}
              </ul>
            )}
          </section>
        ))}
        {sections.length === 0 && <p className="hint">Noch keine Änderungen eingetragen.</p>}
      </div>
    </Dialog>
  )
}
