import { describe, expect, it } from 'vitest'
import {
  EMPTY_EMPLOYEE_FORM,
  employeeCreatePayload,
  validateEmployeeForm,
} from './employeeForm'

const validValues = {
  ...EMPTY_EMPLOYEE_FORM,
  employeeCode: ' EMP-001 ',
  firstName: ' Ada ',
  lastName: ' Lovelace ',
  workEmail: ' ADA@example.com ',
  phone: ' 555-0100 ',
  department: ' Engineering ',
  designation: ' Engineer ',
  joiningDate: '2024-01-15',
}

describe('employeeForm', () => {
  it('accepts valid values and trims/normalizes optional fields for the payload', () => {
    expect(validateEmployeeForm(validValues)).toEqual({})
    expect(employeeCreatePayload(validValues)).toEqual({
      employeeCode: 'EMP-001',
      firstName: 'Ada',
      lastName: 'Lovelace',
      workEmail: 'ADA@example.com',
      phone: '555-0100',
      department: 'Engineering',
      designation: 'Engineer',
      joiningDate: '2024-01-15',
    })
  })

  it('rejects impossible calendar dates', () => {
    const errors = validateEmployeeForm({
      ...validValues,
      joiningDate: '2024-02-30',
    })

    expect(errors.joiningDate).toBe('Enter a valid joining date.')
  })

  it('rejects invalid email addresses and overlong fields', () => {
    const errors = validateEmployeeForm({
      ...validValues,
      workEmail: 'not-an-email',
      employeeCode: 'X'.repeat(51),
    })

    expect(errors.workEmail).toBe('Enter a valid work email address.')
    expect(errors.employeeCode).toBe(
      'Employee code must not exceed 50 characters.',
    )
  })
})
