import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { Plan, PlannedItem, PlannedItemInput } from './types'

/** The plan of the month ("YYYY-MM"): groups, items with their monthly share and the sums (P2–P4). */
export function fetchPlan(month: string): Promise<Plan> {
  return request<Plan>(`/plan?month=${encodeURIComponent(month)}`)
}

export function createPlannedItem(input: PlannedItemInput): Promise<PlannedItem> {
  return request<PlannedItem>('/planned-items', { method: 'POST', body: JSON.stringify(input) })
}

/** "Gilt ab": validFrom of the input decides whether the version changes or a new one starts. */
export function updatePlannedItem(id: number, input: PlannedItemInput): Promise<PlannedItem> {
  return request<PlannedItem>(`/planned-items/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

/** Ends the item from the month on; without a month the item is deleted. */
export function deletePlannedItem(id: number, from?: string): Promise<void> {
  const query = from ? `?from=${encodeURIComponent(from)}` : ''
  return request<void>(`/planned-items/${id}${query}`, { method: 'DELETE' })
}

/** 27f2: how many past terms an automatic item with these values would enter at once. */
export function usePastTerms(input: PlannedItemInput | null) {
  return useQuery({
    queryKey: ['past-terms', input],
    queryFn: () =>
      request<{ count: number }>('/planned-items/past-terms', { method: 'POST', body: JSON.stringify(input) }),
    enabled: input !== null,
    staleTime: 30_000,
    // The last count stays while a changed date loads, so the hint does not disappear and reappear (#126)
    placeholderData: keepPreviousData,
  })
}

export function usePlan(month: string) {
  return useQuery({ queryKey: ['plan', month], queryFn: () => fetchPlan(month) })
}

// The plan feeds the overview (plan per category) and the analysis, so those refresh as well
function useRefresh() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      ['plan', 'dashboard', 'reserves', 'transactions', 'terms'].map((key) =>
        queryClient.invalidateQueries({ queryKey: [key] }),
      ),
    )
}

export function useCreatePlannedItem() {
  const refresh = useRefresh()
  return useMutation({ mutationFn: createPlannedItem, onSuccess: refresh })
}

export function useUpdatePlannedItem() {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: PlannedItemInput }) => updatePlannedItem(id, input),
    onSuccess: refresh,
  })
}

export function useDeletePlannedItem() {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ id, from }: { id: number; from?: string }) => deletePlannedItem(id, from),
    onSuccess: refresh,
  })
}
