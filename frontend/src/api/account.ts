import { useMutation, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { ChangePasswordInput } from './types'

/** US16 */
export function changePassword(input: ChangePasswordInput): Promise<void> {
  return request<void>('/account/password', { method: 'PUT', body: JSON.stringify(input) })
}

/** All own data; the frontend only saves it as a file. */
export function exportAccount(): Promise<unknown> {
  return request<unknown>('/account/export')
}

export function deleteAccount(password: string): Promise<void> {
  return request<void>('/account', { method: 'DELETE', body: JSON.stringify({ password }) })
}

export function useChangePassword() {
  return useMutation({ mutationFn: changePassword })
}

export function useExportAccount() {
  return useMutation({ mutationFn: exportAccount })
}

// Afterwards there is no user any more, so the login page shows
export function useDeleteAccount() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: deleteAccount, onSuccess: () => queryClient.resetQueries() })
}
