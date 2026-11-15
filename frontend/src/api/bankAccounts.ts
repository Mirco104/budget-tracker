import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { BankAccount, BankAccountInput, BankAccountUpdate } from './types'

/** The user's bank accounts, the default account first (R26). */
export function fetchBankAccounts(): Promise<BankAccount[]> {
  return request<BankAccount[]>('/bank-accounts')
}

export function createBankAccount(input: BankAccountInput): Promise<BankAccount> {
  return request<BankAccount>('/bank-accounts', { method: 'POST', body: JSON.stringify(input) })
}

export function updateBankAccount(id: number, input: BankAccountUpdate): Promise<BankAccount> {
  return request<BankAccount>(`/bank-accounts/${id}`, { method: 'PUT', body: JSON.stringify(input) })
}

export function deleteBankAccount(id: number): Promise<void> {
  return request<void>(`/bank-accounts/${id}`, { method: 'DELETE' })
}

export function useBankAccounts() {
  return useQuery({ queryKey: ['bank-accounts'], queryFn: fetchBankAccounts })
}

// Account names appear in the transaction list
function useInvalidateAfterAccountChange() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      ['bank-accounts', 'transactions', 'accounts', 'dashboard'].map((key) =>
        queryClient.invalidateQueries({ queryKey: [key] }),
      ),
    )
}

export function useCreateBankAccount() {
  const onSuccess = useInvalidateAfterAccountChange()
  return useMutation({ mutationFn: createBankAccount, onSuccess })
}

export function useUpdateBankAccount() {
  const onSuccess = useInvalidateAfterAccountChange()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: BankAccountUpdate }) => updateBankAccount(id, input),
    onSuccess,
  })
}

export function useDeleteBankAccount() {
  const onSuccess = useInvalidateAfterAccountChange()
  return useMutation({ mutationFn: deleteBankAccount, onSuccess })
}

export interface MergeResult {
  account: BankAccount
  movedTransactions: number
  deletedTransfers: number
}

/** Ticket #139: moves everything of the account to the target account and deletes it (R26). */
export function mergeBankAccount(id: number, targetId: number): Promise<MergeResult> {
  return request<MergeResult>(`/bank-accounts/${id}/merge`, { method: 'POST', body: JSON.stringify({ targetId }) })
}

export function useMergeBankAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, targetId }: { id: number; targetId: number }) => mergeBankAccount(id, targetId),
    // Bookings, items, reserve pots and balances change their account
    onSuccess: () =>
      Promise.all(
        ['bank-accounts', 'transactions', 'accounts', 'dashboard', 'plan', 'terms', 'reserves', 'notifications'].map(
          (key) => queryClient.invalidateQueries({ queryKey: [key] }),
        ),
      ),
  })
}
