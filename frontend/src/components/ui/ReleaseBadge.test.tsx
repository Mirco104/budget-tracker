import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { APP_VERSION, releaseStage } from '../../version'
import { ReleaseBadge } from './ReleaseBadge'

describe('ReleaseBadge', () => {
  it('names the stage of a pre-release', () => {
    render(<ReleaseBadge version="0.1.0-alpha" />)
    expect(screen.getByText('Alpha')).toHaveAttribute('title', expect.stringContaining('Version 0.1.0-alpha'))
  })

  it('shows nothing for a release', () => {
    const { container } = render(<ReleaseBadge version="1.0.0" />)
    expect(container).toBeEmptyDOMElement()
  })

  it('reads the stage from the version', () => {
    expect(releaseStage('0.2.0-beta.1')).toBe('Beta')
    expect(releaseStage('0.3.0-rc')).toBe('rc')
    expect(releaseStage('1.0.0')).toBeNull()
  })

  it('gets the version from package.json at build time', () => {
    expect(APP_VERSION).toMatch(/^\d+\.\d+\.\d+/)
  })
})
