import { useQuery } from '@tanstack/react-query'
import { request } from './client'

/** Ticket #82: the local profile of the backend (dev, sample); null in production. */
export interface Health {
  status: string
  mode: string | null
}

export function fetchHealth(): Promise<Health> {
  return request<Health>('/health')
}

/** The mode does not change while the app runs, so it is read once; without an answer no mode shows. */
export function useMode(): string | null {
  const health = useQuery({ queryKey: ['health'], queryFn: fetchHealth, retry: false, staleTime: Infinity })
  return typeof health.data?.mode === 'string' ? health.data.mode : null
}
