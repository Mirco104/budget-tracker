// Ticket #129: set by Vite at build time (vite.config.ts)
declare const __APP_VERSION__: string
declare const __APP_COMMIT__: string
// Ticket #159: CHANGELOG.md, set by Vite at build time
declare const __APP_CHANGELOG__: string

/** The version of the app, e.g. "0.1.0-alpha". */
export const APP_VERSION: string = __APP_VERSION__

/** The short commit of the build; empty when unknown. */
export const APP_COMMIT: string = __APP_COMMIT__

const STAGES: Record<string, string> = { alpha: 'Alpha', beta: 'Beta' }

/** The name of the pre-release stage of a version ("0.1.0-alpha" → "Alpha"); null for a release like "1.0.0". */
export function releaseStage(version: string): string | null {
  const stage = version.split('-')[1]?.split('.')[0]
  if (!stage) {
    return null
  }
  return STAGES[stage] ?? stage
}

/** Points of a section under a "### " heading ("Neu" or "Änderungen"); title null for points without heading. */
export interface ChangelogGroup {
  title: string | null
  items: string[]
}

/** Ticket #159: one section of the changelog, e.g. "0.1.0-alpha (15.11.2026)" with its text and its groups. */
export interface ChangelogSection {
  title: string
  paragraphs: string[]
  groups: ChangelogGroup[]
}

/** The changelog of the app as written in CHANGELOG.md. */
export const APP_CHANGELOG: string = __APP_CHANGELOG__

/**
 * Reads the sections of the changelog: each "## " heading starts one, "### " a group of points in it ("Neu",
 * "Änderungen"), "- " lines are the points, other lines its text; the title and text above the first section are left
 * out.
 */
export function changelogSections(markdown: string): ChangelogSection[] {
  const sections: ChangelogSection[] = []
  for (const raw of markdown.split(/\r?\n/)) {
    const line = raw.trim()
    if (line.startsWith('## ')) {
      sections.push({ title: line.slice(3).trim(), paragraphs: [], groups: [] })
      continue
    }
    const current = sections[sections.length - 1]
    if (!current || line === '') {
      continue
    }
    if (line.startsWith('### ')) {
      current.groups.push({ title: line.slice(4).trim(), items: [] })
    } else if (line.startsWith('- ')) {
      let group = current.groups[current.groups.length - 1]
      if (!group) {
        group = { title: null, items: [] }
        current.groups.push(group)
      }
      group.items.push(line.slice(2).trim())
    } else {
      current.paragraphs.push(line)
    }
  }
  return sections
}
