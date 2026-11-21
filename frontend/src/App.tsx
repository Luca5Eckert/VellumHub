import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import type { User } from 'oidc-client-ts'
import { api, ApiError, type Book } from './api'
import { Button } from './Button'
import { session } from './session'

const errorMessage = (cause: unknown) => cause instanceof ApiError
  ? cause.message
  : 'O serviço está indisponível. Tente novamente em instantes.'

function Cover({ book }: { book: Book }) {
  const url = book.coverUrl
  const [failed, setFailed] = useState(false)
  return url && /^https?:\/\//i.test(url) && !failed
    ? <img className="cover" src={url} alt="" loading="lazy" referrerPolicy="no-referrer" onError={() => setFailed(true)} />
    : <div className="cover fallback" aria-hidden="true">{book.title}</div>
}

export function App() {
  const [user, setUser] = useState<User | null>()
  const [error, setError] = useState('')
  const [dark, setDark] = useState(() => localStorage.getItem('theme') === 'dark' ||
    (!localStorage.getItem('theme') && matchMedia('(prefers-color-scheme: dark)').matches))

  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light'
    localStorage.setItem('theme', dark ? 'dark' : 'light')
  }, [dark])

  useEffect(() => {
    const callback = location.pathname === '/callback'
    const request = callback ? session.signinRedirectCallback() : session.getUser()
    request.then(current => {
      if (callback) history.replaceState(null, '', '/')
      setUser(current?.expired ? null : current)
    }).catch(() => {
      if (callback) history.replaceState(null, '', '/')
      setError('Não foi possível concluir o login. Tente novamente.')
      setUser(null)
    })
    return session.events.addAccessTokenExpired(() => {
      void session.removeUser().finally(() => setUser(null))
    })
  }, [])

  return <>
    <header className="header"><div className="frame header-content">
      <div className="brand">VellumHub</div>
      <div className="header-actions">
        <Button variant="secondary" onClick={() => setDark(value => !value)}
          aria-pressed={dark}>Tema escuro</Button>
        {user && <Button variant="secondary" onClick={() => {
          session.signoutRedirect().catch(() => setError('Não foi possível sair. Tente novamente.'))
        }}>Sair</Button>}
      </div>
    </div></header>
    <main className="frame">
      {error && <p role="alert" className="error">{error}</p>}
      {user === undefined ? <p role="status">Verificando sessão…</p> : user
        ? <Library key={user.profile.sub} token={user.access_token} />
        : <section className="welcome">
          <h1>Sua próxima leitura</h1>
          <p>Escolha seus gêneros favoritos e descubra livros para a sua lista.</p>
          <Button onClick={() => session.signinRedirect().catch(() =>
            setError('Não foi possível abrir o serviço de login.'))}>Entrar</Button>
        </section>}
    </main>
  </>
}

