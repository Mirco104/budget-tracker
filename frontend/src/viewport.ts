/**
 * Ticket #85: iOS Safari zooms into a field when it gets focus if it finds the text too small, and stays zoomed in.
 * maximum-scale=1 stops that zoom on iOS only; there the user can still zoom with two fingers (iOS 10 and later).
 * Android keeps the plain viewport, because there maximum-scale would block zooming with two fingers.
 */
export function isIos(userAgent: string, platform: string, maxTouchPoints: number): boolean {
  // An iPad with iPadOS 13 or later reports itself as a Mac with touch
  return /iPad|iPhone|iPod/.test(userAgent) || (platform === 'MacIntel' && maxTouchPoints > 1)
}

export function preventFocusZoomOnIos(doc: Document = document, nav: Navigator = navigator): void {
  if (!isIos(nav.userAgent, nav.platform, nav.maxTouchPoints)) {
    return
  }
  doc
    .querySelector('meta[name="viewport"]')
    ?.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0')
}
