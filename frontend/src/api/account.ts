import { useMutation, useQueryClient } from '@tanstack/react-query'
import { request } from './client'
import type { ChangeEmailInput, ChangePasswordInput, IstStage, Me } from './types'

/** US16 */
export function changePassword(input: ChangePasswordInput): Promise<void> {
  return request<void>('/account/password', { method: 'PUT', body: JSON.stringify(input) })
}

export function changeDisplayName(displayName: string): Promise<Me> {
  return request<Me>('/account/display-name', { method: 'PUT', body: JSON.stringify({ displayName }) })
}

export function changeEmail(input: ChangeEmailInput): Promise<Me> {
  return request<Me>('/account/email', { method: 'PUT', body: JSON.stringify(input) })
}

/**
 * R35: the Ist stage; it only changes what the app shows. Decision 90: when leaving stage 1, autoEntries asks to enter
 * income and fixed costs automatically from entriesFrom (ISO day) on; switching to stage 1 makes every item manual.
 */
export interface ChangeStageInput {
  stage: IstStage
  autoEntries?: boolean
  entriesFrom?: string
}

export function changeStage(input: ChangeStageInput): Promise<Me> {
  return request<Me>('/account/stage', { method: 'PUT', body: JSON.stringify(input) })
}

/** US40: the setup wizard is done (finished or put off) or starts again. */
export function changeSetup(done: boolean): Promise<Me> {
  return request<Me>('/account/setup', { method: 'PUT', body: JSON.stringify({ done }) })
}

/** All own data; the frontend only saves it as a file. */
export function exportAccount(): Promise<unknown> {
  return request<unknown>('/account/export')
}

export function deleteAccount(password: string): Promise<void> {
  return request<void>('/account', { method: 'DELETE', body: JSON.stringify({ password }) })
}

export function useChangePassword() {
  return useMutation({ mutationFn: changePassword })
}

// Name and e-mail show in the header, the account section and the user overview
function useInvalidateAfterAccountChange() {
  const queryClient = useQueryClient()
  return () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ['me'] }),
      queryClient.invalidateQueries({ queryKey: ['users'] }),
    ])
}

export function useChangeDisplayName() {
  const onSuccess = useInvalidateAfterAccountChange()
  return useMutation({ mutationFn: changeDisplayName, onSuccess })
}

export function useChangeEmail() {
  const onSuccess = useInvalidateAfterAccountChange()
  return useMutation({ mutationFn: changeEmail, onSuccess })
}

// The new user goes straight into the cache, so navigation and overview change at once. Field test after 0.3.0: a
// reload of the user still running (e.g. on returning to the tab, frequent on the phone) is cancelled first; its older
// answer would otherwise overwrite the new one, and "Einrichtung abschließen" led back to the start of the setup.
function useSetMe() {
  const queryClient = useQueryClient()
  return {
    cancel: () => queryClient.cancelQueries({ queryKey: ['me'] }),
    set: async (me: Me) => {
      await queryClient.cancelQueries({ queryKey: ['me'] })
      queryClient.setQueryData(['me'], me)
    },
  }
}

export function useChangeStage() {
  const queryClient = useQueryClient()
  const setMe = useSetMe()
  return useMutation({
    mutationFn: changeStage,
    onMutate: setMe.cancel,
    onSuccess: async (me) => {
      await setMe.set(me)
      // Decision 90: the switch may change how items are entered and enter bookings
      void queryClient.invalidateQueries({ predicate: (query) => query.queryKey[0] !== 'me' })
    },
  })
}

export function useChangeSetup() {
  const setMe = useSetMe()
  return useMutation({ mutationFn: changeSetup, onMutate: setMe.cancel, onSuccess: setMe.set })
}

export function useExportAccount() {
  return useMutation({ mutationFn: exportAccount })
}

// Afterwards there is no user any more, so the login page shows
export function useDeleteAccount() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: deleteAccount, onSuccess: () => queryClient.resetQueries() })
}
