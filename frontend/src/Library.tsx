import { useCallback, useEffect, useMemo, useState } from 'react'
import { BookOpen, House, LibraryBig, List, Search, SlidersHorizontal, Sparkles, UserRound } from 'lucide-react'
import { api, errorMessage, type Book, type BookList, type Reading } from './api'
import { BookDetail } from './BookDetail'
import { Button } from './Button'
import { Onboarding } from './Onboarding'
import { ReadOnboarding } from './ReadOnboarding'
import { Cover, Dialog, genreLabel, Logo, Retry, ScreenHeading, ThemeButton } from './ui'

const tabs = [
  { id: 'home', label: 'Início', icon: House },
  { id: 'search', label: 'Buscar', icon: Search },
  { id: 'shelf', label: 'Estante', icon: LibraryBig },
  { id: 'lists', label: 'Listas', icon: List },
  { id: 'profile', label: 'Perfil', icon: UserRound },
] as const

type Tab = typeof tabs[number]['id']
type Load = 'loading' | 'ready' | 'error'

interface Props {
  token: string
  userId: string
  name?: string
  dark: boolean
  toggleTheme: () => void
  signOut: () => void
}

function Shelf({ books, open }: { books: Book[]; open: (book: Book) => void }) {
  return <ul className="shelf">{books.map((book, index) => <li key={book.id}>
    <button className="book-card" onClick={() => open(book)}>
      <Cover book={book} index={index} /><strong>{book.title}</strong><span>{book.author}</span>
    </button>
  </li>)}</ul>
}

