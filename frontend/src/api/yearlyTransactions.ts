import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { YearlyTransaction, YearlyTransactionInput } from './types'

/** Yearly transactions valid in the month ("YYYY-MM"). */
export function fetchYearlyTransactions(month: string): Promise<YearlyTransaction[]> {
  return request<YearlyTransaction[]>(`/yearly-transactions?month=${encodeURIComponent(month)}`)
}

export function createYearlyTransaction(input: YearlyTransactionInput): Promise<YearlyTransaction> {
  return request<YearlyTransaction>('/yearly-transactions', { method: 'POST', body: JSON.stringify(input) })
}

export function updateYearlyTransaction(id: number, input: YearlyTransactionInput): Promise<YearlyTransaction> {
  return request<YearlyTransaction>(`/yearly-transactions/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteYearlyTransaction(id: number): Promise<void> {
  return request<void>(`/yearly-transactions/${id}`, { method: 'DELETE' })
}

export function useYearlyTransactions(month: string) {
  return useQuery({ queryKey: ['yearly-transactions', month], queryFn: () => fetchYearlyTransactions(month) })
}

// A yearly transaction counts in a range of months, so all months are refreshed.
function useInvalidateAfterChange() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['yearly-transactions'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
    ])
}

export function useCreateYearlyTransaction() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({ mutationFn: createYearlyTransaction, onSuccess })
}

export function useUpdateYearlyTransaction() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: YearlyTransactionInput }) => updateYearlyTransaction(id, input),
    onSuccess,
  })
}

export function useDeleteYearlyTransaction() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({ mutationFn: deleteYearlyTransaction, onSuccess })
}
