
const API_BASE_URL = '/api/v1/organizations'
const CSRF_URL = '/api/v1/csrf'

const SESSION_FAILURE_MESSAGE =
  'Your organization session is not active. Please sign in again.'
const WRONG_PRINCIPAL_MESSAGE =
  'This account is not authorized for the organization workspace.'
const SESSION_CHECK_FAILURE_MESSAGE =
  'Unable to verify your organization session. Please try again.'
const SIGN_OUT_FAILURE_MESSAGE = 'Unable to sign out. Please try again.'
const CSRF_SIGN_OUT_FAILURE_MESSAGE =
  'Unable to sign out securely. Please try again.'

export interface OrganizationRegistration {
  name: string
  email: string
  phone: string
  password: string
}

export interface OrganizationLogin {
  organizationName: string
  email: string
  password: string
}

export interface OrganizationLoginResponse {
  id: number
  name: string
  email: string
  role: string
}

interface CsrfResponse {
  headerName: string
  token: string
}

interface LogoutResponse {
  message: string
}

// Carries only a safe message and the status needed by the dashboard.
export class OrganizationApiError extends Error {
  readonly status: number | null

  constructor(status: number | null, message: string) {
    super(message)
    this.name = 'OrganizationApiError'
    this.status = status
  }
}

function isCsrfResponse(value: unknown): value is CsrfResponse {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Partial<CsrfResponse>

  return (
    typeof candidate.headerName === 'string' &&
    candidate.headerName.trim().length > 0 &&
    typeof candidate.token === 'string' &&
    candidate.token.trim().length > 0
  )
}

function isOrganizationLoginResponse(
  value: unknown,
): value is OrganizationLoginResponse {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Partial<OrganizationLoginResponse>

  return (
    typeof candidate.id === 'number' &&
    Number.isFinite(candidate.id) &&
    typeof candidate.name === 'string' &&
    candidate.name.trim().length > 0 &&
    typeof candidate.email === 'string' &&
    candidate.email.trim().length > 0 &&
    candidate.role === 'ORGANIZATION_ADMIN'
  )
}

function isLogoutResponse(value: unknown): value is LogoutResponse {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const candidate = value as Partial<LogoutResponse>

  return (
    typeof candidate.message === 'string' &&
    candidate.message.trim().length > 0
  )
}

// Fetch a CSRF token before submitting a protected request.
async function getCsrfToken(
  errorMessage = 'Unable to initialize secure registration. Please try again.',
): Promise<CsrfResponse> {
  const response = await fetch(CSRF_URL, {
    method: 'GET',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error(errorMessage)
  }

  try {
    const payload: unknown = await response.json()

    if (!isCsrfResponse(payload)) {
      throw new Error(errorMessage)
    }

    return payload
  } catch {
    throw new Error(errorMessage)
  }
}

// Register an organization securely.
export async function registerOrganization(
  organization: OrganizationRegistration,
): Promise<string> {
  const csrf = await getCsrfToken()

  const response = await fetch(`${API_BASE_URL}/register`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(organization),
  })

  const responseBody = await response.text()

  if (!response.ok) {
    throw new Error(
      responseBody || `Registration failed (${response.status}).`,
    )
  }

  return responseBody
}

// Read a user-safe message from an unsuccessful login response.
async function readLoginError(response: Response): Promise<string> {
  const genericMessage = 'Unable to sign in. Please try again.'

  if (response.status !== 401) {
    return genericMessage
  }

  const responseBody = await response.text()

  try {
    const parsed = JSON.parse(responseBody) as { error?: unknown }

    if (typeof parsed.error === 'string' && parsed.error.trim()) {
      return parsed.error.trim()
    }
  } catch {
    // Malformed JSON uses the generic message below.
  }

  return genericMessage
}

// Sign in an approved organization securely.
export async function loginOrganization(
  credentials: OrganizationLogin,
): Promise<OrganizationLoginResponse> {
  const csrf = await getCsrfToken()

  const response = await fetch(`${API_BASE_URL}/login`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify(credentials),
  })

  if (!response.ok) {
    throw new Error(await readLoginError(response))
  }

  return response.json() as Promise<OrganizationLoginResponse>
}

// Verify the organization session backing the current browser session.
export async function getCurrentOrganization(): Promise<OrganizationLoginResponse> {
  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}/me`, {
      method: 'GET',
      credentials: 'include',
    })
  } catch {
    throw new OrganizationApiError(null, SESSION_CHECK_FAILURE_MESSAGE)
  }

  if (response.status === 401) {
    throw new OrganizationApiError(401, SESSION_FAILURE_MESSAGE)
  }

  if (response.status === 403) {
    throw new OrganizationApiError(403, WRONG_PRINCIPAL_MESSAGE)
  }

  if (!response.ok) {
    throw new OrganizationApiError(
      response.status,
      SESSION_CHECK_FAILURE_MESSAGE,
    )
  }

  try {
    const payload: unknown = await response.json()

    if (!isOrganizationLoginResponse(payload)) {
      throw new OrganizationApiError(
        response.status,
        SESSION_CHECK_FAILURE_MESSAGE,
      )
    }

    return payload
  } catch (error) {
    if (error instanceof OrganizationApiError) {
      throw error
    }

    throw new OrganizationApiError(
      response.status,
      SESSION_CHECK_FAILURE_MESSAGE,
    )
  }
}

// End the current organization session using a freshly issued CSRF token.
export async function logoutOrganization(): Promise<void> {
  let csrf: CsrfResponse

  try {
    csrf = await getCsrfToken(CSRF_SIGN_OUT_FAILURE_MESSAGE)
  } catch {
    throw new OrganizationApiError(null, CSRF_SIGN_OUT_FAILURE_MESSAGE)
  }

  let response: Response

  try {
    response = await fetch(`${API_BASE_URL}/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        [csrf.headerName]: csrf.token,
      },
    })
  } catch {
    throw new OrganizationApiError(null, SIGN_OUT_FAILURE_MESSAGE)
  }

  if (response.status === 401) {
    throw new OrganizationApiError(401, SESSION_FAILURE_MESSAGE)
  }

  if (response.status === 403) {
    throw new OrganizationApiError(403, WRONG_PRINCIPAL_MESSAGE)
  }

  if (!response.ok) {
    throw new OrganizationApiError(response.status, SIGN_OUT_FAILURE_MESSAGE)
  }

  try {
    const payload: unknown = await response.json()

    if (!isLogoutResponse(payload)) {
      throw new OrganizationApiError(
        response.status,
        SIGN_OUT_FAILURE_MESSAGE,
      )
    }
  } catch (error) {
    if (error instanceof OrganizationApiError) {
      throw error
    }

    throw new OrganizationApiError(
      response.status,
      SIGN_OUT_FAILURE_MESSAGE,
    )
  }
}
