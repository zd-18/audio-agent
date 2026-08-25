import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => cleanup())

const nativeGetComputedStyle = window.getComputedStyle.bind(window)
window.getComputedStyle = ((element: Element) => {
  const style = nativeGetComputedStyle(element)
  const numericProperties = new Set([
    'padding-top',
    'padding-bottom',
    'border-top-width',
    'border-bottom-width',
  ])
  const nativeGetPropertyValue = style.getPropertyValue.bind(style)
  style.getPropertyValue = (property: string) => {
    const value = nativeGetPropertyValue(property)
    if (!numericProperties.has(property)) return value
    return Number.isFinite(Number.parseFloat(value)) ? value : '0px'
  }
  return style
}) as typeof window.getComputedStyle

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => undefined,
    removeListener: () => undefined,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    dispatchEvent: () => false,
  }),
})

class TestResizeObserver implements ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}

Object.defineProperty(window, 'ResizeObserver', {
  writable: true,
  value: TestResizeObserver,
})

Object.defineProperty(Element.prototype, 'scrollIntoView', {
  writable: true,
  value: () => undefined,
})
