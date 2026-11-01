import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App.tsx'
import { ApiError } from './api/client'
import './index.css'

// A 401 means the session expired (30 days without use); asking for the user again shows the login page
function onError(error: Error) {
  if (error instanceof ApiError && error.problem.status === 401) {
    void queryClient.invalidateQueries({ queryKey: ['me'] })
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError }),
  mutationCache: new MutationCache({ onError }),
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
