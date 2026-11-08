/** The values of the shared filter bar of Transaktionen and the import (US26, US33); '' means no filter. */
export interface Filters {
  query: string
  category: string
  account: string
  kind: string
}

export const NO_FILTERS: Filters = { query: '', category: '', account: '', kind: '' }
