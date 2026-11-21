import { describe, expect, it } from 'vitest'
import { APP_CHANGELOG, APP_VERSION, changelogSections } from './version'

describe('changelogSections (ticket #159)', () => {
  it('reads the sections with their text and points', () => {
    const sections = changelogSections(
      ['# Änderungen', '', 'Vorwort', '', '## 0.2.0-alpha (01.12.2026)', 'Kurz gesagt.', '', '- **Eins:** erstens', '- Zwei', '', '## 0.1.0-alpha', '- Drei'].join('\r\n'),
    )
    expect(sections).toEqual([
      { title: '0.2.0-alpha (01.12.2026)', paragraphs: ['Kurz gesagt.'], items: ['**Eins:** erstens', 'Zwei'] },
      { title: '0.1.0-alpha', paragraphs: [], items: ['Drei'] },
    ])
  })

  it('has a section for the version of the app', () => {
    expect(changelogSections(APP_CHANGELOG).some((s) => s.title.split(' ')[0] === APP_VERSION)).toBe(true)
  })
})
