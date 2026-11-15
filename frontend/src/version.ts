// Ticket #129: set by Vite at build time (vite.config.ts)
declare const __APP_VERSION__: string
declare const __APP_COMMIT__: string

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