export function Library({ token, userId, name, dark, toggleTheme, signOut }: Props) {
  const client = useMemo(() => api(token), [token])
  const [profileName, setProfileName] = useState(name)
  const [continueBooks, setContinueBooks] = useState<Book[]>([])
  const [initialized, setInitialized] = useState<Load>('loading')
  const [genreScreen, setGenreScreen] = useState<'onboarding' | 'edit'>()
  const [readOnboarding, setReadOnboarding] = useState(false)
  const [genres, setGenres] = useState<string[]>([])
  const [savedGenres, setSavedGenres] = useState<string[]>([])
  const [selection, setSelection] = useState<string[]>([])
  const [filter, setFilter] = useState('')
  const [tab, setTab] = useState<Tab>('home')
  const [recommendations, setRecommendations] = useState<Book[]>([])
  const [recommendationState, setRecommendationState] = useState<Load>('loading')
  const [readings, setReadings] = useState<Reading[]>([])
  const [shelf, setShelf] = useState<Book[]>([])
  const [lists, setLists] = useState<BookList[]>([])
  const [searchState, setSearchState] = useState<Load>('ready')
  const [browseStatus, setBrowseStatus] = useState<Record<'shelf' | 'lists', Load>>({ shelf: 'ready', lists: 'ready' })
  const [selected, setSelected] = useState<Book>()
  const [opening, setOpening] = useState(false)
  const [reason, setReason] = useState<Book>()
  const [query, setQuery] = useState('')
  const [searchResults, setSearchResults] = useState<Book[] | null>(null)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')

  const loadRecommendations = useCallback(async () => {
    setRecommendationState('loading')
    try {
      setRecommendations(await client.recommendations())
      setRecommendationState('ready')
    } catch {
      setRecommendationState('error')
    }
  }, [client])

  const initialize = useCallback(async () => {
    setInitialized('loading')
    setError('')
    try {
      const [available, saved, currentReadings] = await Promise.all([
        client.genres(), client.preferences(), client.readingList(),
      ])
      setGenres(available.genres)
      setSavedGenres(saved.genres)
      setReadings(currentReadings)
      void client.profile().then(profile => {
        if (profile.name) setProfileName(profile.name)
      }).catch(() => { /* The profile name is optional. */ })
      const activeIds = currentReadings.filter(reading => reading.status === 'READING').slice(0, 2)
      void Promise.allSettled(activeIds.map(reading => client.book(reading.bookId))).then(results =>
        setContinueBooks(results.flatMap(result => result.status === 'fulfilled' ? [result.value] : [])))
      setSelection(saved.genres.filter(genre => available.genres.includes(genre)))
      setGenreScreen(saved.genres.length ? undefined : 'onboarding')
      setInitialized('ready')
      if (saved.genres.length) void loadRecommendations()
    } catch (cause) {
      setError(errorMessage(cause))
      setInitialized('error')
    }
  }, [client, loadRecommendations])

  useEffect(() => { void initialize() }, [initialize])

  async function loadTab(next: Tab) {
    setSelected(undefined)
    setTab(next)
    setError('')
    if (next === 'shelf' || next === 'lists') {
      setBrowseStatus(current => ({ ...current, [next]: 'loading' }))
      try {
        if (next === 'shelf') {
          const result = await client.readingList()
          setReadings(result)
          setShelf(await Promise.all([...new Set(result.map(item => item.bookId))].map(id => client.book(id))))
        } else setLists(await client.lists())
        setBrowseStatus(current => ({ ...current, [next]: 'ready' }))
      } catch {
        setBrowseStatus(current => ({ ...current, [next]: 'error' }))
      }
    }
  }

  async function search() {
    if (!query.trim()) return
    setSearchState('loading')
    setError('')
    try {
      setSearchResults(await client.search(query.trim()))
      setSearchState('ready')
    } catch {
      setSearchState('error')
    }
  }

  async function openBook(book: Book) {
    setOpening(true)
    setError('')
    try {
      setSelected({ ...book, ...await client.book(book.id) })
      window.scrollTo(0, 0)
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setOpening(false)
    }
  }

  function editGenres() {
    setSelection(savedGenres.filter(genre => genres.includes(genre)))
    setError('')
    setReason(undefined)
    setGenreScreen('edit')
  }

  function leaveGenres() {
    if (genreScreen === 'onboarding') setReadOnboarding(true)
    setError('')
    setGenreScreen(undefined)
  }

  async function saveGenres() {
    if (saving || selection.length < 3) return
    setSaving(true)
    setError('')
    try {
      const saved = await client.savePreferences(selection)
      setSavedGenres(saved.genres)
      setGenreScreen(undefined)
      setNotice('Preferências salvas.')
      setFilter('')
      setSelected(undefined)
      setTab('home')
      if (genreScreen === 'onboarding') setReadOnboarding(true)
      else void loadRecommendations()
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setSaving(false)
    }
  }

  if (initialized === 'loading') return <main className="standalone"><p role="status">Carregando sua biblioteca…</p></main>
  if (initialized === 'error') return <main className="standalone"><Retry message={error} retry={() => void initialize()} /></main>
  if (genreScreen) return <Onboarding genres={genres} selected={selection} pending={saving} error={error}
    editing={genreScreen === 'edit'} onSave={() => void saveGenres()} onSkip={leaveGenres}
    onChange={genre => setSelection(current => current.includes(genre) ? current.filter(value => value !== genre) : [...current, genre])} />

  if (readOnboarding) return <ReadOnboarding client={client} finish={() => {
    setReadOnboarding(false)
    setTab('home')
    void loadRecommendations()
  }} />

  const filtered = recommendations.filter(book => !filter || book.genres.includes(filter))
  const featured = filtered[0]
  const matchingGenres = reason?.genres.filter(genre => savedGenres.includes(genre)) ?? []
  const visibleLoad = tab === 'shelf' || tab === 'lists' ? browseStatus[tab] : searchState

  return <div className="app-shell">
    <header className="app-top">
      <div className="mobile-logo"><Logo /></div>
      <button className="search-shortcut" onClick={() => void loadTab('search')}><Search size={18} aria-hidden /> Buscar livros, autores, ISBN…</button>
      <ThemeButton dark={dark} toggle={toggleTheme} />
    </header>
    <aside className="app-sidebar">
      <div className="sidebar-logo"><Logo /></div>
      <nav aria-label="Navegação principal">
        {tabs.map(({ id, label, icon: Icon }) => <button key={id} type="button"
          aria-current={tab === id && !selected ? 'page' : undefined} disabled={opening}
          onClick={() => void loadTab(id)}><Icon size={20} aria-hidden /><span>{label}</span></button>)}
      </nav>
    </aside>
    <main className="app-content">
      {error && <p role="alert" className="error">{error}</p>}
      {opening && <p role="status">Carregando livro…</p>}
      {selected ? <BookDetail key={selected.id} book={selected} client={client} userId={userId}
        onBack={() => setSelected(undefined)}
        onReading={reading => {
          setReadings(current => {
            const others = current.filter(item => item.bookId !== selected.id)
            return reading ? [...others, reading] : others
          })
          setContinueBooks(current => {
            const others = current.filter(book => book.id !== selected.id)
            return reading?.status === 'READING' ? [selected, ...others].slice(0, 2) : others
          })
        }} />
        : tab === 'home' ? <>
          <header className="screen-intro">
            <p className="eyebrow">{new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }).format(new Date())}</p>
            <ScreenHeading>{profileName ? `Olá, ${profileName.trim().split(/\s+/)[0]}` : 'Recomendações'}</ScreenHeading>
            <p className="muted">Suas próximas leituras, selecionadas para você.</p>
          </header>
          {notice && <p role="status" className="confirmation">{notice}</p>}
          {continueBooks.length > 0 && <section className="continue-reading">
            <h2>Continue lendo</h2>
            <ul>{continueBooks.map(book => {
              const reading = readings.find(item => item.bookId === book.id && item.status === 'READING')
              return <li key={book.id}>
                <Cover book={book} />
                <div><strong>{book.title}</strong><p className="muted">Pág. {reading?.currentPage ?? 0}{book.pageCount ? ` de ${book.pageCount}` : ''}</p>
                  {book.pageCount ? <progress value={reading?.currentPage ?? 0} max={book.pageCount} aria-label={`Progresso de ${book.title}`} /> : null}
                </div>
                <Button variant="secondary" disabled={opening} onClick={() => void openBook(book)}>Abrir leitura</Button>
              </li>
            })}</ul>
          </section>}
          <div className="chips filters" aria-label="Filtrar por gênero">
            <button className="chip" aria-pressed={!filter} onClick={() => setFilter('')}><Sparkles size={15} aria-hidden /> Para você</button>
            {(savedGenres.length ? savedGenres : genres.slice(0, 5)).map(genre => <button key={genre} className="chip"
              aria-pressed={filter === genre} onClick={() => setFilter(genre)}>{genreLabel(genre)}</button>)}
          </div>
          {recommendationState === 'loading' ? <p role="status">Carregando recomendações…</p>
            : recommendationState === 'error' ? <Retry message="As recomendações estão indisponíveis no momento." retry={() => void loadRecommendations()} />
              : featured ? <>
                <article className="feature">
                  <Cover book={featured} />
                  <div className="feature-copy">
                    <h2>{featured.title}</h2>
                    <p className="muted">{featured.author}</p>
                    {featured.description && <p className="feature-description">{featured.description}</p>}
                    <div className="actions">
                      <Button disabled={opening} onClick={() => void openBook(featured)}><BookOpen size={17} aria-hidden /> Ver livro</Button>
                      <button className="text-button" onClick={() => setReason(featured)}><Sparkles size={16} aria-hidden /> Por que este livro?</button>
                    </div>
                  </div>
                </article>
                {filtered.length > 1 && <section className="shelf-section"><h2>Mais recomendações</h2><Shelf books={filtered.slice(1)} open={book => void openBook(book)} /></section>}
              </> : <div className="empty"><p>{filter ? 'Nenhuma recomendação neste gênero.' : 'Ainda não há recomendações disponíveis.'}</p>
                <Button variant="secondary" onClick={filter ? () => setFilter('') : () => void loadRecommendations()}>{filter ? 'Ver todos os gêneros' : 'Atualizar'}</Button>
              </div>}
        </> : tab === 'profile' ? <section>
          <ScreenHeading>Perfil</ScreenHeading>
          {profileName && <p className="muted">{profileName}</p>}
          <h2>Gêneros favoritos</h2>
          <div className="chips">{savedGenres.map(genre => <span key={genre} className="chip static">{genreLabel(genre)}</span>)}</div>
          <div className="actions"><Button onClick={editGenres}>Editar gêneros</Button><Button variant="secondary" onClick={signOut}>Sair</Button></div>
        </section> : <section key={tab}>
          <ScreenHeading>{tabs.find(item => item.id === tab)?.label}</ScreenHeading>
          {tab === 'search' && <form className="catalog-search" onSubmit={event => { event.preventDefault(); void search() }}>
            <label className="sr-only" htmlFor="book-search">Buscar no catálogo</label>
            <input id="book-search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Buscar livros, autores, ISBN…" required />
            <Button type="submit" disabled={searchState === 'loading'}><Search size={17} aria-hidden /> Buscar</Button>
          </form>}
          {visibleLoad === 'loading' ? <p role="status">Carregando…</p>
            : visibleLoad === 'error' ? <Retry message="Não foi possível carregar os dados." retry={() => {
              if (tab === 'search') void search()
              else void loadTab(tab)
            }} />
              : tab === 'lists' ? (lists.length ? <ul className="plain-list">{lists.map((list, index) => <li key={list.id}><h2>Lista {index + 1}</h2><p>{list.booksIds.length} livros</p></li>)}</ul>
                : <p className="muted">Nenhuma lista disponível.</p>)
                : tab === 'search' && searchResults === null ? <p className="muted">Pesquise pelo título ou pelo autor.</p>
                  : (tab === 'search' ? searchResults ?? [] : shelf).length > 0
                    ? <Shelf books={tab === 'search' ? searchResults ?? [] : shelf} open={book => void openBook(book)} />
                    : <p className="muted">{tab === 'search' ? 'Nenhum livro encontrado.' : 'Sua estante ainda está vazia.'}</p>}
        </section>}
    </main>

    {reason && <Dialog title="Por que este livro?" onClose={() => setReason(undefined)}>
      <div className="dialog-book"><Cover book={reason} /><div><strong>{reason.title}</strong><p className="muted">{reason.author}</p></div></div>
      {matchingGenres.length > 0 && <p><strong>Gêneros em comum</strong><br />{matchingGenres.map(genreLabel).join(', ')}</p>}
      <p className="muted">As recomendações podem usar suas avaliações, seu progresso de leitura e seus gêneros preferidos.</p>
      <div className="dialog-actions">
        <Button variant="secondary" onClick={editGenres}><SlidersHorizontal size={17} aria-hidden /> Ajustar preferências</Button>
        <Button onClick={() => { setReason(undefined); void openBook(reason) }}>Ver livro</Button>
      </div>
    </Dialog>}
  </div>
}
