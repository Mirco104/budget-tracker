import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { request } from './client'

/** The planned occurrence a booking would replace (R28); amount is the amount of the series without sign. */
export interface SuggestedOccurrence {
  recurringId: number
  date: string
  description: string
  amount: number
  categoryId: number
}

/**
 * R29: the suggested category, null without one; certain only when the latest bookings agree. With an occurrence
 * (R28) the category is the one of its series.
 */
export interface BookingSuggestion {
  categoryId: number | null
  certain: boolean
  occurrence?: SuggestedOccurrence | null
}

/** Amount (signed) and date of a new booking, to find the occurrence it replaces (R28). */
export interface BookingFacts {
  amount: number | null
  date: string
}

export function fetchBookingSuggestion(text: string, income: boolean, facts?: BookingFacts): Promise<BookingSuggestion> {
  const occurrence = facts && facts.amount !== null && facts.date !== '' ? `&amount=${facts.amount}&date=${facts.date}` : ''
  return request<BookingSuggestion>(`/booking-suggestion?text=${encodeURIComponent(text)}&income=${income}${occurrence}`)
}

/** The suggestion for the text of a booking; keeps the previous one while the next loads, so it does not flicker. */
export function useBookingSuggestion(text: string, income: boolean, enabled: boolean, facts?: BookingFacts) {
  return useQuery({
    queryKey: ['booking-suggestion', text, income, facts?.amount ?? null, facts?.date ?? null],
    queryFn: () => fetchBookingSuggestion(text, income, facts),
    enabled,
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  })
}
