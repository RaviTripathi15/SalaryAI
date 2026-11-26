
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  getCurrentOrganization,
  logoutOrganization,
  OrganizationApiError,
} from './organizationApi'

const fetchMock = vi.fn()

function jsonResponse(payload: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => payload,
    text: async () => JSON.stringify(payload),
  } as Response
}

async function expectOrganizationApiError(
  operation: Promise<unknown>,
  status: number | null,
  message: string,
) {
  let captured: unknown

  try {
    await operation
  } catch (error) {
    captured = error
  }

  expect(captured).toBeInstanceOf(OrganizationApiError)

  const apiError = captured as OrganizationApiError

  expect(apiError.status).toBe(status)
  expect(apiError.message).toBe(message)
}

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('getCurrentOrganization', () => {
  it('requests /me with cookies and validates the server response', async () => {
    const organization = {
      id: 7,
      name: 'Acme',
      email: 'acme@example.com',
      role: 'ORGANIZATION_ADMIN',
    }

    fetchMock.mockResolvedValueOnce(jsonResponse(organization))

    await expect(getCurrentOrganization()).resolves.toEqual(organization)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/organizations/me', {
      method: 'GET',
      credentials: 'include',
    })
  })

  it('maps 401 and 403 responses to safe status-aware errors', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: 'internal backend detail' }, 401),
    )

    await expectOrganizationApiError(
      getCurrentOrganization(),
      401,
      'Your organization session is not active. Please sign in again.',
    )

    fetchMock.mockResolvedValueOnce(
      jsonResponse({ error: 'internal backend detail' }, 403),
    )

    await expectOrganizationApiError(
      getCurrentOrganization(),
      403,
      'This account is not authorized for the organization workspace.',
    )
  })

  it('rejects malformed success payloads with a generic message', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: '7' }))

    await expectOrganizationApiError(
      getCurrentOrganization(),
      200,
      'Unable to verify your organization session. Please try again.',
    )
  })

  it('does not expose network error details', async () => {
    fetchMock.mockRejectedValueOnce(new Error('ECONNRESET internal detail'))

    await expectOrganizationApiError(
      getCurrentOrganization(),
      null,
      'Unable to verify your organization session. Please try again.',
    )
  })
})

describe('logoutOrganization', () => {
  it('fetches fresh CSRF and posts logout with cookies and the CSRF header', async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({
          headerName: 'X-XSRF-TOKEN',
          token: 'fresh-token',
        }),
      )
      .mockResolvedValueOnce(
        jsonResponse({ message: 'Logged out successfully.' }),
      )

    await expect(logoutOrganization()).resolves.toBeUndefined()

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/csrf', {
      method: 'GET',
      credentials: 'include',
    })

    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/v1/organizations/logout',
      {
        method: 'POST',
        credentials: 'include',
        headers: {
          'X-XSRF-TOKEN': 'fresh-token',
        },
      },
    )
  })

  it('maps 401 and 403 logout responses to safe status-aware errors', async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'token-1' }),
      )
      .mockResolvedValueOnce(jsonResponse({ error: 'internal detail' }, 401))

    await expectOrganizationApiError(
      logoutOrganization(),
      401,
      'Your organization session is not active. Please sign in again.',
    )

    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'token-2' }),
      )
      .mockResolvedValueOnce(jsonResponse({ error: 'internal detail' }, 403))

    await expectOrganizationApiError(
      logoutOrganization(),
      403,
      'This account is not authorized for the organization workspace.',
    )
  })

  it('does not expose backend details on server failure', async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'token' }),
      )
      .mockResolvedValueOnce(
        jsonResponse(
          {
            error: 'org.springframework.web.servlet.resource.NoResourceFoundException',
          },
          500,
        ),
      )

    await expectOrganizationApiError(
      logoutOrganization(),
      500,
      'Unable to sign out. Please try again.',
    )
  })

  it('does not report success for a malformed logout response', async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-XSRF-TOKEN', token: 'token' }),
      )
      .mockResolvedValueOnce(jsonResponse({ unexpected: true }))

    await expectOrganizationApiError(
      logoutOrganization(),
      200,
      'Unable to sign out. Please try again.',
    )
  })
})
