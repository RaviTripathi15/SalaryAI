import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { ReactElement } from 'react'
import type { Employee, EmployeePage } from '../api/employeeApi'

export const sampleEmployee: Employee = {
  id: 11,
  employeeCode: 'EMP-001',
  firstName: 'Ada',
  lastName: 'Lovelace',
  workEmail: 'ada@example.com',
  phone: '555-0100',
  department: 'Engineering',
  designation: 'Engineer',
  joiningDate: '2024-01-15',
  employmentStatus: 'ACTIVE',
  createdAt: '2024-01-15T09:00:00',
  updatedAt: '2024-01-16T09:00:00',
  deactivatedAt: null,
  version: 3,
}

export function employeePage(
  overrides: Partial<EmployeePage> = {},
): EmployeePage {
  return {
    content: [sampleEmployee],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
    first: true,
    last: true,
    ...overrides,
  }
}

export function renderEmployeeRoute(
  element: ReactElement,
  initialPath: string | { pathname: string; state?: unknown },
  routePath: string,
  additionalRoutes: ReactElement[] = [],
) {
  const container = document.createElement('div')
  document.body.appendChild(container)

  const root = createRoot(container)

  act(() => {
    root.render(
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path={routePath} element={element} />
          <Route path="/" element={<p>Sign in page</p>} />
          {additionalRoutes}
        </Routes>
      </MemoryRouter>,
    )
  })

  return {
    container,
    unmount() {
      act(() => root.unmount())
      container.remove()
    },
  }
}

export function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void

  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })

  return { promise, resolve, reject }
}

export async function settle() {
  await act(async () => {
    await new Promise<void>((resolve) => setTimeout(resolve, 0))
  })
}

export async function advanceDebounce() {
  await act(async () => {
    await new Promise<void>((resolve) => setTimeout(resolve, 340))
  })
}

function setNativeValue(element: HTMLInputElement | HTMLSelectElement, value: string) {
  const prototype =
    element instanceof HTMLSelectElement
      ? HTMLSelectElement.prototype
      : HTMLInputElement.prototype
  const setter = Object.getOwnPropertyDescriptor(prototype, 'value')?.set
  if (!setter) throw new Error('Native value setter was not available')

  setter.call(element, value)
}

export function setInput(container: HTMLElement, id: string, value: string) {
  const input = container.querySelector<HTMLInputElement>(`#${id}`)
  if (!input) throw new Error(`Input ${id} was not rendered`)

  act(() => {
    setNativeValue(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
  })
}

export function setSelect(container: HTMLElement, id: string, value: string) {
  const select = container.querySelector<HTMLSelectElement>(`#${id}`)
  if (!select) throw new Error(`Select ${id} was not rendered`)

  act(() => {
    setNativeValue(select, value)
    select.dispatchEvent(new Event('change', { bubbles: true }))
  })
}

export function click(container: HTMLElement, selector: string) {
  const element = container.querySelector<HTMLElement>(selector)
  if (!element) throw new Error(`Element ${selector} was not rendered`)

  act(() => {
    element.click()
  })
}

export function submitForm(container: HTMLElement) {
  const form = container.querySelector('form')
  if (!form) throw new Error('Form was not rendered')

  act(() => {
    form.dispatchEvent(
      new Event('submit', { bubbles: true, cancelable: true }),
    )
  })
}

export function pageText(container: HTMLElement): string {
  return container.textContent ?? ''
}
