import { useEffect, useRef } from 'react'

/** A short confirmation at the bottom of the screen (US27); it disappears after a few seconds. */
export function Toast({ message, onDone }: { message: string; onDone: () => void }) {
  const done = useRef(onDone)

  useEffect(() => {
    done.current = onDone
  }, [onDone])

  useEffect(() => {
    const timer = window.setTimeout(() => done.current(), 3000)
    return () => window.clearTimeout(timer)
  }, [message])

  return (
    <div className="toast" role="status">
      {message}
    </div>
  )
}
