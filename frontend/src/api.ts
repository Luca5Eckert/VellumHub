import createClient from 'openapi-fetch'
import type { paths as PreferencePaths } from './preferences.generated'

const gateway = import.meta.env.VITE_GATEWAY_URL ?? 'http://127.0.0.1:8080'

export interface Book {
  id: string
  title: string
  author: string
  description: string | null
  coverUrl?: string
  pageCount?: number
  genres: string[]
  releaseYear?: number
  publisher?: string
  isbn?: string
}

export interface Reading {
  bookId: string
  status: 'WANT_TO_READ' | 'READING' | 'COMPLETED' | 'PAUSED' | 'ABANDONED'
  currentPage: number
}

export interface BookReading extends Omit<Reading, 'bookId'> {
  id: string
  readingNumber: number
  startedAt: string | null
  endAt: string | null
  targetEndDate: string | null
  pageCount: number
}

export interface BookList {
  id: string
  booksIds: string[]
  userOwner: string
}

export class ApiError extends Error {
  constructor(readonly status: number) {
    super(status === 401 ? 'Sua sessão expirou. Entre novamente.' :
      status === 403 ? 'Sua sessão não tem permissão para esta operação.' :
      status === 429 ? 'Muitas solicitações. Aguarde um momento e tente novamente.' :
      `A solicitação falhou (HTTP ${status}).`)
  }
}

export function api(token: string) {
  const preferencesClient = createClient<PreferencePaths>({
    baseUrl: gateway,
    headers: { Authorization: `Bearer ${token}` },
  })

  async function preferenceResult<T>(operation: Promise<{ data?: T; response: Response }>): Promise<T> {
    const { data, response } = await operation
    if (!response.ok) throw new ApiError(response.status)
    if (data === undefined) throw new Error('Missing preference response')
    return data
  }

  async function request<T>(path: string, method = 'GET', body?: object): Promise<T> {
    const response = await fetch(`${gateway}/api/v1${path}`, {
      method,
      headers: { Authorization: `Bearer ${token}`, ...(body && { 'Content-Type': 'application/json' }) },
      body: body && JSON.stringify(body),
    })
    if (!response.ok) throw new ApiError(response.status)
    return (response.headers.get('content-type')?.includes('application/json')
      ? await response.json()
      : undefined) as T
  }

  const readingsPath = (bookId: string) => `/catalog/book-progress/${encodeURIComponent(bookId)}/readings`
  const readingPath = (bookId: string, readingId: string) => `${readingsPath(bookId)}/${encodeURIComponent(readingId)}`

  return {
    profile: () => request<{ name: string }>('/users/me'),
    genres: () => preferenceResult(preferencesClient.GET('/api/v1/users/genres')),
    preferences: () => preferenceResult(preferencesClient.GET('/api/v1/users/me/preferences')),
    savePreferences: (genres: string[]) => preferenceResult(preferencesClient.PUT('/api/v1/users/me/preferences', { body: { genres } })),
    recommendations: () => request<Book[]>('/recommendations?limit=12'),
    book: (id: string) => request<Book>(`/catalog/books/${encodeURIComponent(id)}`),
    catalog: () => request<Book[]>('/catalog/books?pageSize=12'),
    likeReadBook: (id: string) =>
      request<void>('/engagement/reactions', 'POST', { bookId: id, typeReaction: 'POSITIVE' }),
    search: (query: string) => request<Book[]>(`/catalog/books/search?q=${encodeURIComponent(query)}`),
    readingList: () => request<Reading[]>('/catalog/book-progress/reading-list'),
    lists: () => request<BookList[]>('/catalog/book-list?Number+of+page=0&Size+of+page=20'),
    addToList: (listId: string, bookId: string) =>
      request<void>(`/catalog/book-list/${encodeURIComponent(listId)}/books/${encodeURIComponent(bookId)}`, 'POST'),
    rate: (id: string, stars: number, review: string) =>
      request<void>('/engagement/rating', 'POST', { bookId: id, stars, ...(review && { review }) }),
    deleteReading: (bookId: string, readingId: string) => request<void>(readingPath(bookId, readingId), 'DELETE'),
    bookReadings: (bookId: string) => request<BookReading[]>(readingsPath(bookId)),
    startReading: (bookId: string, startedAt?: string) =>
      request<BookReading>(readingsPath(bookId), 'POST', startedAt ? { startedAt } : {}),
    updateProgress: (bookId: string, readingId: string, progress: { page?: number, percentage?: number, readAt?: string }) =>
      request<BookReading>(`${readingPath(bookId, readingId)}/progress`, 'PATCH', progress),
    transitionReading: (bookId: string, readingId: string, status: Reading['status']) =>
      request<BookReading>(`${readingPath(bookId, readingId)}/status`, 'PATCH', { status }),
    setReadingTarget: (bookId: string, readingId: string, targetEndDate: string | null) =>
      request<BookReading>(`${readingPath(bookId, readingId)}/target`, 'PATCH', { targetEndDate }),
  }
}

export const errorMessage = (cause: unknown) => cause instanceof ApiError
  ? cause.message
  : 'O serviço está indisponível. Tente novamente em instantes.'
