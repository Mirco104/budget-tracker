import { afterEach, describe, expect, it } from 'vitest'
import { isIos, preventFocusZoomOnIos } from './viewport'

const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148'
const ANDROID = 'Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36'

function viewport(): HTMLMetaElement {
  document.head.innerHTML = '<meta name="viewport" content="width=device-width, initial-scale=1.0" />'
  return document.querySelector('meta[name="viewport"]') as HTMLMetaElement
}

function navigatorOf(userAgent: string, platform: string, maxTouchPoints: number): Navigator {
  return { userAgent, platform, maxTouchPoints } as Navigator
}

// Ticket #85
describe('viewport', () => {
  afterEach(() => {
    document.head.innerHTML = ''
  })

  it('recognizes iPhone and iPad, also an iPad that reports itself as a Mac', () => {
    expect(isIos(IPHONE, 'iPhone', 5)).toBe(true)
    expect(isIos('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15', 'MacIntel', 5)).toBe(true)
    expect(isIos('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Safari/605.1.15', 'MacIntel', 0)).toBe(false)
    expect(isIos(ANDROID, 'Linux armv8l', 5)).toBe(false)
  })

  it('stops the focus zoom on iOS', () => {
    const meta = viewport()

    preventFocusZoomOnIos(document, navigatorOf(IPHONE, 'iPhone', 5))

    expect(meta.content).toBe('width=device-width, initial-scale=1.0, maximum-scale=1.0')
  })

  it('leaves the viewport of other devices as it is, so zooming with two fingers keeps working', () => {
    const meta = viewport()

    preventFocusZoomOnIos(document, navigatorOf(ANDROID, 'Linux armv8l', 5))

    expect(meta.content).toBe('width=device-width, initial-scale=1.0')
  })
})
