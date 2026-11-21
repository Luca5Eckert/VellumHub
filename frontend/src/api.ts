const gateway = import.meta.env.VITE_GATEWAY_URL ?? 'http://127.0.0.1:8080'

export interface Book {
  id: string
  title: string
  author: string
  description: string | null
  coverUrl?: string
  pageCount?: number
  genres: string[]
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

  return {
    genres: () => request<{ genres: string[] }>('/users/genres'),
    preferences: () => request<{ genres: string[] }>('/users/me/preferences'),
    savePreferences: (genres: string[]) => request<{ genres: string[] }>('/users/me/preferences', 'PUT', { genres }),
    recommendations: () => request<Book[]>('/recommendations?limit=12'),
    book: (id: string) => request<Book>(`/catalog/books/${encodeURIComponent(id)}`),
    rate: (id: string, stars: number) =>
      request<void>('/engagement/rating', 'POST', { bookId: id, stars }),
    startReading: (id: string, currentPage: number) =>
      request<void>(`/catalog/book-progress/${encodeURIComponent(id)}/status`, 'POST', {
        status: 'READING', currentPage, startedAt: new Date().toISOString(),
      }),
    updateProgress: (id: string, newCurrentPage: number) =>
      request<void>(`/catalog/book-progress/${encodeURIComponent(id)}/progress`, 'PUT', { newCurrentPage }),
  }
}
