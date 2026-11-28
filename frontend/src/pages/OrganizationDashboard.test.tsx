
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { OrganizationApiError } from '../api/organizationApi'
import type { OrganizationLoginResponse } from '../api/organizationApi'
import OrganizationDashboard from './OrganizationDashboard'

const apiMocks = vi.hoisted(() => ({
  getCurrentOrganization: vi.fn(),
  logoutOrganization: vi.fn(),
}))

vi.mock('../api/organizationApi', async () => {
  const actual = await vi.importActual<
    typeof import('../api/organizationApi')
  >('../api/organizationApi')

  return {
    ...actual,
    getCurrentOrganization: apiMocks.getCurrentOrganization,
    logoutOrganization: apiMocks.logoutOrganization,
  }
})

const organization: OrganizationLoginResponse = {
  id: 7,
  name: 'Acme',
  email: 'acme@example.com',
  role: 'ORGANIZATION_ADMIN',
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void

  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })

  return { promise, resolve, reject }
}

async function flush() {
  await act(async () => {
    await Promise.resolve()
  })
}

function renderDashboard() {
  const container = document.createElement('div')
  document.body.appendChild(container)

  const root = createRoot(container)

  act(() => {
    root.render(
      <MemoryRouter initialEntries={['/organization/dashboard']}>
        <Routes>
          <Route
            path="/organization/dashboard"
            element={<OrganizationDashboard />}
          />
          <Route path="/" element={<p>Sign in page</p>} />
        </Routes>
      </MemoryRouter>,
    )
  })

  return {
    container,
    unmount() {
      act(() => root.unmount())
    },
  }
}

function logoutButton(container: HTMLElement): HTMLButtonElement {
  const button = container.querySelector('button')

  if (!button) {
    throw new Error('Logout button was not rendered')
  }

  return button
}

beforeEach(() => {
  apiMocks.getCurrentOrganization.mockReset()
  apiMocks.logoutOrganization.mockReset()
})

