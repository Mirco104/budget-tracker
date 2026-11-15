import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { Category, CategoryInput, CategoryUpdate } from './types'

/** The categories; month is kept in the query key for the pages that pass it (the plan comes with /plan). */
export function fetchCategories(month: string): Promise<Category[]> {
  return request<Category[]>(`/categories?month=${encodeURIComponent(month)}`)
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

// Category names appear in every page, so all of them are refreshed.
function useInvalidateAfterCategoryChange() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      ['categories', 'dashboard', 'transactions', 'reserves', 'plan'].map((key) =>
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
