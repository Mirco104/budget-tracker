import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { AccountGroup, ReconcileResult } from './types'

/** Accounts by role with their app balance (P11). */
export function useAccounts() {
  return useQuery({ queryKey: ['accounts'], queryFn: () => request<AccountGroup[]>('/accounts') })
}

/** P11: stores the balance from the bank and returns the difference to the app balance (not booked). */
export function useReconcile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ accountId, date, amount }: { accountId: number; date: string; amount: number }) =>
      request<ReconcileResult>(`/bank-accounts/${accountId}/balances`, {
        method: 'POST',
        body: JSON.stringify({ date, amount }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  })
}

/** P8: links the reserve pot of a rare item to a savings account, or unlinks it with null. */
export function useLinkReserve() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ itemId, accountId }: { itemId: number; accountId: number | null }) =>
      request<unknown>(`/planned-items/${itemId}/reserve-account`, {
        method: 'PUT',
        body: JSON.stringify({ accountId }),
      }),
    onSuccess: () =>
      Promise.all(['plan', 'dashboard', 'accounts'].map((key) => queryClient.invalidateQueries({ queryKey: [key] }))),
  })
}
