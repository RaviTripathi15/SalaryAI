import type {
  EmployeeFormErrors,
  EmployeeFormValues,
} from '../utils/employeeForm'
import { EMPLOYEE_FIELD_LIMITS } from '../utils/employeeForm'

interface EmployeeFormFieldsProps {
  values: EmployeeFormValues
  errors: EmployeeFormErrors
  disabled: boolean
  onChange: (field: keyof EmployeeFormValues, value: string) => void
}

interface FieldProps {
  id: string
  label: string
  value: string
  error?: string
  type?: string
  required?: boolean
  maxLength?: number
  autoComplete?: string
  disabled: boolean
  onChange: (value: string) => void
}

function Field({
  id,
  label,
  value,
  error = '',
  type = 'text',
  required = false,
  maxLength,
  autoComplete,
  disabled,
  onChange,
}: FieldProps) {
  return (
    <div className="employee-field">
      <label htmlFor={id}>
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      <input
        id={id}
        name={id}
        type={type}
        value={value}
        required={required}
        maxLength={maxLength}
        autoComplete={autoComplete}
        disabled={disabled}
        aria-invalid={error.length > 0}
        aria-describedby={error ? `${id}-error` : undefined}
        onChange={(event) => onChange(event.target.value)}
      />
      {error && (
        <p className="employee-field-error" id={`${id}-error`} role="alert">
          {error}
        </p>
      )}
    </div>
  )
}

function EmployeeFormFields({
  values,
  errors,
  disabled,
  onChange,
}: EmployeeFormFieldsProps) {
  return (
    <div className="employee-form-grid">
      <Field
        id="employeeCode"
        label="Employee code"
        value={values.employeeCode}
        error={errors.employeeCode}
        required
        maxLength={EMPLOYEE_FIELD_LIMITS.employeeCode}
        disabled={disabled}
        onChange={(value) => onChange('employeeCode', value)}
      />
      <Field
        id="firstName"
        label="First name"
        value={values.firstName}
        error={errors.firstName}
        required
        maxLength={EMPLOYEE_FIELD_LIMITS.firstName}
        autoComplete="given-name"
        disabled={disabled}
        onChange={(value) => onChange('firstName', value)}
      />
      <Field
        id="lastName"
        label="Last name"
        value={values.lastName}
        error={errors.lastName}
        required
        maxLength={EMPLOYEE_FIELD_LIMITS.lastName}
        autoComplete="family-name"
        disabled={disabled}
        onChange={(value) => onChange('lastName', value)}
      />
      <Field
        id="workEmail"
        label="Work email"
        type="email"
        value={values.workEmail}
        error={errors.workEmail}
        required
        maxLength={EMPLOYEE_FIELD_LIMITS.workEmail}
        autoComplete="email"
        disabled={disabled}
        onChange={(value) => onChange('workEmail', value)}
      />
      <Field
        id="phone"
        label="Phone number"
        value={values.phone}
        error={errors.phone}
        maxLength={EMPLOYEE_FIELD_LIMITS.phone}
        autoComplete="tel"
        disabled={disabled}
        onChange={(value) => onChange('phone', value)}
      />
      <Field
        id="joiningDate"
        label="Joining date"
        type="date"
        value={values.joiningDate}
        error={errors.joiningDate}
        required
        disabled={disabled}
        onChange={(value) => onChange('joiningDate', value)}
      />
      <Field
        id="department"
        label="Department"
        value={values.department}
        error={errors.department}
        maxLength={EMPLOYEE_FIELD_LIMITS.department}
        disabled={disabled}
        onChange={(value) => onChange('department', value)}
      />
      <Field
        id="designation"
        label="Designation"
        value={values.designation}
        error={errors.designation}
        maxLength={EMPLOYEE_FIELD_LIMITS.designation}
        disabled={disabled}
        onChange={(value) => onChange('designation', value)}
      />
    </div>
  )
}

export default EmployeeFormFields