function Library({ token }: { token: string }) {
  const client = useMemo(() => api(token), [token])
  const [genres, setGenres] = useState<string[]>([])
  const [selection, setSelection] = useState<string[]>([])
  const [books, setBooks] = useState<Book[]>([])
  const [booksState, setBooksState] = useState<'loading' | 'ready' | 'unavailable'>('loading')
  const [preferencesSaved, setPreferencesSaved] = useState(false)
  const [selectedBook, setSelectedBook] = useState<Book>()
  const [screen, setScreen] = useState<'loading' | 'genres' | 'books' | 'error'>('loading')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  const loadBooks = useCallback(async () => {
    setScreen('books')
    setBooksState('loading')
    try {
      setBooks(await client.recommendations())
      setBooksState('ready')
    } catch {
      setBooksState('unavailable')
    }
  }, [client])

  const initialize = useCallback(async () => {
    setError('')
    setScreen('loading')
    try {
      const [available, saved] = await Promise.all([client.genres(), client.preferences()])
      setGenres(available.genres)
      setSelection(saved.genres.filter(genre => available.genres.includes(genre)))
      if (saved.genres.length) await loadBooks()
      else setScreen('genres')
    } catch (cause) {
      setError(errorMessage(cause))
      setScreen('error')
    }
  }, [client, loadBooks])

  useEffect(() => { void initialize() }, [initialize])

  async function run(action: () => Promise<void>) {
    setPending(true)
    setError('')
    try { await action() }
    catch (cause) { setError(errorMessage(cause)) }
    finally { setPending(false) }
  }

  return <>
    {error && <p role="alert" className="error">{error}</p>}
    {screen === 'loading' && <p role="status">Carregando sua biblioteca…</p>}
    {screen === 'error' && <Button onClick={() => void initialize()}>Tentar novamente</Button>}
    {screen === 'genres' && <form className="panel" onSubmit={event => {
      event.preventDefault()
      void run(async () => {
        const saved = await client.savePreferences(selection)
        setSelection(saved.genres)
        setPreferencesSaved(true)
        await loadBooks()
      })
    }}>
      <h1>O que você gosta de ler?</h1>
      <fieldset disabled={pending}>
        <legend>Selecione pelo menos três gêneros ou pule esta etapa.</legend>
        <div className="genre-grid">{genres.map(genre => <label className="genre" key={genre}>
          <input type="checkbox" checked={selection.includes(genre)} onChange={event => {
            const checked = event.target.checked
            setSelection(values => checked ? [...values, genre] : values.filter(value => value !== genre))
          }} />
          {genre}
        </label>)}</div>
      </fieldset>
      <div className="actions">
        <Button type="submit" disabled={pending || selection.length < 3}>Salvar preferências</Button>
        <Button variant="secondary" disabled={pending} onClick={() => void run(loadBooks)}>Pular</Button>
      </div>
    </form>}
    {screen === 'books' && (selectedBook
      ? <BookDetail book={selectedBook} client={client} onBack={() => setSelectedBook(undefined)} />
      : <section>
        <div className="section-heading"><h1>Recomendações</h1>
          <Button variant="secondary" disabled={booksState === 'loading' || pending} onClick={() => {
            setError('')
            setPreferencesSaved(false)
            setScreen('genres')
          }}>Editar gêneros</Button>
        </div>
        {preferencesSaved && <p role="status">Preferências salvas.</p>}
        {booksState === 'loading' ? <p role="status">Carregando recomendações…</p>
          : booksState === 'unavailable' ? <div className="panel">
            <p role="alert">As recomendações estão indisponíveis no momento.</p>
            <Button variant="secondary" disabled={pending} onClick={() => void loadBooks()}>Tentar carregar novamente</Button>
          </div>
          : books.length === 0 ? <div className="panel"><p>Ainda não há recomendações disponíveis.</p>
          <Button variant="secondary" disabled={pending} onClick={() => void run(loadBooks)}>Atualizar</Button></div>
          : <ul className="shelf">{books.map(book => <li key={book.id}>
            <button className="book-card" disabled={pending} onClick={() => void run(async () => {
              const details = await client.book(book.id)
              setSelectedBook({ ...book, ...details })
            })}>
              <Cover book={book} /><strong>{book.title}</strong><span>{book.author}</span>
            </button>
          </li>)}</ul>}
      </section>)}
  </>
}

function BookDetail({ book, client, onBack }: {
  book: Book
  client: ReturnType<typeof api>
  onBack: () => void
}) {
  const [stars, setStars] = useState(5)
  const [page, setPage] = useState('')
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  function submit(event: FormEvent, action: () => Promise<void>, success: string) {
    event.preventDefault()
    setError('')
    setStatus('')
    setPending(true)
    action().then(() => setStatus(success)).catch(cause => setError(errorMessage(cause)))
      .finally(() => setPending(false))
  }

  return <section>
    <Button variant="secondary" onClick={onBack}>Voltar às recomendações</Button>
    <div className="details"><Cover book={book} /><div>
      <h1>{book.title}</h1><p>{book.author}</p><p>{book.description}</p>
    </div></div>
    <p role="status">{status}</p>
    {error && <p role="alert" className="error">{error}</p>}
    <div className="form-grid">
      <form className="panel" onSubmit={event => submit(event, () => client.rate(book.id, stars), 'Avaliação enviada.')}>
        <h2>Avaliar livro</h2>
        <label>Estrelas
          <select value={stars} onChange={event => setStars(Number(event.target.value))}>
            {[5, 4, 3, 2, 1].map(value => <option key={value}>{value}</option>)}
          </select>
        </label>
        <Button type="submit" disabled={pending}>Enviar avaliação</Button>
      </form>
      <form className="panel" onSubmit={event => submit(event, () =>
        client.updateProgress(book.id, Number(page)), 'Progresso atualizado.')}>
        <h2>Progresso</h2>
        <label>Página atual<input type="number" min="0" max={book.pageCount} step="1" required
          value={page} onChange={event => setPage(event.target.value)} /></label>
        <div className="actions">
          <Button type="submit" disabled={pending}>Atualizar</Button>
          <Button variant="secondary" disabled={pending} onClick={event => {
            if (event.currentTarget.form?.reportValidity()) {
              submit(event, () => client.startReading(book.id, Number(page)), 'Leitura iniciada.')
            }
          }}>Iniciar leitura</Button>
        </div>
      </form>
    </div>
  </section>
}
