import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Route, useLocation } from 'react-router-dom'
import { EmployeeApiError } from '../api/employeeApi'
import EmployeeFormPage from './EmployeeFormPage'
import { flashMessage } from '../utils/employeeUi'
import {
  click,
  pageText,
  renderEmployeeRoute,
  sampleEmployee,
  setInput,
  settle,
  submitForm,
} from '../test/employeeTestUtils'

const apiMocks = vi.hoisted(() => ({
  createEmployee: vi.fn(),
  getEmployee: vi.fn(),
  updateEmployee: vi.fn(),
  getCurrentOrganization: vi.fn(),
  logoutOrganization: vi.fn(),
}))

vi.mock('../api/employeeApi', async () => {
  const actual = await vi.importActual<typeof import('../api/employeeApi')>(
    '../api/employeeApi',
  )

  return {
    ...actual,
    createEmployee: apiMocks.createEmployee,
    getEmployee: apiMocks.getEmployee,
    updateEmployee: apiMocks.updateEmployee,
  }
})

vi.mock('../api/organizationApi', async () => {
  const actual = await vi.importActual<typeof import('../api/organizationApi')>(
    '../api/organizationApi',
  )

  return {
    ...actual,
    getCurrentOrganization: apiMocks.getCurrentOrganization,
    logoutOrganization: apiMocks.logoutOrganization,
  }
})

function DetailMarker() {
  const location = useLocation()
  return <p>Employee detail marker {flashMessage(location.state)}</p>
}

function renderCreate() {
  return renderEmployeeRoute(
    <EmployeeFormPage mode="create" />,
    '/organization/employees/new',
    '/organization/employees/new',
    [
      <Route
        key="detail"
        path="/organization/employees/:id"
        element={<DetailMarker />}
      />,
    ],
  )
}

function renderEdit(path = '/organization/employees/11/edit') {
  return renderEmployeeRoute(
    <EmployeeFormPage mode="edit" />,
    path,
    '/organization/employees/:id/edit',
    [
      <Route
        key="detail"
        path="/organization/employees/:id"
        element={<DetailMarker />}
      />,
    ],
  )
}

function fillValidForm(container: HTMLElement) {
  setInput(container, 'employeeCode', '  EMP-001  ')
  setInput(container, 'firstName', '  Ada  ')
  setInput(container, 'lastName', '  Lovelace  ')
  setInput(container, 'workEmail', '  ADA@example.com  ')
  setInput(container, 'phone', '  555-0100  ')
  setInput(container, 'department', '  Engineering  ')
  setInput(container, 'designation', '  Engineer  ')
  setInput(container, 'joiningDate', '2024-01-15')
}

beforeEach(() => {
  vi.clearAllMocks()
  apiMocks.getCurrentOrganization.mockResolvedValue({
    id: 7,
    name: 'Acme',
    email: 'acme@example.com',
    role: 'ORGANIZATION_ADMIN',
  })
  apiMocks.logoutOrganization.mockResolvedValue(undefined)
  apiMocks.createEmployee.mockResolvedValue(sampleEmployee)
  apiMocks.getEmployee.mockResolvedValue(sampleEmployee)
  apiMocks.updateEmployee.mockResolvedValue(sampleEmployee)
})

afterEach(() => {
  document.body.innerHTML = ''
  vi.restoreAllMocks()
})

