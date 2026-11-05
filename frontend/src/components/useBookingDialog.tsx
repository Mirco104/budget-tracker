import { useState } from 'react'
import type { Category, Transaction } from '../api/types'
import { BookingDialog } from './BookingDialog'
import { Toast } from './ui/Toast'

interface Options {
  categories: Category[] | undefined
  defaultDate: string
}

/** Opens the booking dialog for a new or an existing entry and shows the confirmation afterwards. */
export function useBookingDialog({ categories, defaultDate }: Options) {
  // undefined: closed; null: new booking
  const [entry, setEntry] = useState<Transaction | null | undefined>(undefined)
  const [toast, setToast] = useState<string | null>(null)

  const element = (
    <>
      {entry !== undefined && categories && (
        <BookingDialog
          categories={categories}
          entry={entry}
          defaultDate={defaultDate}
          onClose={() => setEntry(undefined)}
          onDone={(message) => {
            setEntry(undefined)
            setToast(message)
          }}
        />
      )}
      {toast && <Toast message={toast} onDone={() => setToast(null)} />}
    </>
  )

  return {
    openNew: () => setEntry(null),
    openEdit: (selected: Transaction) => setEntry(selected),
    element,
  }
}
