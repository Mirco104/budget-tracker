import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { Transaction, TransactionInput } from './types'

export function fetchTransactions(month: string): Promise<Transaction[]> {
  return request<Transaction[]>(`/transactions?month=${encodeURIComponent(month)}`)
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
