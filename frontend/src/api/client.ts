import type { ProblemDetail } from './types'

export class ApiError extends Error {
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? 'Unbekannter Fehler')
    this.problem = problem
  }
}

/** The token the backend puts in the XSRF-TOKEN cookie; writing calls send it back as a header (section 15). */
function csrfToken(): string | undefined {
  const cookie = document.cookie.split('; ').find((entry) => entry.startsWith('XSRF-TOKEN='))
  return cookie ? decodeURIComponent(cookie.slice('XSRF-TOKEN='.length)) : undefined
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? 'GET'
  const token = method === 'GET' ? undefined : csrfToken()
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { 'X-XSRF-TOKEN': token } : {}),
        ...init?.headers,
      },
    })
  } catch {
    throw new ApiError({ detail: 'Der Server ist nicht erreichbar.' })
  }

  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as ProblemDetail
    throw new ApiError({ status: response.status, ...problem })
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
