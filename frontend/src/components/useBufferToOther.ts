import { useState } from 'react'
import { useCreatePlannedItem, useDeletePlannedItem, useUpdatePlannedItem } from '../api/plan'
import type { Plan, PlannedItem } from '../api/types'

const SONSTIGES = 'Sonstiges'

/** What "Als Puffer in Sonstiges" did, so it can be taken back. */
interface Done {
  amount: number
  /** The budget item before, or null when the button created it. */
  before: PlannedItem | null
  itemId: number
  month: string
}

/**
 * P4 "Als Puffer in Sonstiges": the rest of the month goes to the budget of the catch-all category, from the month
 * shown on. Field test after 0.3.0: the step can be taken back as long as the page stays open ("Rückgängig"): a budget
 * the button created is deleted, a raised one gets its amount back from the same month on.
 */
export function useBufferToOther() {
  const createItem = useCreatePlannedItem()
  const updateItem = useUpdatePlannedItem()
  const deleteItem = useDeletePlannedItem()
  const [done, setDone] = useState<Done | null>(null)

  async function put(plan: Plan, categoryId: number, month: string) {
    const existing = plan.items.find((item) => item.group === 'BUDGET' && item.categoryId === categoryId) ?? null
    const amount = plan.unplanned
    if (existing) {
      const saved = await updateItem.mutateAsync({
        id: existing.id,
        input: budgetInput(existing, -(existing.monthlyShare + amount), month),
      })
      setDone({ amount, before: existing, itemId: saved.id, month })
    } else {
      const saved = await createItem.mutateAsync({
        name: SONSTIGES,
        amount: -amount,
        categoryId,
        rhythmUnit: null,
        rhythmCount: null,
        anchorDate: null,
        validFrom: month,
      })
      setDone({ amount, before: null, itemId: saved.id, month })
    }
  }

  async function undo() {
    if (done === null) {
      return
    }
    if (done.before === null) {
      await deleteItem.mutateAsync({ id: done.itemId })
    } else {
      await updateItem.mutateAsync({ id: done.itemId, input: budgetInput(done.before, done.before.amount, done.month) })
    }
    setDone(null)
  }

  return {
    put,
    undo,
    /** The amount moved to Sonstiges while the page is open, null before or after "Rückgängig". */
    moved: done?.amount ?? null,
    forget: () => setDone(null),
    busy: createItem.isPending || updateItem.isPending || deleteItem.isPending,
  }
}

function budgetInput(item: PlannedItem, amount: number, month: string) {
  return {
    name: item.name,
    amount,
    categoryId: item.categoryId,
    accountId: item.accountId,
    rhythmUnit: null,
    rhythmCount: null,
    anchorDate: null,
    entryMode: item.entryMode,
    validFrom: month,
  }
}