describe('EmployeeFormPage', () => {
  it('validates required fields, email, date, and lengths before saving', async () => {
    const view = renderCreate()
    await settle()

    submitForm(view.container)
    await settle()

    expect(apiMocks.createEmployee).not.toHaveBeenCalled()
    expect(pageText(view.container)).toContain('Employee code is required.')
    expect(pageText(view.container)).toContain('First name is required.')
    expect(pageText(view.container)).toContain('Last name is required.')
    expect(pageText(view.container)).toContain('Work email is required.')
    expect(pageText(view.container)).toContain('Joining date is required.')
    expect(
      view.container.querySelector('#workEmail')?.getAttribute('aria-invalid'),
    ).toBe('true')

    view.unmount()
  })

  it('trims values, creates the employee, and navigates with a success message', async () => {
    const view = renderCreate()
    await settle()

    fillValidForm(view.container)
    submitForm(view.container)
    await settle()

    expect(apiMocks.createEmployee).toHaveBeenCalledWith({
      employeeCode: 'EMP-001',
      firstName: 'Ada',
      lastName: 'Lovelace',
      workEmail: 'ADA@example.com',
      phone: '555-0100',
      department: 'Engineering',
      designation: 'Engineer',
      joiningDate: '2024-01-15',
    })
    expect(pageText(view.container)).toContain('Employee detail marker')
    expect(pageText(view.container)).toContain(
      'Employee created successfully.',
    )

    view.unmount()
  })

  it('shows a safe duplicate-code or email error without navigating', async () => {
    apiMocks.createEmployee.mockRejectedValueOnce(
      new EmployeeApiError(
        409,
        'An employee with these details already exists in this organization.',
      ),
    )

    const view = renderCreate()
    await settle()

    fillValidForm(view.container)
    submitForm(view.container)
    await settle()

    expect(pageText(view.container)).toContain(
      'An employee with these details already exists in this organization.',
    )
    expect(pageText(view.container)).not.toContain('Employee detail marker')
    expect(
      view.container.querySelector('[role="alert"]')?.textContent,
    ).toContain('already exists')

    view.unmount()
  })

  it('redirects on 401 and shows a safe message on 403', async () => {
    apiMocks.createEmployee.mockRejectedValueOnce(
      new EmployeeApiError(
        401,
        'Your organization session is not active. Please sign in again.',
      ),
    )

    const unauthorizedView = renderCreate()
    await settle()
    fillValidForm(unauthorizedView.container)
    submitForm(unauthorizedView.container)
    await settle()
    expect(pageText(unauthorizedView.container)).toContain('Sign in page')
    unauthorizedView.unmount()

    apiMocks.createEmployee.mockRejectedValueOnce(
      new EmployeeApiError(
        403,
        'This account is not authorized for the organization workspace.',
      ),
    )

    const forbiddenView = renderCreate()
    await settle()
    fillValidForm(forbiddenView.container)
    submitForm(forbiddenView.container)
    await settle()
    expect(pageText(forbiddenView.container)).toContain(
      'This account is not authorized for the organization workspace.',
    )
    expect(pageText(forbiddenView.container)).not.toContain('EMP-001')
    expect(forbiddenView.container.querySelector('form')).toBeNull()

    forbiddenView.unmount()
  })

  it('loads an employee for editing and preserves the server version on update', async () => {
    const view = renderEdit()
    await settle()

    expect(apiMocks.getEmployee).toHaveBeenCalledWith(11, expect.any(AbortSignal))
    expect(pageText(view.container)).toContain('Version 3 is preserved')
    expect(
      view.container.querySelector<HTMLInputElement>('#employeeCode')?.value,
    ).toBe('EMP-001')

    setInput(view.container, 'designation', 'Senior Engineer')
    submitForm(view.container)
    await settle()

    expect(apiMocks.updateEmployee).toHaveBeenCalledWith(
      11,
      expect.objectContaining({
        version: 3,
        designation: 'Senior Engineer',
      }),
    )
    expect(pageText(view.container)).toContain(
      'Employee updated successfully.',
    )

    view.unmount()
  })

  it('handles a stale 409 conflict with a reload prompt instead of overwriting', async () => {
    apiMocks.getEmployee
      .mockResolvedValueOnce(sampleEmployee)
      .mockResolvedValueOnce({ ...sampleEmployee, version: 4 })
    apiMocks.updateEmployee.mockRejectedValueOnce(
      new EmployeeApiError(
        409,
        'The employee record changed. Reload it and try again.',
      ),
    )

    const view = renderEdit()
    await settle()

    setInput(view.container, 'designation', 'Staff Engineer')
    submitForm(view.container)
    await settle()

    expect(apiMocks.updateEmployee).toHaveBeenCalledWith(
      11,
      expect.objectContaining({ version: 3 }),
    )
    expect(pageText(view.container)).toContain(
      'The employee record changed. Reload it and try again.',
    )
    expect(pageText(view.container)).toContain('Reload employee')

    click(view.container, '.employee-form-actions button:last-child')
    await settle()
    expect(apiMocks.getEmployee).toHaveBeenCalledTimes(2)
    expect(pageText(view.container)).toContain('Version 4 is preserved')

    view.unmount()
  })

  it('rejects an invalid employee link before loading', async () => {
    const view = renderEdit('/organization/employees/not-a-number/edit')
    await settle()

    expect(apiMocks.getEmployee).not.toHaveBeenCalled()
    expect(pageText(view.container)).toContain('This employee link is invalid.')

    view.unmount()
  })
})
