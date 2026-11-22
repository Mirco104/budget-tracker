import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

// jsdom has no ResizeObserver, which the Recharts ResponsiveContainer needs
class ResizeObserverStub {
  observe() {}
  unobserve() {}
  disconnect() {}
}
globalThis.ResizeObserver ??= ResizeObserverStub

// Review 23.11.2026: the first load of a lazy page (setup wizard, Auswertung) can take more than the default 1 s of
// findBy* when all test files run in parallel
configure({ asyncUtilTimeout: 3000 })

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})
