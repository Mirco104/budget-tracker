import { useMutation, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { ChangeEmailInput, ChangePasswordInput, Me } from './types'

/** US16 */
export function changePassword(input: ChangePasswordInput): Promise<void> {
  return request<void>('/account/password', { method: 'PUT', body: JSON.stringify(input) })
}

export function changeEmail(input: ChangeEmailInput): Promise<Me> {
  return request<Me>('/account/email', { method: 'PUT', body: JSON.stringify(input) })
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

// The e-mail shows in the account section and the user overview
export function useChangeEmail() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: changeEmail,
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['me'] }),
        queryClient.invalidateQueries({ queryKey: ['users'] }),
      ]),
  })
}

export function useExportAccount() {
  return useMutation({ mutationFn: exportAccount })
}

// Afterwards there is no user any more, so the login page shows
export function useDeleteAccount() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: deleteAccount, onSuccess: () => queryClient.resetQueries() })
}
