import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request } from './client'

export type ImportProfile = 'C24' | 'TRADE_REPUBLIC' | 'N26' | 'SPARKASSE'

/** NEW and POSSIBLE_DUPLICATE can be imported; DUPLICATE was imported before; SKIPPED cannot be imported (R30). */
export type ImportStatus = 'NEW' | 'POSSIBLE_DUPLICATE' | 'DUPLICATE' | 'SKIPPED'

/** A source account of the bank file; accountId is the own account it went to last time, else null. */
export interface ImportSource {
  key: string
  name: string
  accountId: number | null
  suggestedName: string
}

/**
 * One line of the file. categoryId is the suggestion (R29) with its certainty; transfer the suggestion of R27,
 * transferPeerId a stored counterpart that becomes a transfer as well.
 */
export interface ImportEntry {
  line: number
  status: ImportStatus
  reason: string | null
  fingerprint: string | null
  source: string | null
  date: string | null
  amount: number | null
  description: string | null
  counterparty: string | null
  bankCategory: string | null
  suggestionKey: string | null
  categoryId: number | null
  certain: boolean
  transfer: boolean
  transferCertain: boolean
  transferPeerId: number | null
  /** R27: the counterpart for the hint; line is set for a row of the same file (to jump to it), accountName is then its source. */
  transferPeer: {
    line: number | null
    date: string | null
    description: string | null
    amount: number | null
    accountName: string | null
  } | null
  /**
   * P5: the term of a planned item the row fulfils (recurringId is the id of the item version); categoryId is then the
   * category of the item. autoEntryId: the automatic entry of the term the row replaces (P6).
   */
  replaces: {
    recurringId: number
    date: string
    description: string
    amount: number
    categoryId: number
    autoEntryId?: number | null
  } | null
  duplicateOf: { id: number; date: string; description: string } | null
}

/** R31: rows that look like a monthly series that does not exist yet; categoryId is only a suggestion. */
export interface ImportSeries {
  lines: number[]
  startDate: string
  amount: number
  description: string
  source: string
  categoryId: number | null
}

export interface ImportPreview {
  profile: ImportProfile
  profileLabel: string
  rows: number
  newRows: number
  duplicates: number
  skipped: number
  sources: ImportSource[]
  entries: ImportEntry[]
  series: ImportSeries[]
}

export interface ImportRequest {
  profile: ImportProfile
  sources: { key: string; accountId?: number; newAccountName?: string }[]
  rows: {
    fingerprint: string
    source: string
    date: string
    amount: number
    description: string
    counterparty: string | null
    bankCategory: string | null
    categoryId: number | null
    transfer: boolean
    transferPeerId?: number
    replaces?: { recurringId: number; date: string }
    /** R31: the index of the new series in series whose occurrence the row replaces. */
    series?: number
  }[]
  /** R31: monthly series the import creates (code review, 08.11.2026). */
  series?: { startDate: string; amount: number; description: string; categoryId: number; source: string }[]
}

export interface ImportBatch {
  id: number
  profile: ImportProfile
  profileLabel: string
  createdAt: string
  firstDate: string
  lastDate: string
  count: number
}

export interface ImportResult {
  batch: ImportBatch | null
  created: number
  skippedDuplicates: number
  /** P6: automatic entries the rows replaced. */
  replacedAuto?: number
  /** P6: automatic entries of the accounts of the file within its dates that the file does not contain. */
  missingAuto?: MissingAuto[]
}

/** Reads the file in the backend; nothing is stored (decision 44). */
export function previewImport(file: File): Promise<ImportPreview> {
  const body = new FormData()
  body.append('file', file)
  return request<ImportPreview>('/imports/preview', { method: 'POST', body })
}

export function commitImport(input: ImportRequest): Promise<ImportResult> {
  return request<ImportResult>('/imports', { method: 'POST', body: JSON.stringify(input) })
}

export function fetchImports(): Promise<ImportBatch[]> {
  return request<ImportBatch[]>('/imports')
}

export function deleteImport(id: number): Promise<void> {
  return request<void>(`/imports/${id}`, { method: 'DELETE' })
}

export function useImports() {
  return useQuery({ queryKey: ['imports'], queryFn: fetchImports })
}

export function usePreviewImport() {
  return useMutation({ mutationFn: previewImport })
}

// An import changes bookings, accounts and everything calculated from them
function useInvalidateAfterImport() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all(
      ['imports', 'transactions', 'dashboard', 'reserves', 'history', 'categories', 'bank-accounts', 'booking-suggestion'].map(
        (key) => queryClient.invalidateQueries({ queryKey: [key] }),
      ),
    )
}

export function useCommitImport() {
  const onSuccess = useInvalidateAfterImport()
  return useMutation({ mutationFn: commitImport, onSuccess })
}

export function useDeleteImport() {
  const onSuccess = useInvalidateAfterImport()
  return useMutation({ mutationFn: deleteImport, onSuccess })
}

/** P6: an automatic entry the import did not find in the file. */
export interface MissingAuto {
  id: number
  date: string
  description: string
  amount: number
  accountName: string
}
