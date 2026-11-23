import { useState, type FormEvent } from 'react'
import { ArrowLeft, BookOpen, Bookmark, CalendarDays, Star } from 'lucide-react'
import { type api, type Book, type BookList, type Reading, errorMessage } from './api'
import { Button } from './Button'
import { Cover, Dialog, genreLabel, ScreenHeading } from './ui'

const ratingLabels = ['Não gostei', 'Regular', 'Gostei', 'Gostei muito', 'Adorei']

interface Props {
  book: Book
  reading?: Reading
  client: ReturnType<typeof api>
  userId: string
  onReading: (reading: Reading) => void
  onBack: () => void
}

export function BookDetail({ book, reading, client, userId, onReading, onBack }: Props) {
  const [dialog, setDialog] = useState<'rating' | 'progress' | 'lists'>()
  const [lists, setLists] = useState<BookList[]>([])
  const [stars, setStars] = useState(5)
  const [review, setReview] = useState('')
  const [page, setPage] = useState(reading?.currentPage ?? 0)
  const [mode, setMode] = useState<'page' | 'percent'>('page')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const active = reading?.status === 'READING'
  const canRead = !reading || active
  const pages = book.pageCount || undefined
  const percentage = (value: number) => pages ? Math.round(value * 100 / pages) : 0
  const date = new Intl.DateTimeFormat('pt-BR', { day: 'numeric', month: 'short' }).format(new Date())

  function open(next: typeof dialog) {
    setError('')
    setStatus('')
    setDialog(next)
  }

  async function run(action: () => Promise<void>, success: string) {
    setPending(true)
    setError('')
    try {
      await action()
      setDialog(undefined)
      setStatus(success)
    } catch (cause) {
      setError(errorMessage(cause))
    } finally {
      setPending(false)
    }
  }

  function submitProgress(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    void run(async () => {
      if (active) await client.updateProgress(book.id, page)
      else await client.startReading(book.id, page)
      onReading({ bookId: book.id, status: 'READING', currentPage: page })
    }, active ? 'Progresso atualizado.' : 'Leitura iniciada.')
  }

  function openLists() {
    setPending(true)
    setError('')
    void client.lists().then(result => {
      setLists(result.filter(list => list.userOwner === userId))
      open('lists')
    }).catch(cause => setError(errorMessage(cause))).finally(() => setPending(false))
  }

  return <article className="book-detail">
    <button className="text-button back" onClick={onBack}><ArrowLeft size={17} aria-hidden /> Voltar</button>
    <div className="detail-layout">
      <aside className="detail-cover">
        <Cover book={book} />
        <Button variant="secondary" disabled={pending} onClick={openLists}><Bookmark size={17} aria-hidden /> Salvar em lista</Button>
      </aside>
      <div className="detail-content">
        <ScreenHeading>{book.title}</ScreenHeading>
        <p className="muted">{book.author}</p>
        <div className="chips">{book.genres.map(genre => <span key={genre} className="chip static">{genreLabel(genre)}</span>)}</div>
        <section className="reading-card" aria-labelledby="reading-heading">
          <h2 id="reading-heading">Suas leituras</h2>
          {reading && <p className="reading-status">{active ? 'Em andamento' : reading.status === 'COMPLETED' ? 'Concluída' : 'Quero ler'}</p>}
          {active && <>
            <p>Página {reading.currentPage}{pages ? ` de ${pages} · ${percentage(reading.currentPage)}% lido` : ''}</p>
            {pages && <progress aria-label="Progresso atual" value={reading.currentPage} max={pages} />}
          </>}
          <div className="actions">
            {canRead && <Button onClick={() => {
              setMode('page')
              setPage(reading?.currentPage ?? 0)
              open('progress')
            }}><BookOpen size={17} aria-hidden /> {active ? 'Atualizar progresso' : 'Iniciar leitura'}</Button>}
            <Button variant="secondary" onClick={() => open('rating')}><Star size={17} aria-hidden /> Avaliar livro</Button>
          </div>
          {status && <p role="status" className="confirmation">{status}</p>}
          {!dialog && error && <p role="alert" className="error">{error}</p>}
        </section>
        {book.description && <section className="about"><h2>Sobre o livro</h2><p>{book.description}</p></section>}
        <dl className="book-metadata">
          {pages && <div><dt>Páginas</dt><dd>{pages}</dd></div>}
          {book.releaseYear ? <div><dt>Ano</dt><dd>{book.releaseYear}</dd></div> : null}
          {book.publisher && <div><dt>Editora</dt><dd>{book.publisher}</dd></div>}
          {book.isbn && <div><dt>ISBN</dt><dd>{book.isbn}</dd></div>}
        </dl>
      </div>
    </div>

    {dialog && <Dialog title={dialog === 'rating' ? 'Avaliar livro' : dialog === 'lists' ? 'Salvar em lista' :
      active ? 'Atualizar progresso' : 'Iniciar leitura'} onClose={() => { if (!pending) setDialog(undefined) }}>
      <div className="dialog-book"><Cover book={book} /><div><strong>{book.title}</strong><p className="muted">{book.author}</p></div></div>
      {dialog === 'rating' && <form onSubmit={event => {
        event.preventDefault()
        void run(() => client.rate(book.id, stars, review.trim()), 'Avaliação enviada.')
      }}>
        <fieldset className="rating">
          <legend className="sr-only">Estrelas</legend>
          {ratingLabels.map((label, index) => <label key={label} className="star">
            <input type="radio" className="sr-only" name="stars" value={index + 1} checked={stars === index + 1}
              onChange={() => setStars(index + 1)} aria-label={`${index + 1} de 5 · ${label}`} />
            <Star size={25} fill={stars > index ? 'currentColor' : 'none'} aria-hidden />
          </label>)}
        </fieldset>
        <p className="rating-caption">{stars} de 5 · {ratingLabels[stars - 1]}</p>
        <label className="field">Resenha (opcional)
          <textarea maxLength={1000} placeholder="O que você achou do livro?" value={review} onChange={event => setReview(event.target.value)} />
          <span className="field-note">{review.length}/1000</span>
        </label>
        <div className="dialog-actions">
          <button type="button" className="text-button" disabled={pending} onClick={() => setDialog(undefined)}>Cancelar</button>
          <Button type="submit" disabled={pending}>Salvar avaliação</Button>
        </div>
      </form>}
      {dialog === 'progress' && <form onSubmit={submitProgress}>
        <p className="date-label"><CalendarDays size={16} aria-hidden /> Hoje, {date}</p>
        {pages && <div className="chips" aria-label="Unidade do progresso">
          <button type="button" className="chip" aria-pressed={mode === 'page'} onClick={() => setMode('page')}>Página</button>
          <button type="button" className="chip" aria-pressed={mode === 'percent'} onClick={() => setMode('percent')}>%</button>
        </div>}
        <label className="field">{mode === 'page' ? 'Página atual' : 'Porcentagem lida'}
          <input type="number" min={active ? (mode === 'page' ? reading.currentPage : percentage(reading.currentPage)) : 0}
            max={mode === 'page' ? pages : 100} step="1" required value={Number.isNaN(page) ? '' : mode === 'page' ? page : percentage(page)}
            onChange={event => {
              const value = event.target.valueAsNumber
              setPage(mode === 'percent' && pages ? Math.max(reading?.currentPage ?? 0, Math.round(value * pages / 100)) : value)
            }} />
        </label>
        {pages && <>
          <progress aria-label="Progresso previsto" value={page || 0} max={pages} />
          <p className="muted">Página {page || 0} de {pages} · {percentage(page || 0)}% lido</p>
        </>}
        <div className="dialog-actions">
          <button type="button" className="text-button" disabled={pending} onClick={() => setDialog(undefined)}>Cancelar</button>
          <Button type="submit" disabled={pending}>{active ? 'Salvar progresso' : 'Iniciar leitura'}</Button>
        </div>
      </form>}
      {dialog === 'lists' && (lists.length
        ? <ul className="plain-list">{lists.map((list, index) => <li key={list.id}>
          <Button variant="secondary" disabled={pending} onClick={() =>
            void run(() => client.addToList(list.id, book.id), 'Livro salvo na lista.')}>Lista {index + 1} · {list.booksIds.length} livros</Button>
        </li>)}</ul>
        : <p className="muted">Nenhuma lista disponível para salvar este livro.</p>)}
      {error && <p role="alert" className="error">{error}</p>}
    </Dialog>}
  </article>
}
