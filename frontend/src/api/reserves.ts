import { useQuery } from '@tanstack/react-query'
import { request } from './client'
import type { ReserveData } from './types'

export function fetchReserves(month: string): Promise<ReserveData> {
  return request<ReserveData>(`/reserves?month=${encodeURIComponent(month)}`)
}

export function useReserves(month: string) {
  return useQuery({ queryKey: ['reserves', month], queryFn: () => fetchReserves(month) })
}
