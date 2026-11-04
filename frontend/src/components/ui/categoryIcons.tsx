import type { ReactNode } from 'react'

// Data for CategoryIcon, in its own file so the component file only exports components (fast refresh).

/** Line icon per key of the fixed list (US28, spec section 4), with the German name for the picker. */
export const ICONS = {
  home: { label: 'Haus', path: <path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z" /> },
  bolt: { label: 'Blitz', path: <path d="M13 2 4 14h7l-1 8 9-12h-7z" /> },
  shield: { label: 'Schild', path: <path d="M12 3 4 6v6c0 5 3.5 8 8 9 4.5-1 8-4 8-9V6z" /> },
  car: {
    label: 'Auto',
    path: (
      <>
        <path d="M5 17H3v-5l2-5h14l2 5v5h-2M9 17h6M3 12h18" />
        <circle cx="7" cy="17" r="2" />
        <circle cx="17" cy="17" r="2" />
      </>
    ),
  },
  card: {
    label: 'Karte',
    path: (
      <>
        <rect x="2" y="5" width="20" height="14" rx="2" />
        <path d="M2 10h20M6 15h4" />
      </>
    ),
  },
  heart: { label: 'Herz', path: <path d="M12 20s-7-4.5-9-9a4.5 4.5 0 0 1 9-3 4.5 4.5 0 0 1 9 3c-2 4.5-9 9-9 9z" /> },
  cart: {
    label: 'Einkaufswagen',
    path: (
      <>
        <path d="M3 4h2l2.5 11h11L21 8H6.5" />
        <circle cx="9" cy="19" r="1.5" />
        <circle cx="17" cy="19" r="1.5" />
      </>
    ),
  },
  music: {
    label: 'Musik',
    path: (
      <>
        <path d="M9 18V5l11-2v13" />
        <circle cx="6" cy="18" r="3" />
        <circle cx="17" cy="16" r="3" />
      </>
    ),
  },
  suitcase: {
    label: 'Koffer',
    path: (
      <>
        <rect x="6" y="6" width="12" height="14" rx="2" />
        <path d="M9 6V3h6v3M10 10v6M14 10v6M9 20v2M15 20v2" />
      </>
    ),
  },
  bag: { label: 'Tasche', path: <path d="M5 8h14l-1 13H6zM9 8V6a3 3 0 0 1 6 0v2" /> },
  gift: {
    label: 'Geschenk',
    path: (
      <>
        <rect x="3" y="8" width="18" height="4" rx="1" />
        <path d="M5 12v9h14v-9M12 8v13M12 8C10 4 6 4 6 6.5S10 8 12 8zm0 0c2-4 6-4 6-1.5S14 8 12 8z" />
      </>
    ),
  },
  pulse: { label: 'Puls', path: <path d="M3 12h4l3-7 4 14 3-7h4" /> },
  trend: { label: 'Trend', path: <path d="m3 17 6-6 4 4 8-8M15 7h6v6" /> },
  briefcase: {
    label: 'Aktentasche',
    path: (
      <>
        <rect x="3" y="7" width="18" height="13" rx="2" />
        <path d="M8 7V5a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2M3 13h18" />
      </>
    ),
  },
  wallet: {
    label: 'Geldbörse',
    path: (
      <>
        <path d="M19 7V5a1 1 0 0 0-1-1H5a2 2 0 0 0 0 4h15a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a2 2 0 0 1-2-2V6" />
        <circle cx="17" cy="14" r="1" />
      </>
    ),
  },
  laptop: {
    label: 'Laptop',
    path: (
      <>
        <rect x="4" y="5" width="16" height="10" rx="1" />
        <path d="M2 19h20" />
      </>
    ),
  },
  receipt: { label: 'Beleg', path: <path d="M6 3h12v18l-3-2-3 2-3-2-3 2zM9 8h6M9 12h6M9 16h3" /> },
  coins: {
    label: 'Münzen',
    path: (
      <>
        <circle cx="9" cy="9" r="6" />
        <path d="M18.1 10.9a6 6 0 1 1-7.2 7.2" />
      </>
    ),
  },
  landmark: { label: 'Gebäude', path: <path d="M3 21h18M5 21v-9M9.5 21v-9M14.5 21v-9M19 21v-9M3 10l9-6 9 6z" /> },
  plus: {
    label: 'Plus',
    path: (
      <>
        <circle cx="12" cy="12" r="9" />
        <path d="M12 8v8M8 12h8" />
      </>
    ),
  },
  tag: {
    label: 'Etikett',
    path: (
      <>
        <path d="M3 12V4a1 1 0 0 1 1-1h8l9 9-9 9z" />
        <circle cx="7.5" cy="7.5" r="1.5" />
      </>
    ),
  },
} satisfies Record<string, { label: string; path: ReactNode }>

export type CategoryIconKey = keyof typeof ICONS

/** All keys in the order of the picker; must match the backend list (CategoryIcons). */
export const CATEGORY_ICON_KEYS = Object.keys(ICONS) as CategoryIconKey[]

/** The list can grow (section 4): a key this frontend does not know yet is drawn as "tag". */
export function knownCategoryIcon(icon: string): CategoryIconKey {
  return icon in ICONS ? (icon as CategoryIconKey) : 'tag'
}

/** German name of an icon, for labels in the picker. */
export function categoryIconLabel(icon: string): string {
  return ICONS[knownCategoryIcon(icon)].label
}
