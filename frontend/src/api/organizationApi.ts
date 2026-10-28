
const API_BASE_URL = '/api/v1/organizations'

export interface OrganizationRegistration {
  name: string
  email: string
  phone: string
  password: string
}

export async function registerOrganization(
  organization: OrganizationRegistration,
): Promise<string> {
  const response = await fetch(`${API_BASE_URL}/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
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
