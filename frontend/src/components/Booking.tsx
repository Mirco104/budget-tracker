import { Icon } from './ui/Icon'

/** "+ Buchung" in the page header; hidden on phones, where the round button takes its place (decision 28). */
export function AddBookingButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className="primary add-booking" onClick={onClick}>
      <Icon name="plus" />
      Buchung
    </button>
  )
}

/** Round button at the bottom right on phones (US27). */
export function AddBookingFab({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" className="fab" aria-label="Neue Buchung" onClick={onClick}>
      <Icon name="plus" size={26} />
    </button>
  )
}
