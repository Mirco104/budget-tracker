import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { SavingGoal, SavingGoalInput } from './types'

export function fetchSavingGoals(): Promise<SavingGoal[]> {
  return request<SavingGoal[]>('/saving-goals')
}

export function useSavingGoals() {
  return useQuery({ queryKey: ['saving-goals'], queryFn: fetchSavingGoals })
}

export function useCreateSavingGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: SavingGoalInput) =>
      request<SavingGoal>('/saving-goals', { method: 'POST', body: JSON.stringify(input) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['saving-goals'] }),
  })
}

/** 27f4: saving goals are managed on the page Konten. */
export function useUpdateSavingGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: SavingGoalInput }) =>
      request<SavingGoal>(`/saving-goals/${id}`, { method: 'PUT', body: JSON.stringify(input) }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['saving-goals'] }),
  })
}

/** A goal with saving items or bookings cannot be deleted (409). */
export function useDeleteSavingGoal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => request<unknown>(`/saving-goals/${id}`, { method: 'DELETE' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['saving-goals'] }),
  })
}
