
const API_BASE_URL = '/api/v1/platform-admin'
const CSRF_URL = '/api/v1/csrf'

// Information returned after a successful administrator login.
export interface AdminLoginResponse {
  id: number
  email: string
  role: string
}

// Organization information returned by the review API.
export interface OrganizationReview {
  id: number
  name: string
  email: string
  phone: string
  status: string
  createdAt: string
}

export type OrganizationStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export type OrganizationStatusCounts = Record<OrganizationStatus, number>

interface CsrfResponse {
  headerName: string
  token: string
}

// Obtain a CSRF token before sending a protected request.
async function getCsrfToken(): Promise<CsrfResponse> {
  const response = await fetch(CSRF_URL, {
    method: 'GET',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error('Unable to initialize secure request. Please try again.')
  }

  return response.json() as Promise<CsrfResponse>
}

// Read an API error message when the server provides one.
async function readError(response: Response): Promise<string> {
  const errorBody = await response.text()

  try {
    const parsed = JSON.parse(errorBody) as {
      error?: string
      message?: string
    }

    return (
      parsed.error ||
      parsed.message ||
      `Request failed (${response.status}).`
    )
  } catch {
    return errorBody || `Request failed (${response.status}).`
  }
}

// Sign in the platform administrator.
export async function loginPlatformAdmin(
  email: string,
  password: string,
): Promise<AdminLoginResponse> {
  const csrf = await getCsrfToken()

  const response = await fetch(`${API_BASE_URL}/login`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ email, password }),
  })

  if (!response.ok) {
    throw new Error(await readError(response))
  }

  return response.json() as Promise<AdminLoginResponse>
}

// Verify the currently signed-in administrator's session.
export async function getCurrentPlatformAdmin(): Promise<AdminLoginResponse> {
  const response = await fetch(`${API_BASE_URL}/me`, {
    method: 'GET',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error('Your administrator session is not active. Please sign in again.')
  }

  return response.json() as Promise<AdminLoginResponse>
}

// Sign out the platform administrator.
export async function logoutPlatformAdmin(): Promise<void> {
  const csrf = await getCsrfToken()

  const response = await fetch(`${API_BASE_URL}/logout`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      [csrf.headerName]: csrf.token,
    },
  })

  if (!response.ok) {
    throw new Error(await readError(response))
  }
}

// Retrieve organizations waiting for platform-admin review.
export async function getPendingOrganizations(): Promise<OrganizationReview[]> {
  const response = await fetch(`${API_BASE_URL}/organizations/pending`, {
    method: 'GET',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error(await readError(response))
  }

  return response.json() as Promise<OrganizationReview[]>
}

// Approve or reject a pending organization.
export async function reviewOrganization(
  id: number,
  action: 'approve' | 'reject',
): Promise<OrganizationReview> {
  const csrf = await getCsrfToken()

  const response = await fetch(
    `${API_BASE_URL}/organizations/${id}/${action}`,
    {
      method: 'PATCH',
      credentials: 'include',
      headers: {
        [csrf.headerName]: csrf.token,
      },
    },
  )  

  if (!response.ok) {
    throw new Error(await readError(response))
  }

  return response.json() as Promise<OrganizationReview>
}


// Retrieve live organization counts grouped by status.
export async function getOrganizationStatusCounts(): Promise<OrganizationStatusCounts> {
  const response = await fetch(`${API_BASE_URL}/organizations/counts`, {
    method: 'GET',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error(await readError(response))
  }

  return response.json() as Promise<OrganizationStatusCounts>
}




export async function getOrganizationsByStatus(
  status: OrganizationStatus,
): Promise<OrganizationReview[]> {
  const response = await fetch(
    `${API_BASE_URL}/organizations?status=${status}`,
    {
      method: 'GET',
      credentials: 'include',
    },
  )

  if (!response.ok) {
    throw new Error(await readError(response))
  }

  return response.json() as Promise<OrganizationReview[]>
}
