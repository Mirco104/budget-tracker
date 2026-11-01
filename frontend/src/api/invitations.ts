import { useMutation, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { AcceptInvitationInput, Invitation, Me } from './types'

/** US15: only for admins. */
export function createInvitation(): Promise<Invitation> {
  return request<Invitation>('/invitations', { method: 'POST' })
}

/** Creates the user and signs them in. */
export function acceptInvitation(token: string, input: AcceptInvitationInput): Promise<Me> {
  return request<Me>(`/invitations/${encodeURIComponent(token)}/accept`, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function useCreateInvitation() {
  return useMutation({ mutationFn: createInvitation })
}

// The new user is signed in, so every query starts over
export function useAcceptInvitation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ token, input }: { token: string; input: AcceptInvitationInput }) => acceptInvitation(token, input),
    onSuccess: () => queryClient.resetQueries(),
  })
}
