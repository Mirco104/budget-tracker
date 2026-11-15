import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { Transaction, TransactionInput } from './types'

export function fetchTransactions(month: string): Promise<Transaction[]> {
  return request<Transaction[]>(`/transactions?month=${encodeURIComponent(month)}`)
}

/** Entries from one day to another, both included ("YYYY-MM-DD", US26). */
export function fetchTransactionsBetween(from: string, to: string): Promise<Transaction[]> {
  return request<Transaction[]>(`/transactions?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
}

export function createTransaction(input: TransactionInput): Promise<Transaction> {
  return request<Transaction>('/transactions', { method: 'POST', body: JSON.stringify(input) })
}

export function updateTransaction(id: number, input: TransactionInput): Promise<Transaction> {
  return request<Transaction>(`/transactions/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteTransaction(id: number): Promise<void> {
  return request<void>(`/transactions/${id}`, { method: 'DELETE' })
}

export function useTransactions(month: string) {
  return useQuery({ queryKey: ['transactions', month], queryFn: () => fetchTransactions(month) })
}

/** 27f4: the first and the last day of the user's bookings (null without one), for the quick choice "Gesamt". */
export function useTransactionRange(enabled = true) {
  return useQuery({
    // Under 'transactions', so every change of a booking refreshes it
    queryKey: ['transactions', 'range'],
    queryFn: () => request<{ from: string | null; to: string | null }>('/transactions/range'),
    enabled,
  })
}

export function useTransactionsBetween(from: string, to: string, enabled = true) {
  return useQuery({
    queryKey: ['transactions', 'period', from, to],
    queryFn: () => fetchTransactionsBetween(from, to),
    enabled,
  })
}

// A change can move a transaction between months, so all months are refreshed.
function useInvalidateAfterChange() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['transactions'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
}

export function useCreateTransaction() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({ mutationFn: createTransaction, onSuccess })
}

export function useUpdateTransaction() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: TransactionInput }) => updateTransaction(id, input),
    onSuccess,
  })
}

export function useDeleteTransaction() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({ mutationFn: deleteTransaction, onSuccess })
}
