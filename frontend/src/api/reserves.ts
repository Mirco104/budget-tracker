import { useQuery } from '@tanstack/react-query'
import { request } from './client'
import type { HistoryData, ReserveData } from './types'

/** Outlook from the current month on: reserve pot and forecast (R14, decision 29). */
export function fetchReserves(): Promise<ReserveData> {
  return request<ReserveData>('/reserves')
}

export function useReserves() {
  return useQuery({ queryKey: ['reserves'], queryFn: fetchReserves })
}

/** Look back from month to month ("YYYY-MM", both included, R14). */
export function fetchHistory(from: string, to: string): Promise<HistoryData> {
  return request<HistoryData>(`/history?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
}

// While another period loads, the last result stays, so the page does not jump.
export function useHistory(from: string, to: string, enabled = true) {
  return useQuery({
    queryKey: ['history', from, to],
    queryFn: () => fetchHistory(from, to),
    enabled,
    placeholderData: (previous) => previous,
  })
}
