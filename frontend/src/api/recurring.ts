import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type {
  OccurrenceScope,
  RecurringTransaction,
  RecurringTransactionInput,
  Transaction,
  TransactionInput,
} from './types'

/** Series active in the month ("YYYY-MM"). */
export function fetchRecurringTransactions(month: string): Promise<RecurringTransaction[]> {
  return request<RecurringTransaction[]>(`/recurring-transactions?month=${encodeURIComponent(month)}`)
}

export function createRecurringTransaction(input: RecurringTransactionInput): Promise<RecurringTransaction> {
  return request<RecurringTransaction>('/recurring-transactions', { method: 'POST', body: JSON.stringify(input) })
}

function occurrencePath(recurringId: number, occurrenceDate: string, scope: OccurrenceScope): string {
  return `/recurring-transactions/${recurringId}/occurrences/${occurrenceDate}?scope=${scope}`
}

/** "Nur dieser Eintrag": the occurrence is suspended and the values are booked as a single transaction. */
export function updateOccurrenceThis(
  recurringId: number,
  occurrenceDate: string,
  input: TransactionInput,
): Promise<Transaction> {
  return request<Transaction>(occurrencePath(recurringId, occurrenceDate, 'THIS'), {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

/** "Dieser und alle folgenden": changes the series from this occurrence on. */
export function updateOccurrenceFollowing(
  recurringId: number,
  occurrenceDate: string,
  input: RecurringTransactionInput,
): Promise<RecurringTransaction> {
  return request<RecurringTransaction>(occurrencePath(recurringId, occurrenceDate, 'FOLLOWING'), {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function deleteOccurrence(recurringId: number, occurrenceDate: string, scope: OccurrenceScope): Promise<void> {
  return request<void>(occurrencePath(recurringId, occurrenceDate, scope), { method: 'DELETE' })
}

export function useRecurringTransactions(month: string) {
  return useQuery({ queryKey: ['recurring', month], queryFn: () => fetchRecurringTransactions(month) })
}

// A series affects many months, so all transaction lists, dashboards, series lists and reserves are refreshed.
function useInvalidateAfterChange() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['transactions'] }),
      queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
      queryClient.invalidateQueries({ queryKey: ['recurring'] }),
      queryClient.invalidateQueries({ queryKey: ['reserves'] }),
    ])
}

export function useCreateRecurringTransaction() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({ mutationFn: createRecurringTransaction, onSuccess })
}

export function useUpdateOccurrenceThis() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({
    mutationFn: ({ recurringId, date, input }: { recurringId: number; date: string; input: TransactionInput }) =>
      updateOccurrenceThis(recurringId, date, input),
    onSuccess,
  })
}

export function useUpdateOccurrenceFollowing() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({
    mutationFn: ({
      recurringId,
      date,
      input,
    }: {
      recurringId: number
      date: string
      input: RecurringTransactionInput
    }) => updateOccurrenceFollowing(recurringId, date, input),
    onSuccess,
  })
}

export function useDeleteOccurrence() {
  const onSuccess = useInvalidateAfterChange()
  return useMutation({
    mutationFn: ({ recurringId, date, scope }: { recurringId: number; date: string; scope: OccurrenceScope }) =>
      deleteOccurrence(recurringId, date, scope),
    onSuccess,
  })
}