afterEach(() => {
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('OrganizationDashboard session verification', () => {
  it('shows a loading state while checking the session', async () => {
    const pending = deferred<OrganizationLoginResponse>()

    apiMocks.getCurrentOrganization.mockReturnValue(pending.promise)

    const view = renderDashboard()

    expect(view.container.textContent).toContain(
      'Checking your organization session...',
    )
    expect(view.container.querySelector('button')).toBeNull()

    view.unmount()
    pending.resolve(organization)
    await flush()
  })

  it('renders server session data after refresh with no router state', async () => {
    apiMocks.getCurrentOrganization.mockResolvedValue(organization)

    const view = renderDashboard()
    await flush()

    expect(apiMocks.getCurrentOrganization).toHaveBeenCalledTimes(1)
    expect(view.container.textContent).toContain('Acme')
    expect(view.container.textContent).toContain('acme@example.com')
    expect(view.container.textContent).toContain('ORGANIZATION_ADMIN')
    expect(view.container.textContent).not.toContain('Sign in page')
    expect(
      view.container.querySelector(
        'a[href="/organization/employees"]',
      )?.textContent,
    ).toContain('Employee management')

    view.unmount()
  })

  it('redirects to organization login on 401', async () => {
    apiMocks.getCurrentOrganization.mockRejectedValue(
      new OrganizationApiError(
        401,
        'Your organization session is not active. Please sign in again.',
      ),
    )

    const view = renderDashboard()
    await flush()
    await flush()

    expect(view.container.textContent).toContain('Sign in page')
    expect(view.container.textContent).not.toContain('Acme')

    view.unmount()
  })

  it('shows a safe error state on 403 without rendering the dashboard', async () => {
    apiMocks.getCurrentOrganization.mockRejectedValue(
      new OrganizationApiError(
        403,
        'This account is not authorized for the organization workspace.',
      ),
    )

    const view = renderDashboard()
    await flush()

    expect(view.container.querySelector('[role="alert"]')?.textContent).toContain(
      'This account is not authorized for the organization workspace.',
    )
    expect(view.container.textContent).not.toContain('acme@example.com')
    expect(view.container.textContent).not.toContain('Sign in page')

    view.unmount()
  })

  it('shows a safe error state on an unexpected session failure', async () => {
    apiMocks.getCurrentOrganization.mockRejectedValue(
      new Error('ECONNRESET internal backend detail'),
    )

    const view = renderDashboard()
    await flush()

    expect(view.container.querySelector('[role="alert"]')?.textContent).toContain(
      'Unable to verify your organization session. Please try again.',
    )
    expect(view.container.textContent).not.toContain('ECONNRESET')

    view.unmount()
  })

  it('does not update state after unmount', async () => {
    const pending = deferred<OrganizationLoginResponse>()

    apiMocks.getCurrentOrganization.mockReturnValue(pending.promise)

    const view = renderDashboard()
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    view.unmount()

    await act(async () => {
      pending.resolve(organization)
      await Promise.resolve()
    })

    expect(view.container.textContent).not.toContain('Acme')

    const unmountWarnings = errorSpy.mock.calls.filter(([message]) =>
      String(message).includes('unmounted'),
    )

    expect(unmountWarnings).toHaveLength(0)
  })
})

describe('OrganizationDashboard logout', () => {
  it('navigates to organization login after successful logout', async () => {
    apiMocks.getCurrentOrganization.mockResolvedValue(organization)
    apiMocks.logoutOrganization.mockResolvedValue(undefined)

    const view = renderDashboard()
    await flush()

    await act(async () => {
      logoutButton(view.container).click()
      await Promise.resolve()
    })
    await flush()

    expect(apiMocks.logoutOrganization).toHaveBeenCalledTimes(1)
    expect(view.container.textContent).toContain('Sign in page')

    view.unmount()
  })

  it('returns to login when logout reports 401', async () => {
    apiMocks.getCurrentOrganization.mockResolvedValue(organization)
    apiMocks.logoutOrganization.mockRejectedValue(
      new OrganizationApiError(
        401,
        'Your organization session is not active. Please sign in again.',
      ),
    )

    const view = renderDashboard()
    await flush()

    await act(async () => {
      logoutButton(view.container).click()
      await Promise.resolve()
    })
    await flush()

    expect(view.container.textContent).toContain('Sign in page')

    view.unmount()
  })

  it('navigates to sign-in page on logout 403 without keeping the dashboard', async () => {
    apiMocks.getCurrentOrganization.mockResolvedValue(organization)
    apiMocks.logoutOrganization.mockRejectedValue(
      new OrganizationApiError(
        403,
        'This account is not authorized for the organization workspace.',
      ),
    )

    const view = renderDashboard()
    await flush()

    expect(view.container.textContent).toContain('Acme')
    expect(view.container.textContent).toContain('acme@example.com')

    await act(async () => {
      logoutButton(view.container).click()
      await Promise.resolve()
    })
    await flush()

    expect(view.container.textContent).toContain('Sign in page')
    expect(view.container.textContent).not.toContain('Acme')
    expect(view.container.textContent).not.toContain('acme@example.com')
    expect(view.container.textContent).not.toContain('ORGANIZATION_ADMIN')
    expect(view.container.textContent).not.toContain('Organization details')
    expect(view.container.textContent).not.toContain(
      'Your organization dashboard is coming soon.',
    )

    view.unmount()
  })

  it('keeps the dashboard and hides internals on logout network failure', async () => {
    apiMocks.getCurrentOrganization.mockResolvedValue(organization)
    apiMocks.logoutOrganization.mockRejectedValue(
      new Error('ECONNRESET internal backend detail'),
    )

    const view = renderDashboard()
    await flush()

    await act(async () => {
      logoutButton(view.container).click()
      await Promise.resolve()
    })
    await flush()

    expect(view.container.querySelector('[role="alert"]')?.textContent).toContain(
      'Unable to sign out. Please try again.',
    )
    expect(view.container.textContent).not.toContain('ECONNRESET')
    expect(view.container.textContent).toContain('Acme')

    view.unmount()
  })

  it('prevents duplicate logout submissions while one is in progress', async () => {
    const pendingLogout = deferred<void>()

    apiMocks.getCurrentOrganization.mockResolvedValue(organization)
    apiMocks.logoutOrganization.mockReturnValue(pendingLogout.promise)

    const view = renderDashboard()
    await flush()

    const button = logoutButton(view.container)

    await act(async () => {
      button.click()
      button.click()
      await Promise.resolve()
    })

    expect(apiMocks.logoutOrganization).toHaveBeenCalledTimes(1)
    expect(button.disabled).toBe(true)

    await act(async () => {
      pendingLogout.resolve()
      await Promise.resolve()
    })

    expect(view.container.textContent).toContain('Sign in page')

    view.unmount()
  })
})
