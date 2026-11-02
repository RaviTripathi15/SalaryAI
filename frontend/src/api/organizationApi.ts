
const API_BASE_URL = '/api/v1/organizations'
const CSRF_URL = '/api/v1/csrf'

export interface OrganizationRegistration {
  name: string
  email: string
  phone: string
  password: string
}

interface CsrfResponse {
  headerName: string
  token: string
}

// Fetch a CSRF token before submitting the registration form.
async function getCsrfToken(): Promise<CsrfResponse> {
  const response = await fetch(CSRF_URL, {
    method: 'GET',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error('Unable to initialize secure registration. Please try again.')
  }

  return response.json() as Promise<CsrfResponse>
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
