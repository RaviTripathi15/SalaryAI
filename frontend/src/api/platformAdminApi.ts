
const API_BASE_URL = '/api/v1/platform-admin'
const CSRF_URL = '/api/v1/csrf'

export interface AdminLoginResponse {
  id: number
  email: string
  role: string
}

interface CsrfResponse {
  headerName: string
  token: string
}

async function getCsrfToken(): Promise<CsrfResponse> {
  const response = await fetch(CSRF_URL, {
    method: 'GET',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error('Unable to initialize secure login. Please try again.')
  }

  return response.json() as Promise<CsrfResponse>
}

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
    const errorBody = await response.text()
    throw new Error(errorBody || `Login failed (${response.status}).`)
  }

  return response.json() as Promise<AdminLoginResponse>
}

export async function getCurrentPlatformAdmin(): Promise<AdminLoginResponse> {
  const response = await fetch(`${API_BASE_URL}/me`, {
    method: 'GET',
    credentials: 'include',
  })

  if (!response.ok) {
    throw new Error('Your administrator session is not active.')
  }

  return response.json() as Promise<AdminLoginResponse>
}

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
    throw new Error('Unable to log out. Please try again.')
  }
}
