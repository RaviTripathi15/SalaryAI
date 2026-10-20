import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('sign-in page', () => {
  it('renders required organization and account fields', () => {
    const markup = renderToStaticMarkup(<App />)

    expect(markup).toContain('Welcome back')
    expect(markup).toContain('id="organization"')
    expect(markup).toContain('id="email"')
    expect(markup).toContain('type="password"')
    expect(markup).toContain('Sign in')
  })
})
