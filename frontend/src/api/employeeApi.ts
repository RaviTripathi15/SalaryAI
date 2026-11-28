import { getCsrfToken } from './organizationApi'

const API_BASE_URL = '/api/v1/organizations/employees'

const SESSION_FAILURE_MESSAGE =
  'Your organization session is not active. Please sign in again.'
const FORBIDDEN_MESSAGE =
  'This account is not authorized for the organization workspace.'
const DUPLICATE_MESSAGE =
  'An employee with these details already exists in this organization.'
const STALE_MESSAGE =
  'The employee record changed. Reload it and try again.'
const NOT_FOUND_MESSAGE = 'Employee not found.'
const CSRF_FAILURE_MESSAGE =
  'Unable to initialize a secure request. Please try again.'
const NETWORK_FAILURE_MESSAGE =
  'Unable to complete the request. Please try again.'
const LOAD_FAILURE_MESSAGE = 'Unable to load employees. Please try again.'
const DETAILS_FAILURE_MESSAGE =
  'Unable to load the employee. Please try again.'
const SAVE_FAILURE_MESSAGE = 'Unable to save the employee. Please try again.'
const DEACTIVATE_FAILURE_MESSAGE =
  'Unable to deactivate the employee. Please try again.'
const UNEXPECTED_MESSAGE = 'An unexpected error occurred.'
const INVALID_FILTER_MESSAGES = [
  'Invalid sort field.',
  'Invalid sort direction.',
  'Invalid employment status filter.',
  'Page index must not be negative.',
  'Page index is too large.',
  'Page size must be between 1 and 100.',
  'Search text must not exceed 100 characters.',
]

const VALIDATION_MESSAGES = [
  'Employee code is required',
  'Employee code is required.',
  'Employee code must not exceed 50 characters',
  'Employee code must not exceed 50 characters.',
  'First name is required',
  'First name is required.',
  'First name must not exceed 100 characters',
  'First name must not exceed 100 characters.',
  'Last name is required',
  'Last name is required.',
  'Last name must not exceed 100 characters',
  'Last name must not exceed 100 characters.',
  'Work email is required',
  'Work email is required.',
  'Enter a valid work email address',
  'Enter a valid work email address.',
  'Work email must not exceed 255 characters',
  'Work email must not exceed 255 characters.',
  'Phone number must not exceed 20 characters',
  'Phone number must not exceed 20 characters.',
  'Department must not exceed 100 characters',
  'Department must not exceed 100 characters.',
  'Designation must not exceed 100 characters',
  'Designation must not exceed 100 characters.',
  'Joining date is required',
  'Joining date is required.',
  'Version is required',
  'Version is required.',
  'Version must not be negative',
  'Version must not be negative.',
]

export type EmploymentStatus = 'ACTIVE' | 'INACTIVE'

export type EmployeeSortField =
  | 'id'
  | 'employeeCode'
  | 'firstName'
  | 'lastName'
  | 'workEmail'
  | 'phone'
  | 'department'
  | 'designation'
  | 'joiningDate'
  | 'employmentStatus'
  | 'createdAt'
  | 'updatedAt'
  | 'deactivatedAt'

export type EmployeeSortDirection = 'asc' | 'desc'

export interface Employee {
  id: number
  employeeCode: string
  firstName: string
  lastName: string
  workEmail: string
  phone: string | null
  department: string | null
  designation: string | null
  joiningDate: string
  employmentStatus: EmploymentStatus
  createdAt: string
  updatedAt: string
  deactivatedAt: string | null
  version: number
}

