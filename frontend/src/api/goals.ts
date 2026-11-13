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
