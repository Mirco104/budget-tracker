import { afterEach, describe, expect, it, vi } from 'vitest'
import { request, SERVER_ERROR } from './client'

function respond(status: number, body: unknown) {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(body), { status })))
}

describe('request', () => {
  afterEach(() => vi.unstubAllGlobals())

  it('shows an understandable text for an unexpected server error', async () => {
    respond(500, { timestamp: '2026-11-21T10:00:00Z', status: 500, error: 'Internal Server Error', path: '/api/auth/login' })
    await expect(request('/auth/login')).rejects.toThrow(SERVER_ERROR)
  })

  it('ignores the English title of a server error', async () => {
    respond(500, { title: 'Internal Server Error', status: 500 })
    await expect(request('/auth/login')).rejects.toThrow(SERVER_ERROR)
  })

  it('keeps the detail of the backend', async () => {
    respond(409, { title: 'Konflikt', status: 409, detail: 'Die Kategorie gibt es schon.' })
    await expect(request('/categories')).rejects.toThrow('Die Kategorie gibt es schon.')
  })
})
