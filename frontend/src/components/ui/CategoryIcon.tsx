import { ICONS, knownCategoryIcon } from './categoryIcons'

/** The icon of a category in a small tinted circle; decorative, the category name stands next to it. */
export function CategoryIcon({ icon, size = 18 }: { icon: string; size?: number }) {
  const key = knownCategoryIcon(icon)
  return (
    <span className="category-icon" data-icon={key}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={2}
        strokeLinecap="round"
        strokeLinejoin="round"
        aria-hidden="true"
        focusable="false"
      >
        {ICONS[key].path}
      </svg>
    </span>
  )
}
