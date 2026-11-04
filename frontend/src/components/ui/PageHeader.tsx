import type { ReactNode } from 'react'

/** Page title with the page's own controls on the right; the controls wrap below on narrow screens. */
export function PageHeader({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="page-head">
      <h1>{title}</h1>
      {children && <div className="page-actions">{children}</div>}
    </div>
  )
}
