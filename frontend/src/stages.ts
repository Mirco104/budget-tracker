import type { IstStage } from './api/types'
import type { Page } from './router'

/** R35: the four Ist stages as the setup wizard and the settings show them (display texts only). */
export const STAGES: {
  stage: IstStage
  name: string
  what: string
  get: string[]
  effort: string
  effortNote: string
  accuracy: string
  accuracyNote: string
}[] = [
  {
    stage: 1,
    name: 'Nur Plan',
    what: 'Du erfasst kein Ist.',
    get: ['Plan je Monat', 'Was nicht verplant ist', 'Sparquote und Rücklagen'],
    effort: 'Sehr gering',
    effortNote: 'nach der Einrichtung nichts mehr',
    accuracy: 'Keine',
    accuracyNote: 'kein Ist, nur der Plan',
  },
  {
    stage: 2,
    name: 'Kontostand',
    what: 'Einmal im Monat trägst du den Kontostand ein.',
    get: ['Fixkosten trägt die App selbst ein', 'Variable Ausgaben gesamt aus der Differenz', 'Ergebnis je Monat'],
    effort: 'Gering',
    effortNote: 'etwa 2 Minuten im Monat',
    accuracy: 'Gering',
    accuracyNote: 'variable Ausgaben nur als Summe je Monat',
  },
  {
    stage: 3,
    name: 'Buchungen',
    what: 'Du erfasst Ausgaben oder importierst sie, ohne Kategorie.',
    get: ['Alles aus Stufe 2, jederzeit aktuell', 'Liste aller Ausgaben', 'Variabel gesamt gegen die Summe der Budgets'],
    effort: 'Mittel',
    effortNote: 'laufend erfassen oder importieren',
    accuracy: 'Mittel',
    accuracyNote: 'jede Ausgabe nachvollziehbar, aber nicht je Kategorie',
  },
  {
    stage: 4,
    name: 'Buchungen mit Kategorien',
    what: 'Du ordnest jede Ausgabe einer Kategorie zu, die App schlägt vor.',
    get: ['Jedes Budget einzeln: Soll und Ist', 'Auswertung je Kategorie', 'Ohne Kategorie bleibt sichtbar'],
    effort: 'Hoch',
    effortNote: 'laufend erfassen oder importieren und zuordnen',
    accuracy: 'Hoch',
    accuracyNote: 'jede Ausgabe je Kategorie, Soll und Ist je Budget',
  },
]

export function stageName(stage: IstStage): string {
  return STAGES[stage - 1].name
}

/** R39: the pages a stage hides from the navigation. */
export function hiddenPages(stage: IstStage): Page[] {
  return stage === 1 ? ['transactions', 'evaluation'] : []
}

/** R39: Auswertung › Kategorien only in stage 4. */
export function showsCategoryAnalysis(stage: IstStage): boolean {
  return stage === 4
}
