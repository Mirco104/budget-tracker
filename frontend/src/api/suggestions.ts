import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { request } from './client'

/** R29: the suggested category, null without one; certain only when the latest bookings agree. */
export interface BookingSuggestion {
  categoryId: number | null
  certain: boolean
}

export function fetchBookingSuggestion(text: string, income: boolean): Promise<BookingSuggestion> {
  return request<BookingSuggestion>(`/booking-suggestion?text=${encodeURIComponent(text)}&income=${income}`)
}

/** The suggestion for the text of a booking; keeps the previous one while the next loads, so it does not flicker. */
export function useBookingSuggestion(text: string, income: boolean, enabled: boolean) {
  return useQuery({
    queryKey: ['booking-suggestion', text, income],
    queryFn: () => fetchBookingSuggestion(text, income),
    enabled,
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  })
}
