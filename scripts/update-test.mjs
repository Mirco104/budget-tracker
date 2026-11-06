// Update test (spec section 7): fills a running app with fictional test data, and later checks that an
// update kept the data. Used by the CI job "update-test"; works the same against a local docker compose.
//
//   node scripts/update-test.mjs seed  <snapshot.json>   create the data and save what the app shows
//   node scripts/update-test.mjs check <snapshot.json>   compare what the app shows now with the snapshot
//
// Environment: ADMIN_EMAIL and ADMIN_PASSWORD (the system admin of the app), BASE_URL (default
// http://localhost:8080). Only stable endpoints are used, because "seed" runs against the old version.

import { readFileSync, writeFileSync } from 'node:fs'

const baseUrl = process.env.BASE_URL ?? 'http://localhost:8080'
const month = '2026-10'
const personA = { email: process.env.ADMIN_EMAIL, password: process.env.ADMIN_PASSWORD }
const personB = { email: 'person-b@example.test', displayName: 'Person B', password: 'test-only-password-b' }

/** A signed-in browser: keeps the session and the CSRF cookie and sends the token on writing calls. */
class Client {
  cookies = new Map()

  async request(method, path, body) {
    const headers = { Cookie: [...this.cookies].map(([name, value]) => `${name}=${value}`).join('; ') }
    if (method !== 'GET') {
      headers['X-XSRF-TOKEN'] = this.cookies.get('XSRF-TOKEN') ?? ''
    }
    if (body !== undefined) {
      headers['Content-Type'] = 'application/json'
    }
    const response = await fetch(baseUrl + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
    for (const cookie of response.headers.getSetCookie()) {
      const [pair] = cookie.split(';')
      const index = pair.indexOf('=')
      this.cookies.set(pair.slice(0, index), pair.slice(index + 1))
    }
    if (!response.ok) {
      throw new Error(`${method} ${path} -> ${response.status} ${await response.text()}`)
    }
    return response.status === 204 ? null : response.json()
  }

  // Only fetches the CSRF cookie; 401 is expected without a session
  async fetchCsrfToken() {
    await this.request('GET', '/api/auth/me').catch(() => {})
    return this
  }

  async signIn({ email, password }) {
    await this.fetchCsrfToken()
    await this.request('POST', '/api/auth/login', { email, password })
    return this
  }
}

async function categoryId(client, name) {
  const categories = await client.request('GET', `/api/categories?month=${month}`)
  return categories.find((category) => category.name === name).id
}

async function seed() {
  const a = await new Client().signIn(personA)
  await a.request('PUT', `/api/categories/${await categoryId(a, 'Lebensmittel')}/target?month=${month}`, {
    targetAmount: 400,
  })
  // Euro only: a percent target becomes "no target" with decision 36 (migration V6, own test)
  await a.request('PUT', `/api/categories/${await categoryId(a, 'Freizeit')}/target?month=${month}`, {
    targetAmount: 150,
  })
  const transactions = [
    [3000, 'Gehalt', '2026-10-01', 'Gehalt/Lohn (netto)'],
    [-120.3, 'Wocheneinkauf', '2026-10-04', 'Lebensmittel'],
    [-200.2, 'Großeinkauf', '2026-10-18', 'Lebensmittel'],
    [30, 'Erstattung Pfand', '2026-10-20', 'Lebensmittel'],
    [-45.99, 'Tanken', '2026-10-12', 'Mobilität'],
  ]
  for (const [amount, description, date, category] of transactions) {
    await a.request('POST', '/api/transactions', { amount, description, date, categoryId: await categoryId(a, category) })
  }
  await a.request('POST', '/api/recurring-transactions', {
    amount: -900, description: 'Miete', categoryId: await categoryId(a, 'Wohnen'), interval: 'MONTHLY', startDate: '2026-01-01',
  })
  await a.request('POST', '/api/recurring-transactions', {
    amount: -240, description: 'Haftpflicht', categoryId: await categoryId(a, 'Versicherungen'), interval: 'YEARLY', startDate: '2026-10-15',
  })

  // A second user with own data, to see that the data stays separated (R15)
  const { link } = await a.request('POST', '/api/invitations')
  const token = new URL(link).searchParams.get('einladung')
  const invited = await new Client().fetchCsrfToken()
  await invited.request('POST', `/api/invitations/${token}/accept`, personB)
  const b = await new Client().signIn(personB)
  await b.request('POST', '/api/transactions', {
    amount: -55, description: 'Kino', date: '2026-10-09', categoryId: await categoryId(b, 'Freizeit'),
  })

  return snapshot()
}

/** What the app shows for both users; must be the same before and after an update. */
async function snapshot() {
  const result = {}
  for (const [name, credentials] of [['Person A', personA], ['Person B', personB]]) {
    const client = await new Client().signIn(credentials)
    const dashboard = await client.request('GET', `/api/dashboard?month=${month}`)
    const entries = await client.request('GET', `/api/transactions?month=${month}`)
    const reserves = await client.request('GET', `/api/reserves?month=${month}`)
    const categories = await client.request('GET', `/api/categories?month=${month}`)
    result[name] = {
      incomeTotal: dashboard.incomeTotal,
      expenses: dashboard.expenses.actual,
      recurring: dashboard.expenses.recurring,
      // The stored targets in euro (R3; percent targets are gone since decision 36)
      targets: categories
        .filter((category) => category.targetAmount !== null)
        .map((category) => `${category.name} ${category.targetAmount}`)
        .sort(),
      surplus: dashboard.surplus,
      entries: entries.map((entry) => `${entry.date} ${entry.amount} ${entry.description}`).sort(),
      monthlyReserve: reserves.monthlyReserve,
    }
  }
  return result
}

const [command, file] = process.argv.slice(2)
if (!personA.email || !personA.password || !file || !['seed', 'check'].includes(command)) {
  console.error('Usage: ADMIN_EMAIL=… ADMIN_PASSWORD=… node scripts/update-test.mjs seed|check <snapshot.json>')
  process.exit(2)
}

if (command === 'seed') {
  const before = await seed()
  writeFileSync(file, JSON.stringify(before, null, 2))
  console.log('Test data created:\n' + JSON.stringify(before, null, 2))
} else {
  const before = readFileSync(file, 'utf8').trim()
  const after = JSON.stringify(await snapshot(), null, 2)
  if (after !== before) {
    console.error(`The data changed with the update.\nBefore:\n${before}\nAfter:\n${after}`)
    process.exit(1)
  }
  // US28: categories created before the icons got the icon of their default name from the migration
  const categories = await (await new Client().signIn(personA)).request('GET', `/api/categories?month=${month}`)
  const wohnen = categories.find((category) => category.name === 'Wohnen')
  if (wohnen?.icon !== 'home') {
    console.error(`Expected the icon "home" for Wohnen after the update, got ${JSON.stringify(wohnen?.icon)}.`)
    process.exit(1)
  }
  console.log('Data unchanged after the update.')
}
