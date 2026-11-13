import { useState } from 'react'
import { useBankAccounts, useCreateBankAccount } from '../api/bankAccounts'
import { InlineCreate } from './ui/InlineCreate'

const NEW = 'new'

interface Props {
  /** The chosen account id as string; '' is the default account, or the empty option when one is given. */
  value: string
  onChange: (value: string) => void
  /** An option with the value '' before the accounts, e.g. "nicht in der App". */
  empty?: string
  /** An account that may not be chosen here, e.g. the source of a transfer. */
  exclude?: string
  ariaLabel?: string
  labelledBy?: string
  invalid?: boolean
}

/**
 * 27f4: the one account selection of the app. It hides archived accounts (except the chosen one) and ends with
 * "+ Neues Konto …", which creates the account (Alltag) right here and chooses it (08.11.2026).
 */
export function AccountSelect({ value, onChange, empty, exclude, ariaLabel, labelledBy, invalid }: Props) {
  const accounts = useBankAccounts().data ?? []
  const create = useCreateBankAccount()
  const [creating, setCreating] = useState(false)
  const defaultAccount = accounts.find((a) => a.defaultAccount)
  const shown = value === '' && empty === undefined ? String(defaultAccount?.id ?? '') : value
  const options = accounts.filter((a) => (!a.archived || String(a.id) === shown) && String(a.id) !== exclude)

  async function handleCreate(name: string) {
    const account = await create.mutateAsync({ name })
    setCreating(false)
    onChange(String(account.id))
  }

  return (
    <>
      <select
        aria-label={ariaLabel}
        aria-labelledby={labelledBy}
        value={shown}
        aria-invalid={invalid ? true : undefined}
        onChange={(e) => {
          if (e.target.value === NEW) {
            setCreating(true)
          } else {
            setCreating(false)
            onChange(e.target.value)
          }
        }}
      >
        {empty !== undefined && <option value="">{empty}</option>}
        {options.map((a) => (
          <option key={a.id} value={a.id}>
            {a.name}
          </option>
        ))}
        <option value={NEW}>+ Neues Konto …</option>
      </select>
      {creating && (
        <InlineCreate
          label="Neues Konto"
          placeholder="Name des Kontos"
          onCreate={handleCreate}
          onCancel={() => setCreating(false)}
        />
      )}
    </>
  )
}
