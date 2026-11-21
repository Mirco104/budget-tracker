import { describe, expect, it } from 'vitest'
import { APP_CHANGELOG, APP_VERSION, changelogSections } from './version'

describe('changelogSections (ticket #159)', () => {
  it('reads the sections with their text and points', () => {
    const sections = changelogSections(
      ['# Änderungen', '', 'Vorwort', '', '## 0.2.0-alpha (01.12.2026)', 'Kurz gesagt.', '', '- **Eins:** erstens', '- Zwei', '', '## 0.1.0-alpha', '- Drei'].join('\r\n'),
    )
    expect(sections).toEqual([
      { title: '0.2.0-alpha (01.12.2026)', paragraphs: ['Kurz gesagt.'], groups: [{ title: null, items: ['**Eins:** erstens', 'Zwei'] }] },
      { title: '0.1.0-alpha', paragraphs: [], groups: [{ title: null, items: ['Drei'] }] },
    ])
  })

  it('reads the groups Neu and Änderungen of a section', () => {
    const sections = changelogSections(
      ['## 0.3.0-alpha', 'Kurz gesagt.', '', '### Neu', '', '- Eins', '- Zwei', '', '### Änderungen', '- Drei'].join('\n'),
    )
    expect(sections).toEqual([
      {
        title: '0.3.0-alpha',
        paragraphs: ['Kurz gesagt.'],
        groups: [
          { title: 'Neu', items: ['Eins', 'Zwei'] },
          { title: 'Änderungen', items: ['Drei'] },
        ],
      },
    ])
  })

  it('splits every section of CHANGELOG.md into Neu and Änderungen', () => {
    for (const section of changelogSections(APP_CHANGELOG)) {
      expect(section.groups.map((group) => group.title).every((title) => title === 'Neu' || title === 'Änderungen')).toBe(true)
      expect(section.groups.every((group) => group.items.length > 0)).toBe(true)
    }
  })

  it('has a section for the version of the app', () => {
    expect(changelogSections(APP_CHANGELOG).some((s) => s.title.split(' ')[0] === APP_VERSION)).toBe(true)
  })
})
