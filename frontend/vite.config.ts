/// <reference types="vitest/config" />
import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Ticket #129: the version of the app has one source, package.json (the CI checks that pom.xml has the same)
const version = (JSON.parse(readFileSync(new URL('./package.json', import.meta.url), 'utf-8')) as { version: string })
  .version

// Ticket #159: the changes of every version, shown on a click on the version (CHANGELOG.md in the root of the repository;
// the Docker build copies it next to the frontend)
const changelog = readFileSync(new URL('../CHANGELOG.md', import.meta.url), 'utf-8')

// Ticket #129: the commit of the build; in the Docker image there is no .git, there it comes from APP_COMMIT
function commit(): string {
  if (process.env.APP_COMMIT) {
    return process.env.APP_COMMIT
  }
  try {
    return execSync('git rev-parse --short HEAD', { stdio: ['ignore', 'pipe', 'ignore'] }).toString().trim()
  } catch {
    return ''
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  define: {
    __APP_VERSION__: JSON.stringify(version),
    __APP_COMMIT__: JSON.stringify(commit()),
    __APP_CHANGELOG__: JSON.stringify(changelog),
  },
  server: {
    proxy: {
      // Keeps the Host header of the frontend, so links the backend builds (invitations, US15) point here
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
  },
})
