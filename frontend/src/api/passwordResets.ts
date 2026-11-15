import { useMutation, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { Me, PasswordResetLink } from './types'

/** US36: only for admins; a link for another account whose password is forgotten. */
export function createPasswordReset(userId: number): Promise<PasswordResetLink> {
  return request<PasswordResetLink>(`/users/${userId}/password-reset`, { method: 'POST' })
}

/** Sets the new password and signs the user in. */
export function resetPassword(token: string, password: string): Promise<Me> {
  return request<Me>(`/password-resets/${encodeURIComponent(token)}`, {
    method: 'POST',
    body: JSON.stringify({ password }),
  })
}

export function useCreatePasswordReset() {
  return useMutation({ mutationFn: createPasswordReset })
}

// The user is signed in afterwards, so every query starts over
export function useResetPassword() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ token, password }: { token: string; password: string }) => resetPassword(token, password),
    onSuccess: () => queryClient.resetQueries(),
  })
}
