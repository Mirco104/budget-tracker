import { Fragment, type ReactNode } from 'react'
import { APP_CHANGELOG, changelogSections } from '../version'
import { Dialog } from './ui/Dialog'

/** "**Bold:** text" and "*italic*" as in CHANGELOG.md; other markdown is not used there. */
function inline(text: string): ReactNode {
  return text.split(/(\*\*.+?\*\*|\*[^*]+?\*)/).map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**') && part.length > 4) {
      return <strong key={index}>{part.slice(2, -2)}</strong>
    }
    if (part.startsWith('*') && part.endsWith('*') && part.length > 2) {
      return <em key={index}>{part.slice(1, -1)}</em>
    }
    return <Fragment key={index}>{part}</Fragment>
  })
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
            {section.groups.map((group) => (
              <Fragment key={group.title ?? ''}>
                {group.title && <h4>{group.title}</h4>}
                <ul>
                  {group.items.map((item) => (
                    <li key={item}>{inline(item)}</li>
                  ))}
                </ul>
              </Fragment>
            ))}
          </section>
        ))}
        {sections.length === 0 && <p className="hint">Noch keine Änderungen eingetragen.</p>}
      </div>
    </Dialog>
  )
}
