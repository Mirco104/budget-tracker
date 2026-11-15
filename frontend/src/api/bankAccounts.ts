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
