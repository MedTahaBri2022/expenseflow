import type { ClaimAction, ClaimStatus } from './api/types'

const money = new Intl.NumberFormat('fr-MA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const date = new Intl.DateTimeFormat('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
const dateTime = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeStyle: 'short' })

export const formatMoney = (amount: number, currency = 'MAD') => `${money.format(amount)} ${currency}`
export const formatDate = (iso: string) => date.format(new Date(iso))
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))

export const STATUS_LABELS: Record<ClaimStatus, string> = {
  DRAFT: 'Draft',
  SUBMITTED: 'Submitted',
  PENDING_FINANCE: 'Waiting for finance',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  PAID: 'Paid',
}

export const ACTION_LABELS: Record<ClaimAction, string> = {
  SUBMIT: 'Submit for approval',
  APPROVE: 'Approve',
  REJECT: 'Reject',
  REOPEN: 'Reopen as draft',
  PAY: 'Mark as paid',
}
