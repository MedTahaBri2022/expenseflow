export type Role = 'EMPLOYEE' | 'MANAGER' | 'FINANCE'
export type ClaimStatus = 'DRAFT' | 'SUBMITTED' | 'PENDING_FINANCE' | 'APPROVED' | 'REJECTED' | 'PAID'
export type ClaimAction = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'REOPEN' | 'PAY'
export type Category = 'MEALS' | 'TRAVEL' | 'LODGING' | 'SUPPLIES' | 'OTHER'

export const CATEGORIES: { value: Category; label: string; limit: number }[] = [
  { value: 'MEALS', label: 'Meals', limit: 500 },
  { value: 'TRAVEL', label: 'Travel', limit: 5000 },
  { value: 'LODGING', label: 'Lodging', limit: 2000 },
  { value: 'SUPPLIES', label: 'Supplies', limit: 1500 },
  { value: 'OTHER', label: 'Other', limit: 1000 },
]

export interface Me {
  id: number
  fullName: string
  email: string
  role: Role
  managerName: string | null
}

export interface Person {
  id: number
  fullName: string
  email: string
}

export interface Item {
  id?: number
  spentOn: string
  category: Category
  amount: number
  description: string
}

export interface ClaimSummary {
  id: number
  title: string
  owner: Person
  status: ClaimStatus
  total: number
  createdAt: string
  submittedAt: string | null
}

export interface ClaimDetail extends ClaimSummary {
  currency: string
  version: number
  items: Item[]
  allowedActions: ClaimAction[]
  editable: boolean
}

export interface ClaimEvent {
  id: number
  actor: Person
  fromStatus: ClaimStatus | null
  toStatus: ClaimStatus
  comment: string | null
  occurredAt: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Summary {
  from: string
  to: string
  currency: string
  byStatus: { status: ClaimStatus; claims: number; amount: number }[]
  byCategory: { category: Category; items: number; amount: number }[]
  approvedByMonth: { month: string; claims: number; amount: number }[]
}
