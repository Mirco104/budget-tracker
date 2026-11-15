import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { Term, Transaction } from './types'

/** Terms with their planned date from and to (ISO dates), with status (P5); due automatic entries are made first. */
export function fetchTerms(from: string, to: string): Promise<Term[]> {
  return request<Term[]>(`/terms?from=${from}&to=${to}`)
}

export function useTerms(from: string, to: string) {
  return useQuery({ queryKey: ['terms', from, to], queryFn: () => fetchTerms(from, to) })
}

/** P7: the term, optionally with another amount (without sign) and booking date. */
export interface PayInput {
  itemId: number
  date: string
  amount?: number
  bookingDate?: string
}

function termQuery(itemId: number, date: string): string {
  return `itemId=${itemId}&date=${date}`
}

// A tick-off books, so the lists and sums refresh as well
function useRefresh() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      ['terms', 'transactions', 'dashboard', 'reserves'].map((key) => queryClient.invalidateQueries({ queryKey: [key] })),
    )
}

export function usePayTerm() {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: (input: PayInput) =>
      request<Transaction>('/terms/paid', { method: 'POST', body: JSON.stringify(input) }),
    onSuccess: refresh,
  })
}

export function useUnpayTerm() {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ itemId, date }: { itemId: number; date: string }) =>
      request<void>(`/terms/paid?${termQuery(itemId, date)}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}

export function useSkipTerm() {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ itemId, date }: { itemId: number; date: string }) =>
      request<void>('/terms/skip', { method: 'POST', body: JSON.stringify({ itemId, date }) }),
    onSuccess: refresh,
  })
}

export function useUnskipTerm() {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: ({ itemId, date }: { itemId: number; date: string }) =>
      request<void>(`/terms/skip?${termQuery(itemId, date)}`, { method: 'DELETE' }),
    onSuccess: refresh,
  })
}
