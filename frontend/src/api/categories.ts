import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { Category, TargetInput } from './types'

export function fetchCategories(): Promise<Category[]> {
  return request<Category[]>('/categories')
}

export function updateTarget(id: number, input: TargetInput): Promise<Category> {
  return request<Category>(`/categories/${id}/target`, { method: 'PUT', body: JSON.stringify(input) })
}

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: fetchCategories })
}

// A target change affects every month's dashboard (including the remainder), so all months are refreshed.
export function useUpdateTarget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: TargetInput }) => updateTarget(id, input),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['categories'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ]),
  })
}
