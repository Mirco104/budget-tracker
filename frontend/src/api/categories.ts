import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { Category, CategoryInput, CategoryUpdate, TargetInput } from './types'

/** Categories with the targets in effect for the month ("YYYY-MM"). */
export function fetchCategories(month: string): Promise<Category[]> {
  return request<Category[]>(`/categories?month=${encodeURIComponent(month)}`)
}

/** Sets the target from the month onward. */
export function updateTarget(id: number, month: string, input: TargetInput): Promise<Category> {
  return request<Category>(`/categories/${id}/target?month=${encodeURIComponent(month)}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function createCategory(input: CategoryInput): Promise<Category> {
  return request<Category>('/categories', { method: 'POST', body: JSON.stringify(input) })
}

export function updateCategory(id: number, input: CategoryUpdate): Promise<Category> {
  return request<Category>(`/categories/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteCategory(id: number): Promise<void> {
  return request<void>(`/categories/${id}`, { method: 'DELETE' })
}

export function useCategories(month: string) {
  return useQuery({ queryKey: ['categories', month], queryFn: () => fetchCategories(month) })
}

// A target applies to its month and all later ones (including the remainder), so all months are refreshed.
export function useUpdateTarget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, month, input }: { id: number; month: string; input: TargetInput }) =>
      updateTarget(id, month, input),
    onSuccess: () =>
      Promise.all([
        queryClient.invalidateQueries({ queryKey: ['categories'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      ]),
  })
}

// Category names appear in every page, so all of them are refreshed.
function useInvalidateAfterCategoryChange() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      ['categories', 'dashboard', 'transactions', 'reserves'].map((key) =>
        queryClient.invalidateQueries({ queryKey: [key] }),
      ),
    )
}

export function useCreateCategory() {
  const onSuccess = useInvalidateAfterCategoryChange()
  return useMutation({ mutationFn: createCategory, onSuccess })
}

export function useUpdateCategory() {
  const onSuccess = useInvalidateAfterCategoryChange()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: CategoryUpdate }) => updateCategory(id, input),
    onSuccess,
  })
}

export function useDeleteCategory() {
  const onSuccess = useInvalidateAfterCategoryChange()
  return useMutation({ mutationFn: deleteCategory, onSuccess })
}
