import type {
  Employee,
  EmployeeCreateRequest,
} from '../api/employeeApi'

export interface EmployeeFormValues {
  employeeCode: string
  firstName: string
  lastName: string
  workEmail: string
  phone: string
  department: string
  designation: string
  joiningDate: string
}

export type EmployeeFormErrors = Partial<Record<keyof EmployeeFormValues, string>>

export const EMPTY_EMPLOYEE_FORM: EmployeeFormValues = {
  employeeCode: '',
  firstName: '',
  lastName: '',
  workEmail: '',
  phone: '',
  department: '',
  designation: '',
  joiningDate: '',
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function isValidCalendarDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) return false

  const year = Number(match[1])
  const month = Number(match[2])
  const day = Number(match[3])
  const date = new Date(year, month - 1, day)

  return (
    date.getFullYear() === year &&
    date.getMonth() === month - 1 &&
    date.getDate() === day
  )
}

export const EMPLOYEE_FIELD_LIMITS = {
  employeeCode: 50,
  firstName: 100,
  lastName: 100,
  workEmail: 255,
  phone: 20,
  department: 100,
  designation: 100,
} as const

export function employeeValuesFrom(employee: Employee): EmployeeFormValues {
  return {
    employeeCode: employee.employeeCode,
    firstName: employee.firstName,
    lastName: employee.lastName,
    workEmail: employee.workEmail,
    phone: employee.phone ?? '',
    department: employee.department ?? '',
    designation: employee.designation ?? '',
    joiningDate: employee.joiningDate,
  }
}

export function normalizeFormValues(values: EmployeeFormValues): EmployeeFormValues {
  return {
    employeeCode: values.employeeCode.trim(),
    firstName: values.firstName.trim(),
    lastName: values.lastName.trim(),
    workEmail: values.workEmail.trim(),
    phone: values.phone.trim(),
    department: values.department.trim(),
    designation: values.designation.trim(),
    joiningDate: values.joiningDate.trim(),
  }
}

function hasLengthError(
  errors: EmployeeFormErrors,
  field: keyof EmployeeFormErrors,
): boolean {
  return Object.prototype.hasOwnProperty.call(errors, field)
}

export function validateEmployeeForm(values: EmployeeFormValues): EmployeeFormErrors {
  const normalized = normalizeFormValues(values)
  const errors: EmployeeFormErrors = {}

  if (!normalized.employeeCode) {
    errors.employeeCode = 'Employee code is required.'
  } else if (normalized.employeeCode.length > EMPLOYEE_FIELD_LIMITS.employeeCode) {
    errors.employeeCode = 'Employee code must not exceed 50 characters.'
  }

  if (!normalized.firstName) {
    errors.firstName = 'First name is required.'
  } else if (normalized.firstName.length > EMPLOYEE_FIELD_LIMITS.firstName) {
    errors.firstName = 'First name must not exceed 100 characters.'
  }

  if (!normalized.lastName) {
    errors.lastName = 'Last name is required.'
  } else if (normalized.lastName.length > EMPLOYEE_FIELD_LIMITS.lastName) {
    errors.lastName = 'Last name must not exceed 100 characters.'
  }

  if (!normalized.workEmail) {
    errors.workEmail = 'Work email is required.'
  } else if (!EMAIL_PATTERN.test(normalized.workEmail)) {
    errors.workEmail = 'Enter a valid work email address.'
  } else if (normalized.workEmail.length > EMPLOYEE_FIELD_LIMITS.workEmail) {
    errors.workEmail = 'Work email must not exceed 255 characters.'
  }

  if (normalized.phone.length > EMPLOYEE_FIELD_LIMITS.phone) {
    errors.phone = 'Phone number must not exceed 20 characters.'
  }

  if (normalized.department.length > EMPLOYEE_FIELD_LIMITS.department) {
    errors.department = 'Department must not exceed 100 characters.'
  }

  if (normalized.designation.length > EMPLOYEE_FIELD_LIMITS.designation) {
    errors.designation = 'Designation must not exceed 100 characters.'
  }

  if (!normalized.joiningDate) {
    errors.joiningDate = 'Joining date is required.'
  } else if (!isValidCalendarDate(normalized.joiningDate)) {
    errors.joiningDate = 'Enter a valid joining date.'
  }

  return errors
}

export function hasEmployeeFormErrors(errors: EmployeeFormErrors): boolean {
  return Object.keys(errors).length > 0
}

export function employeeCreatePayload(
  values: EmployeeFormValues,
): EmployeeCreateRequest {
  const normalized = normalizeFormValues(values)

  return {
    employeeCode: normalized.employeeCode,
    firstName: normalized.firstName,
    lastName: normalized.lastName,
    workEmail: normalized.workEmail,
    phone: normalized.phone || null,
    department: normalized.department || null,
    designation: normalized.designation || null,
    joiningDate: normalized.joiningDate,
  }
}

export function fieldError(
  errors: EmployeeFormErrors,
  field: keyof EmployeeFormValues,
): string {
  return hasLengthError(errors, field) ? errors[field] ?? '' : ''
}
