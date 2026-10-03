import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { Item } from '../api/types'
import ClaimForm, { validate } from './ClaimForm'

const item = (patch: Partial<Item> = {}): Item => ({
  spentOn: '2026-01-15', category: 'MEALS', amount: 120, description: 'Lunch', ...patch,
})

describe('validate', () => {
  it('accepts a valid claim', () => {
    expect(validate('Trip', [item()])).toEqual({})
  })

  it('applies the limit of the category', () => {
    expect(validate('Trip', [item({ amount: 500 })])).toEqual({})
    expect(validate('Trip', [item({ amount: 500.01 })])['items[0].amount']).toMatch(/limit for MEALS/)
    expect(validate('Trip', [item({ category: 'TRAVEL', amount: 4999.99 })])).toEqual({})
  })

  it('refuses more than two decimals, without floating-point false alarms', () => {
    expect(validate('Trip', [item({ amount: 0.29 })])).toEqual({})
    expect(validate('Trip', [item({ amount: 12.345 })])['items[0].amount']).toBe('two decimals at most')
  })

  it('refuses dates in the future and empty fields', () => {
    const errors = validate(' ', [item({ spentOn: '2999-01-01', description: '', amount: 0 })])
    expect(Object.keys(errors).sort()).toEqual(['items[0].amount', 'items[0].description', 'items[0].spentOn', 'title'])
  })
})

describe('ClaimForm', () => {
  it('does not call the API while the form is invalid', async () => {
    const onSubmit = vi.fn()
    render(<ClaimForm submitLabel="Save draft" onSubmit={onSubmit} />)

    await userEvent.click(screen.getByRole('button', { name: 'Save draft' }))

    expect(onSubmit).not.toHaveBeenCalled()
    expect(screen.getByText('a title is required')).toBeInTheDocument()
  })

  it('shows the field errors returned by the API next to the inputs', async () => {
    const onSubmit = vi.fn().mockRejectedValue(
      new ApiError(422, 'Some items exceed the company limits', { 'items[0].amount': 'the limit for MEALS is 500.00 MAD per item' }))
    render(<ClaimForm submitLabel="Save" initial={{ title: 'Dinner', items: [item()] }} onSubmit={onSubmit} />)

    await userEvent.click(screen.getByRole('button', { name: 'Save' }))

    expect(onSubmit).toHaveBeenCalledWith({ title: 'Dinner', items: [item()] })
    expect(await screen.findByRole('alert')).toHaveTextContent('Some items exceed the company limits')
    expect(screen.getByText('the limit for MEALS is 500.00 MAD per item')).toBeInTheDocument()
  })

  it('adds and removes items and keeps the total up to date', async () => {
    render(<ClaimForm submitLabel="Save" initial={{ title: 'Trip', items: [item({ amount: 100 })] }} onSubmit={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: '+ Add an item' }))
    await userEvent.type(screen.getByLabelText('Amount of item 2'), '50.5')
    expect(screen.getByText(/150,50 MAD/)).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Remove item 1' }))
    expect(screen.getByText(/50,50 MAD/)).toBeInTheDocument()
  })
})
