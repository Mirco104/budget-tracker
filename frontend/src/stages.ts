import type { IstStage } from './api/types'
import type { Page } from './router'

/** R35: the four Ist stages with their name (display texts only). */
export const STAGES: { stage: IstStage; name: string }[] = [
  { stage: 1, name: 'Nur Plan' },
  { stage: 2, name: 'Kontostand' },
  { stage: 3, name: 'Buchungen' },
  { stage: 4, name: 'Buchungen mit Kategorien' },
]

/** Decision 87: what the stages deliver; stage n has the first n checks. */
export const CHECKS = [
  'Aufteilung deines Einkommens',
  'Summe der variablen Kosten, wenn du den Kontostand einträgst',
  'Jede einzelne Ausgabe, jederzeit aktuell',
  'Wofür genau: je Kategorie',
] as const

/** Decision 86: the answers to "Was willst du über dein Geld wissen?"; answer n wishes stage n. */
export const WISHES: { text: string; note: string }[] = [
  { text: 'Wie ich mein Einkommen aufteile', note: '' },
  { text: '… und wie viel ich für variable Kosten ausgegeben habe', note: 'z. B. Lebensmittel, als Summe, immer wenn ich meinen Kontostand eintrage' },
  { text: '… und jede einzelne Ausgabe', note: 'als Liste, jederzeit aktuell' },
  { text: '… und wofür genau', note: 'jede Ausgabe in einer Kategorie' },
]

/** Decision 86: the answers to "Wie viel Zeit willst du investieren?"; answer n allows up to stage n. */
export const TIMES = ['Gar keine', '5 Minuten im Monat', 'Ein paar Minuten pro Woche', 'Was nötig ist'] as const

/** Decision 90: a switch from or to stage 1 changes how items are entered, so it asks first. */
export function needsSwitchDialog(from: IstStage, to: IstStage): boolean {
  return from !== to && (from === 1 || to === 1)
}

/** R35: the recommended stage is the smaller one of the wished stage and the stage the time allows (both 1 to 4). */
export function recommendStage(wish: IstStage, time: IstStage): IstStage {
  return Math.min(wish, time) as IstStage
}

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
