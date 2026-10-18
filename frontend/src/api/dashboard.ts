import { useQuery } from '@tanstack/react-query'
import { request } from './client'
import type { DashboardData } from './types'

export function fetchDashboard(month: string): Promise<DashboardData> {
  return request<DashboardData>(`/dashboard?month=${encodeURIComponent(month)}`)
}

export function useDashboard(month: string) {
  return useQuery({ queryKey: ['dashboard', month], queryFn: () => fetchDashboard(month) })
}
