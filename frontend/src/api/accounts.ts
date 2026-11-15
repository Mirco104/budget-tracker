import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { AccountGroup, BalanceLine, LowerLimitMode, ReconcileResult, ReserveGroup } from './types'

/** Accounts by role with their app balance (P11). */
export function useAccounts() {
  return useQuery({ queryKey: ['accounts'], queryFn: () => request<AccountGroup[]>('/accounts') })
}

/** 27f2: the reserve pots by their savings account, with the coverage by the account. */
export function useReservesByAccount() {
  return useQuery({ queryKey: ['accounts', 'reserves'], queryFn: () => request<ReserveGroup[]>('/reserves/by-account') })
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

/** P12 (27h): sets the lower limit of an account; amount only for CUSTOM. */
export function useSetLowerLimit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ accountId, mode, amount }: { accountId: number; mode: LowerLimitMode; amount: number | null }) =>
      request<unknown>(`/bank-accounts/${accountId}/lower-limit`, {
        method: 'PUT',
        body: JSON.stringify({ mode, amount }),
      }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  })
}

/**
 * Ticket #135: the balances from the bank of an account, newest first; refreshed with every change of ['accounts'].
 * Without an account (a new one) nothing is loaded.
 */
export function useBalanceHistory(accountId: number | null) {
  return useQuery({
    queryKey: ['accounts', 'balances', accountId],
    queryFn: () => request<BalanceLine[]>(`/bank-accounts/${accountId}/balances`),
    enabled: accountId !== null,
  })
}

/** Ticket #135: deletes a wrong balance from the bank; the app balance then counts from the one before (P11). */
export function useDeleteBalance() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ accountId, balanceId }: { accountId: number; balanceId: number }) =>
      request<unknown>(`/bank-accounts/${accountId}/balances/${balanceId}`, { method: 'DELETE' }),
    onSuccess: () =>
      Promise.all(
        ['accounts', 'bank-accounts', 'notifications', 'month-closing'].map((key) =>
          queryClient.invalidateQueries({ queryKey: [key] }),
        ),
      ),
  })
}
