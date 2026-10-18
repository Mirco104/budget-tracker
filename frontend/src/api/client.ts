import type { ProblemDetail } from './types'

export class ApiError extends Error {
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? 'Unbekannter Fehler')
    this.problem = problem
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...init?.headers },
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
