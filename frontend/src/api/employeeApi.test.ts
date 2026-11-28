import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createEmployee,
  deactivateEmployee,
  EmployeeApiError,
  getEmployee,
  getEmployeeStatusCounts,
  listEmployees,
  updateEmployee,
} from './employeeApi'
import { employeePage, sampleEmployee } from '../test/employeeTestUtils'

const csrfMocks = vi.hoisted(() => ({
  getCsrfToken: vi.fn(),
}))

vi.mock('./organizationApi', async () => {
  const actual = await vi.importActual<typeof import('./organizationApi')>(
    './organizationApi',
  )

  return {
    ...actual,
    getCsrfToken: csrfMocks.getCsrfToken,
  }
})

const fetchMock = vi.fn()

function jsonResponse(payload: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => payload,
    text: async () => JSON.stringify(payload),
  } as Response
}

async function expectEmployeeApiError(
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

  expect(captured).toBeInstanceOf(EmployeeApiError)

  const apiError = captured as EmployeeApiError
  expect(apiError.status).toBe(status)
  expect(apiError.message).toBe(message)
}

beforeEach(() => {
  fetchMock.mockReset()
  csrfMocks.getCsrfToken.mockReset()
  csrfMocks.getCsrfToken.mockResolvedValue({
    headerName: 'X-XSRF-TOKEN',
    token: 'csrf-token',
  })
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('employeeApi', () => {
  it('sends list parameters with credentials and validates the page response', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(employeePage()))

    const result = await listEmployees({
      page: 1,
      size: 50,
      sortBy: 'firstName',
      sortDirection: 'desc',
      search: '  ada  ',
      status: 'ACTIVE',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/organizations/employees?page=1&size=50&sortBy=firstName&sortDirection=desc&search=ada&status=ACTIVE',
      {
        method: 'GET',
        credentials: 'include',
      },
    )
    expect(result.content[0]).toEqual(sampleEmployee)
  })

  it('derives accurate status counts from two backend-filtered pages', async () => {
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse(employeePage({ totalElements: 7, content: [] })),
      )
      .mockResolvedValueOnce(
        jsonResponse(employeePage({ totalElements: 3, content: [] })),
      )

    const counts = await getEmployeeStatusCounts()

    expect(counts).toEqual({ total: 10, active: 7, inactive: 3 })
    expect(fetchMock.mock.calls[0][0]).toContain('status=ACTIVE')
    expect(fetchMock.mock.calls[1][0]).toContain('status=INACTIVE')
  })

  it('fetches a fresh CSRF token and includes it when creating an employee', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(sampleEmployee, 201))

    const payload = {
      employeeCode: 'EMP-001',
      firstName: 'Ada',
      lastName: 'Lovelace',
      workEmail: 'ada@example.com',
      phone: null,
      department: 'Engineering',
      designation: 'Engineer',
      joiningDate: '2024-01-15',
    }

    const result = await createEmployee(payload)

    expect(csrfMocks.getCsrfToken).toHaveBeenCalledWith(
      'Unable to initialize a secure request. Please try again.',
    )
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/organizations/employees', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'X-XSRF-TOKEN': 'csrf-token',
      },
      body: JSON.stringify(payload),
    })
    expect(result).toEqual(sampleEmployee)
  })

  it('preserves the server version in update requests', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(sampleEmployee))

    const payload = {
      employeeCode: 'EMP-001',
      firstName: 'Ada',
      lastName: 'Lovelace',
      workEmail: 'ada@example.com',
      phone: null,
      department: 'Engineering',
      designation: 'Senior Engineer',
      joiningDate: '2024-01-15',
      version: 3,
    }

    await updateEmployee(11, payload)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/organizations/employees/11',
      {
        method: 'PUT',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': 'csrf-token',
        },
        body: JSON.stringify(payload),
      },
    )
    expect(JSON.parse(String(fetchMock.mock.calls[0][1].body)).version).toBe(3)
  })

  it('uses a CSRF-protected POST for deactivation and never sends DELETE', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse({ ...sampleEmployee, employmentStatus: 'INACTIVE' }),
    )

    await deactivateEmployee(11)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/organizations/employees/11/deactivate',
      {
        method: 'POST',
        credentials: 'include',
        headers: {
          'X-XSRF-TOKEN': 'csrf-token',
        },
      },
    )
    expect(fetchMock.mock.calls[0][1].method).not.toBe('DELETE')
  })

  it('maps session, authorization, not-found, duplicate, and stale responses to safe messages', async () => {
    const cases: { status: number; error: string; message: string }[] = [
      {
        status: 401,
        error: 'internal detail',
        message: 'Your organization session is not active. Please sign in again.',
      },
      {
        status: 403,
        error: 'internal detail',
        message:
          'This account is not authorized for the organization workspace.',
      },
      {
        status: 404,
        error: 'internal detail',
        message: 'Employee not found.',
      },
      {
        status: 409,
        error: 'An employee with these details already exists in this organization.',
        message:
          'An employee with these details already exists in this organization.',
      },
      {
        status: 409,
        error: 'The employee record changed. Reload it and try again.',
        message: 'The employee record changed. Reload it and try again.',
      },
      {
        status: 400,
        error: 'Employee code is required',
        message: 'Employee code is required',
      },
    ]

    for (const testCase of cases) {
      fetchMock.mockResolvedValueOnce(
        jsonResponse({ error: testCase.error }, testCase.status),
      )

      await expectEmployeeApiError(
        getEmployee(11),
        testCase.status,
        testCase.message,
      )
    }
  })

  it('does not expose backend internals on server, network, or malformed responses', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(
        { error: 'org.springframework.web.servlet.NoResourceFoundException' },
        500,
      ),
    )
    await expectEmployeeApiError(
      getEmployee(11),
      500,
      'Unable to load the employee. Please try again.',
    )

    fetchMock.mockRejectedValueOnce(
      new Error('connect ECONNREFUSED 10.0.0.4:8080'),
    )
    await expectEmployeeApiError(
      getEmployee(11),
      null,
      'Unable to complete the request. Please try again.',
    )

    fetchMock.mockResolvedValueOnce(jsonResponse({ unexpected: true }))
    await expectEmployeeApiError(
      getEmployee(11),
      200,
      'Unable to load the employee. Please try again.',
    )
  })

  it('preserves abort errors so callers can ignore cancelled requests', async () => {
    const abortError = new DOMException('Aborted', 'AbortError')
    fetchMock.mockRejectedValueOnce(abortError)

    await expect(getEmployee(11)).rejects.toBe(abortError)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('preserves abort errors so callers can ignore cancelled requests', async () => {
    const abortError = new DOMException('Aborted', 'AbortError')
    fetchMock.mockRejectedValueOnce(abortError)

    await expect(getEmployee(11)).rejects.toBe(abortError)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('reports a safe error when a CSRF token cannot be obtained', async () => {
    csrfMocks.getCsrfToken.mockRejectedValueOnce(new Error('cookie disabled'))

    await expectEmployeeApiError(
      createEmployee({
        employeeCode: 'EMP-002',
        firstName: 'Grace',
        lastName: 'Hopper',
        workEmail: 'grace@example.com',
        phone: null,
        department: null,
        designation: null,
        joiningDate: '2024-02-01',
      }),
      null,
      'Unable to initialize a secure request. Please try again.',
    )

    expect(fetchMock).not.toHaveBeenCalled()
  })
})
