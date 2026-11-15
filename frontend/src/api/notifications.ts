import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'

/** 27h (decision 65): a notification of the bell; only the fields of its kind are set. */
export interface AppNotification {
  key: string
  kind: 'MONTH_CLOSING' | 'LOWER_LIMIT' | 'PRICE_CHANGE' | 'OVERPLANNED'
  month: string | null
  amount: number | null
  accountId: number | null
  accountName: string | null
  balance: number | null
  expected: number | null
  limit: number | null
  itemId: number | null
  itemName: string | null
  oldAmount: number | null
  newAmount: number | null
}

/** P10: a past month with its leftover, its closing and the change of each account (decision 59). */
export interface MonthView {
  month: string
  leftover: number
  closed: boolean
  savingGoalId: number | null
  savingGoalName: string | null
  accounts: { accountId: number; name: string; change: number }[]
}

export function useNotifications() {
  return useQuery({
    queryKey: ['notifications'],
    queryFn: () => request<AppNotification[]>('/notifications'),
    refetchOnWindowFocus: true,
  })
}

/** What a closing, a dismissal or a price change touches. */
function useInvalidateAfterBell() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      ['notifications', 'month-closing', 'saving-goals', 'plan', 'dashboard'].map((key) =>
        queryClient.invalidateQueries({ queryKey: [key] }),
      ),
    )
}

/** "Erledigt": the notification does not come back. */
export function useDismissNotification() {
  const onSuccess = useInvalidateAfterBell()
  return useMutation({
    mutationFn: (key: string) => request<void>('/notifications/done', { method: 'POST', body: JSON.stringify({ key }) }),
    onSuccess,
  })
}

/** P13 "Übernehmen": the item gets the new amount from this month on. */
export function useAcceptPriceChange() {
  const onSuccess = useInvalidateAfterBell()
  return useMutation({
    mutationFn: ({ itemId, amount }: { itemId: number; amount: number }) =>
      request<void>('/notifications/price-change', { method: 'POST', body: JSON.stringify({ itemId, amount }) }),
    onSuccess,
  })
}

export function useMonthView(month: string, enabled: boolean) {
  return useQuery({
    queryKey: ['month-closing', month],
    queryFn: () => request<MonthView>(`/month-closings/${month}`),
    enabled,
  })
}

/** P10: closes the month with its leftover; savingGoalId null closes it without a goal ("Nicht zuordnen"). */
export function useCloseMonth() {
  const onSuccess = useInvalidateAfterBell()
  return useMutation({
    mutationFn: ({ month, savingGoalId }: { month: string; savingGoalId: number | null }) =>
      request<MonthView>(`/month-closings/${month}`, { method: 'POST', body: JSON.stringify({ savingGoalId }) }),
    onSuccess,
  })
}

/** P10 "Rückgängig". */
export function useUndoClosing() {
  const onSuccess = useInvalidateAfterBell()
  return useMutation({
    mutationFn: (month: string) => request<void>(`/month-closings/${month}`, { method: 'DELETE' }),
    onSuccess,
  })
}
