import { useQuery } from '@tanstack/react-query'
import { request } from './client'
import type { Category } from './types'

export function fetchCategories(): Promise<Category[]> {
  return request<Category[]>('/categories')
}

export function useCategories() {
  return useQuery({ queryKey: ['categories'], queryFn: fetchCategories })
}
