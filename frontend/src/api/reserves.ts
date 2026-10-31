import { useQuery } from '@tanstack/react-query'
import { request } from './client'
import type { ReserveData } from './types'

/** months is the period of the average and look back (R14); the backend defaults to 12. */
export function fetchReserves(month: string, months = 12): Promise<ReserveData> {
  return request<ReserveData>(`/reserves?month=${encodeURIComponent(month)}&months=${months}`)
}

// While another period of the same month loads, the last result stays, so the page and its period select stay
// in place. A different month never shows the data of the previous one.
export function useReserves(month: string, months = 12) {
  return useQuery({
    queryKey: ['reserves', month, months],
    queryFn: () => fetchReserves(month, months),
    placeholderData: (previous) => (previous?.month === month ? previous : undefined),
  })
}
