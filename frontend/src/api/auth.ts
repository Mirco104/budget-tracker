import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, request } from './client'
import type { LoginInput, Me } from './types'

/** The signed-in user, or null without a session (401). */
export async function fetchMe(): Promise<Me | null> {
  try {
    return await request<Me>('/auth/me')
  } catch (e) {
    if (e instanceof ApiError && e.problem.status === 401) {
      return null
    }
    throw e
  }
}

export function login(input: LoginInput): Promise<void> {
  return request<void>('/auth/login', { method: 'POST', body: JSON.stringify(input) })
}

export function logout(): Promise<void> {
  return request<void>('/auth/logout', { method: 'POST' })
}

export function useMe() {
  return useQuery({ queryKey: ['me'], queryFn: fetchMe, retry: false })
}

// After signing in or out all data belongs to another user, so every query starts over
export function useLogin() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: login, onSuccess: () => queryClient.resetQueries() })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: logout, onSuccess: () => queryClient.resetQueries() })
}
