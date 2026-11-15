import { APP_VERSION, releaseStage } from '../../version'

/**
 * Ticket #129: names the stage of the app (Alpha, Beta) next to the brand on every page, so everyone sees it is not
 * finished; a release like 1.0.0 shows nothing.
 */
export function ReleaseBadge({ version = APP_VERSION }: { version?: string }) {
  const stage = releaseStage(version)
  if (stage === null) {
    return null
  }
  return (
    <span className="release-badge" title={`Version ${version}: frühe Testphase, es kann noch Fehler geben.`}>
      {stage}
    </span>
  )
}
