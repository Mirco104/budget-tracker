import { useMe } from './api/auth'

/** R35: categories of bookings only show in stage 4; below it a booking is without category, a transfer or Sparen. */
export function useShowsCategories(): boolean {
  return (useMe().data?.istStage ?? 4) === 4
}
