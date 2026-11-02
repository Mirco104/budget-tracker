import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { UserSummary } from './types'

/** US25: all accounts, only for admins. */
export function fetchUsers(): Promise<UserSummary[]> {
  return request<UserSummary[]>('/users')
}

export function changeRole(id: number, role: 'USER' | 'ADMIN'): Promise<UserSummary> {
  return request<UserSummary>(`/users/${id}/role`, { method: 'PUT', body: JSON.stringify({ role }) })
}

export function useUsers() {
  return useQuery({ queryKey: ['users'], queryFn: fetchUsers })
}

export function useChangeRole() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, role }: { id: number; role: 'USER' | 'ADMIN' }) => changeRole(id, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
  })
}
