import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { errorText, request } from './client'
import type { Passkey } from './types'

/**
 * US44: passkeys. The backend sends the WebAuthn options as JSON with base64url bytes; the browser API needs
 * ArrayBuffers and answers with them, so this file converts both ways (like the script of Spring Security).
 */

/** Whether this browser can use passkeys at all; the backend decides the rest. */
export function passkeysSupported(): boolean {
  return typeof window !== 'undefined' && typeof window.PublicKeyCredential === 'function'
}

function decode(base64url: string): ArrayBuffer {
  const binary = window.atob(base64url.replace(/-/g, '+').replace(/_/g, '/'))
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes.buffer
}

function encode(buffer: ArrayBuffer): string {
  let binary = ''
  for (const byte of new Uint8Array(buffer)) {
    binary += String.fromCharCode(byte)
  }
  return window.btoa(binary).replace(/=/g, '').replace(/\+/g, '-').replace(/\//g, '_')
}

interface CredentialDescriptorJson {
  type: 'public-key'
  id: string
  transports?: AuthenticatorTransport[]
}

interface CreationOptionsJson extends Omit<PublicKeyCredentialCreationOptions, 'challenge' | 'user' | 'excludeCredentials'> {
  challenge: string
  user: { id: string; name: string; displayName: string }
  excludeCredentials?: CredentialDescriptorJson[]
}

interface RequestOptionsJson extends Omit<PublicKeyCredentialRequestOptions, 'challenge' | 'allowCredentials'> {
  challenge: string
  allowCredentials?: CredentialDescriptorJson[]
}

function descriptors(list: CredentialDescriptorJson[] | undefined): PublicKeyCredentialDescriptor[] {
  return (list ?? []).map((item) => ({ ...item, id: decode(item.id) }))
}

/** A readable German text for the errors of the browser API; errors of the backend as usual (field error first). */
export function passkeyErrorText(error: unknown, fallback: string): string {
  if (error instanceof DOMException) {
    if (error.name === 'NotAllowedError' || error.name === 'AbortError') {
      return 'Der Vorgang wurde abgebrochen oder hat zu lange gedauert.'
    }
    if (error.name === 'InvalidStateError') {
      return 'Auf diesem Gerät gibt es schon einen Passkey für dein Konto.'
    }
    if (error.name === 'SecurityError') {
      return 'Passkeys gehen nur über die HTTPS-Adresse der App.'
    }
    return fallback
  }
  return errorText(error, fallback)
}

/** Adds a passkey on this device: the password first (decision 96), then the dialog of the browser. */
export async function addPasskey({ password, label }: { password: string; label: string }): Promise<Passkey> {
  const options = await request<CreationOptionsJson>('/account/passkeys/options', {
    method: 'POST',
    body: JSON.stringify({ password }),
  })
  const created = (await navigator.credentials.create({
    publicKey: {
      ...options,
      challenge: decode(options.challenge),
      user: { ...options.user, id: decode(options.user.id) },
      excludeCredentials: descriptors(options.excludeCredentials),
    },
  })) as PublicKeyCredential | null
  if (!created) {
    throw new DOMException('No credential', 'NotAllowedError')
  }
  const response = created.response as AuthenticatorAttestationResponse
  const credential = {
    id: created.id,
    rawId: encode(created.rawId),
    type: created.type,
    response: {
      attestationObject: encode(response.attestationObject),
      clientDataJSON: encode(response.clientDataJSON),
      transports: typeof response.getTransports === 'function' ? response.getTransports() : [],
    },
    clientExtensionResults: created.getClientExtensionResults(),
    authenticatorAttachment: created.authenticatorAttachment,
  }
  return request<Passkey>('/account/passkeys', { method: 'POST', body: JSON.stringify({ label, credential }) })
}

/** US44: signs in with a passkey of this domain; the browser shows the passkeys it has. */
export async function signInWithPasskey(): Promise<void> {
  const options = await request<RequestOptionsJson>('/auth/passkey/options', { method: 'POST' })
  const found = (await navigator.credentials.get({
    publicKey: {
      ...options,
      challenge: decode(options.challenge),
      allowCredentials: descriptors(options.allowCredentials),
    },
  })) as PublicKeyCredential | null
  if (!found) {
    throw new DOMException('No credential', 'NotAllowedError')
  }
  const response = found.response as AuthenticatorAssertionResponse
  const body = {
    id: found.id,
    rawId: encode(found.rawId),
    type: found.type,
    response: {
      authenticatorData: encode(response.authenticatorData),
      clientDataJSON: encode(response.clientDataJSON),
      signature: encode(response.signature),
      userHandle: response.userHandle ? encode(response.userHandle) : undefined,
    },
    clientExtensionResults: found.getClientExtensionResults(),
    authenticatorAttachment: found.authenticatorAttachment,
  }
  return request<void>('/auth/passkey', { method: 'POST', body: JSON.stringify(body) })
}

export function fetchPasskeys(): Promise<Passkey[]> {
  return request<Passkey[]>('/account/passkeys')
}

export function renamePasskey({ id, label }: { id: number; label: string }): Promise<Passkey> {
  return request<Passkey>(`/account/passkeys/${id}`, { method: 'PUT', body: JSON.stringify({ label }) })
}

export function deletePasskey(id: number): Promise<void> {
  return request<void>(`/account/passkeys/${id}`, { method: 'DELETE' })
}

export function usePasskeys() {
  return useQuery({ queryKey: ['passkeys'], queryFn: fetchPasskeys })
}

function useInvalidatePasskeys() {
  const queryClient = useQueryClient()
  return () => queryClient.invalidateQueries({ queryKey: ['passkeys'] })
}

export function useAddPasskey() {
  const onSuccess = useInvalidatePasskeys()
  return useMutation({ mutationFn: addPasskey, onSuccess })
}

export function useRenamePasskey() {
  const onSuccess = useInvalidatePasskeys()
  return useMutation({ mutationFn: renamePasskey, onSuccess })
}

export function useDeletePasskey() {
  const onSuccess = useInvalidatePasskeys()
  return useMutation({ mutationFn: deletePasskey, onSuccess })
}

// Like the sign-in with the password: afterwards all data belongs to the user, so every query starts over
export function useSignInWithPasskey() {
  const queryClient = useQueryClient()
  return useMutation({ mutationFn: signInWithPasskey, onSuccess: () => queryClient.resetQueries() })
}
