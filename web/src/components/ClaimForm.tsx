import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { CATEGORIES, type Category, type Item } from '../api/types'
import { formatMoney } from '../format'

export interface ClaimFormValues {
  title: string
  items: Item[]
}

interface Props {
  initial?: ClaimFormValues
  submitLabel: string
  onSubmit: (values: ClaimFormValues) => Promise<unknown>
}

const today = () => new Date().toISOString().slice(0, 10)
const emptyItem = (): Item => ({ spentOn: today(), category: 'TRAVEL', amount: 0, description: '' })

/**
 * Title and items of a claim. Checks what the user can fix right away (limits
 * per category, dates, amounts); the API checks everything again and its field
 * errors are shown next to the matching input.
 */
export default function ClaimForm({ initial, submitLabel, onSubmit }: Props) {
  const [title, setTitle] = useState(initial?.title ?? '')
  const [items, setItems] = useState<Item[]>(initial?.items.length ? initial.items : [emptyItem()])
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const total = items.reduce((sum, item) => sum + (Number.isFinite(item.amount) ? item.amount : 0), 0)

  const update = (index: number, patch: Partial<Item>) =>
    setItems((current) => current.map((item, i) => (i === index ? { ...item, ...patch } : item)))

  async function submit(event: FormEvent) {
    event.preventDefault()
    const found = validate(title, items)
    setErrors(found)
    setMessage(null)
    if (Object.keys(found).length > 0) return
    setSaving(true)
    try {
      await onSubmit({ title: title.trim(), items })
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
        setMessage(error.message)
      } else {
        setMessage('Something went wrong; try again.')
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-5" noValidate>
      <div>
        <label htmlFor="title" className="block text-sm font-medium">Title</label>
        <input id="title" value={title} maxLength={120} onChange={(e) => setTitle(e.target.value)}
          className="mt-1 w-full rounded-md border border-slate-300 px-3 py-2" placeholder="Client visit in Rabat" />
        <FieldError message={errors.title} />
      </div>

      <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white">
        <table className="w-full text-sm">
          <thead className="bg-slate-50 text-left text-slate-600">
            <tr>
              <th className="px-3 py-2">Date</th>
              <th className="px-3 py-2">Category</th>
              <th className="px-3 py-2">Description</th>
              <th className="px-3 py-2 text-right">Amount (MAD)</th>
              <th className="px-3 py-2" />
            </tr>
          </thead>
          <tbody>
            {items.map((item, index) => (
              <tr key={index} className="border-t border-slate-100 align-top">
                <td className="px-3 py-2">
                  <input type="date" aria-label={`Date of item ${index + 1}`} value={item.spentOn} max={today()}
                    onChange={(e) => update(index, { spentOn: e.target.value })}
                    className="rounded border border-slate-300 px-2 py-1" />
                  <FieldError message={errors[`items[${index}].spentOn`]} />
                </td>
                <td className="px-3 py-2">
                  <select aria-label={`Category of item ${index + 1}`} value={item.category}
                    onChange={(e) => update(index, { category: e.target.value as Category })}
                    className="rounded border border-slate-300 px-2 py-1">
                    {CATEGORIES.map((c) => (
                      <option key={c.value} value={c.value}>{c.label} (max {c.limit})</option>
                    ))}
                  </select>
                </td>
                <td className="px-3 py-2">
                  <input aria-label={`Description of item ${index + 1}`} value={item.description} maxLength={200}
                    onChange={(e) => update(index, { description: e.target.value })}
                    className="w-full rounded border border-slate-300 px-2 py-1" />
                  <FieldError message={errors[`items[${index}].description`]} />
                </td>
                <td className="px-3 py-2 text-right">
                  <input type="number" step="0.01" min="0.01" aria-label={`Amount of item ${index + 1}`}
                    value={Number.isFinite(item.amount) && item.amount !== 0 ? item.amount : ''}
                    onChange={(e) => update(index, { amount: e.target.valueAsNumber })}
                    className="w-32 rounded border border-slate-300 px-2 py-1 text-right" />
                  <FieldError message={errors[`items[${index}].amount`]} />
                </td>
                <td className="px-3 py-2">
                  <button type="button" onClick={() => setItems((c) => c.filter((_, i) => i !== index))}
                    className="text-rose-700 hover:underline" aria-label={`Remove item ${index + 1}`}>
                    Remove
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="border-t border-slate-200 font-medium">
              <td className="px-3 py-2" colSpan={3}>
                <button type="button" onClick={() => setItems((c) => [...c, emptyItem()])}
                  className="text-brand-700 hover:underline">+ Add an item</button>
              </td>
              <td className="px-3 py-2 text-right">{formatMoney(total)}</td>
              <td />
            </tr>
          </tfoot>
        </table>
      </div>
      <FieldError message={errors.items} />

      {message && <p role="alert" className="rounded-md bg-rose-50 px-3 py-2 text-sm text-rose-800">{message}</p>}
      <button type="submit" disabled={saving}
        className="rounded-md bg-brand-700 px-4 py-2 font-medium text-white hover:bg-brand-900 disabled:opacity-60">
        {saving ? 'Saving…' : submitLabel}
      </button>
    </form>
  )
}

function FieldError({ message }: { message?: string }) {
  return message ? <p className="mt-1 text-xs text-rose-700">{message}</p> : null
}

/** Same rules as the API; exported for the tests. */
export function validate(title: string, items: Item[]): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!title.trim()) errors.title = 'a title is required'
  items.forEach((item, i) => {
    const limit = CATEGORIES.find((c) => c.value === item.category)!.limit
    if (!Number.isFinite(item.amount) || item.amount <= 0) errors[`items[${i}].amount`] = 'must be greater than 0'
    // Checked on the text, not with floating-point arithmetic (0.29 * 100 is not 29).
    else if (!/^\d+(\.\d{1,2})?$/.test(String(item.amount))) errors[`items[${i}].amount`] = 'two decimals at most'
    else if (item.amount > limit) errors[`items[${i}].amount`] = `the limit for ${item.category} is ${limit}.00 MAD per item`
    if (!item.description.trim()) errors[`items[${i}].description`] = 'a description is required'
    if (!item.spentOn || item.spentOn > today()) errors[`items[${i}].spentOn`] = 'must be today or earlier'
  })
  return errors
}