export interface EmployeePage {
  content: Employee[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface EmployeeListParams {
  page: number
  size: number
  sortBy: EmployeeSortField
  sortDirection: EmployeeSortDirection
  search?: string
  status?: EmploymentStatus
}

export interface EmployeeStatusCounts {
  total: number
  active: number
  inactive: number
}

export interface EmployeeCreateRequest {
  employeeCode: string
  firstName: string
  lastName: string
  workEmail: string
  phone: string | null
  department: string | null
  designation: string | null
  joiningDate: string
}

export interface EmployeeUpdateRequest extends EmployeeCreateRequest {
  version: number
}

// Carries only a safe message and the status needed by the pages.
export class EmployeeApiError extends Error {
  readonly status: number | null

  constructor(status: number | null, message: string) {
    super(message)
    this.name = 'EmployeeApiError'
    this.status = status
  }
}

const SAFE_MESSAGES = new Set([
  SESSION_FAILURE_MESSAGE,
  FORBIDDEN_MESSAGE,
  DUPLICATE_MESSAGE,
  STALE_MESSAGE,
  NOT_FOUND_MESSAGE,
  UNEXPECTED_MESSAGE,
  ...INVALID_FILTER_MESSAGES,
  ...VALIDATION_MESSAGES,
])

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

function isNullableString(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

function isDateString(value: unknown): value is string {
  return typeof value === 'string' && !Number.isNaN(Date.parse(value))
}

function isEmploymentStatus(value: unknown): value is EmploymentStatus {
  return value === 'ACTIVE' || value === 'INACTIVE'
}

function isEmployee(value: unknown): value is Employee {
  if (!isRecord(value)) return false

  return (
    isFiniteNumber(value.id) &&
    isNonBlankString(value.employeeCode) &&
    isNonBlankString(value.firstName) &&
    isNonBlankString(value.lastName) &&
    isNonBlankString(value.workEmail) &&
    isNullableString(value.phone) &&
    isNullableString(value.department) &&
    isNullableString(value.designation) &&
    isDateString(value.joiningDate) &&
    isEmploymentStatus(value.employmentStatus) &&
    isDateString(value.createdAt) &&
    isDateString(value.updatedAt) &&
    isNullableString(value.deactivatedAt) &&
    isFiniteNumber(value.version)
  )
}

function isEmployeePage(value: unknown): value is EmployeePage {
  if (!isRecord(value) || !Array.isArray(value.content)) return false

  return (
    value.content.every(isEmployee) &&
    isFiniteNumber(value.page) &&
    isFiniteNumber(value.size) &&
    isFiniteNumber(value.totalElements) &&
    isFiniteNumber(value.totalPages) &&
    typeof value.first === 'boolean' &&
    typeof value.last === 'boolean'
  )
}

function isAbortError(error: unknown): boolean {
  return (
    typeof error === 'object' &&
    error !== null &&
    'name' in error &&
    error.name === 'AbortError'
  )
}

async function readError(response: Response, fallback: string): Promise<EmployeeApiError> {
  let serverMessage = ''

  try {
    const body: unknown = await response.json()

    if (isRecord(body) && typeof body.error === 'string') {
      serverMessage = body.error.trim()
    }
  } catch {
    // A non-JSON error body must never be displayed to the user.
  }

  if (response.status === 401) {
    return new EmployeeApiError(401, SESSION_FAILURE_MESSAGE)
  }

  if (response.status === 403) {
    return new EmployeeApiError(403, FORBIDDEN_MESSAGE)
  }

  if (response.status === 404) {
    return new EmployeeApiError(404, NOT_FOUND_MESSAGE)
  }

  if (response.status === 409 && SAFE_MESSAGES.has(serverMessage)) {
    return new EmployeeApiError(409, serverMessage)
  }

  if (response.status === 400 && SAFE_MESSAGES.has(serverMessage)) {
    return new EmployeeApiError(400, serverMessage)
  }

  return new EmployeeApiError(response.status, fallback)
}

async function csrfHeaders(): Promise<Record<string, string>> {
  try {
    const csrf = await getCsrfToken(CSRF_FAILURE_MESSAGE)
    return { [csrf.headerName]: csrf.token }
  } catch {
    throw new EmployeeApiError(null, CSRF_FAILURE_MESSAGE)
  }
}

async function request<T>(
  path: string,
  init: RequestInit,
  fallback: string,
  isResponse: (value: unknown) => value is T,
): Promise<T> {
  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      credentials: 'include',
      ...init,
    })
  } catch (error) {
    if (isAbortError(error)) throw error
    throw new EmployeeApiError(null, NETWORK_FAILURE_MESSAGE)
  }

  if (!response.ok) {
    throw await readError(response, fallback)
  }

  let payload: unknown

  try {
    payload = await response.json()
  } catch {
    throw new EmployeeApiError(response.status, fallback)
  }

  if (!isResponse(payload)) {
    throw new EmployeeApiError(response.status, fallback)
  }

  return payload
}

function listInit(signal?: AbortSignal): RequestInit {
  const init: RequestInit = { method: 'GET' }
  if (signal) init.signal = signal
  return init
}

export function listEmployees(
  params: EmployeeListParams,
  signal?: AbortSignal,
): Promise<EmployeePage> {
  const query = new URLSearchParams({
    page: String(params.page),
    size: String(params.size),
    sortBy: params.sortBy,
    sortDirection: params.sortDirection,
  })

  const search = params.search?.trim()
  if (search) query.set('search', search)
  if (params.status) query.set('status', params.status)

  return request(
    `?${query.toString()}`,
    listInit(signal),
    LOAD_FAILURE_MESSAGE,
    isEmployeePage,
  )
}

export async function getEmployeeStatusCounts(
  signal?: AbortSignal,
): Promise<EmployeeStatusCounts> {
  const baseParams: EmployeeListParams = {
    page: 0,
    size: 1,
    sortBy: 'id',
    sortDirection: 'asc',
  }

  const [activePage, inactivePage] = await Promise.all([
    listEmployees({ ...baseParams, status: 'ACTIVE' }, signal),
    listEmployees({ ...baseParams, status: 'INACTIVE' }, signal),
  ])

  return {
    active: activePage.totalElements,
    inactive: inactivePage.totalElements,
    total: activePage.totalElements + inactivePage.totalElements,
  }
}

export function getEmployee(
  id: number,
  signal?: AbortSignal,
): Promise<Employee> {
  return request(
    `/${id}`,
    listInit(signal),
    DETAILS_FAILURE_MESSAGE,
    isEmployee,
  )
}

export async function createEmployee(
  employee: EmployeeCreateRequest,
): Promise<Employee> {
  const headers = await csrfHeaders()

  return request(
    '',
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: JSON.stringify(employee),
    },
    SAVE_FAILURE_MESSAGE,
    isEmployee,
  )
}

export async function updateEmployee(
  id: number,
  employee: EmployeeUpdateRequest,
): Promise<Employee> {
  const headers = await csrfHeaders()

  return request(
    `/${id}`,
    {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        ...headers,
      },
      body: JSON.stringify(employee),
    },
    SAVE_FAILURE_MESSAGE,
    isEmployee,
  )
}

export async function deactivateEmployee(id: number): Promise<Employee> {
  const headers = await csrfHeaders()

  return request(
    `/${id}/deactivate`,
    {
      method: 'POST',
      headers,
    },
    DEACTIVATE_FAILURE_MESSAGE,
    isEmployee,
  )
}
