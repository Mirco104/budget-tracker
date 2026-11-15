// Ticket #129: the app has one version; frontend/package.json and backend/pom.xml must name the same.
// Usage: node scripts/check-version.mjs (from any directory)
import { readFileSync } from 'node:fs'

const root = new URL('../', import.meta.url)
const frontend = JSON.parse(readFileSync(new URL('frontend/package.json', root), 'utf-8')).version
const pom = readFileSync(new URL('backend/pom.xml', root), 'utf-8')
// The first <version> after </parent> is the version of the project
const backend = pom.split('</parent>')[1]?.match(/<version>([^<]+)<\/version>/)?.[1]

if (frontend !== backend) {
  console.error(`Version mismatch: frontend/package.json ${frontend}, backend/pom.xml ${backend}`)
  process.exit(1)
}
console.log(`Version ${frontend}`)
