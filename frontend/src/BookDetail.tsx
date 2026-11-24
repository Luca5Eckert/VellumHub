import { useEffect, useState, type FormEvent } from 'react'
import { ArrowLeft, BookOpen, Bookmark, CalendarDays, Star } from 'lucide-react'
import { type api, type Book, type BookList, type BookReading, type Reading, errorMessage } from './api'
import { Button } from './Button'
import { Cover, Dialog, genreLabel, ScreenHeading } from './ui'

const readingLabels: Record<Reading['status'], string> = {
  WANT_TO_READ: 'Quero ler', READING: 'Em andamento', PAUSED: 'Pausada',
  COMPLETED: 'Concluída', ABANDONED: 'Abandonada',
}

const ratingLabels = ['Não gostei', 'Regular', 'Gostei', 'Gostei muito', 'Adorei']

interface Props {
  book: Book
  client: ReturnType<typeof api>
  userId: string
  onReading: (reading: Reading) => void
  onBack: () => void
}

export function BookDetail({ book, client, userId, onReading, onBack }: Props) {
  const [dialog, setDialog] = useState<'rating' | 'progress' | 'lists' | 'target'>()
  const [lists, setLists] = useState<BookList[]>([])
  const [stars, setStars] = useState(5)
  const [review, setReview] = useState('')
  const [page, setPage] = useState(0)
  const [mode, setMode] = useState<'page' | 'percent'>('page')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [history, setHistory] = useState<BookReading[]>()
  const [retry, setRetry] = useState(0)
  const [readDate, setReadDate] = useState('')
  const [targetDate, setTargetDate] = useState('')
  const current = history?.find(item => item.status === 'READING' || item.status === 'PAUSED' || item.status === 'WANT_TO_READ')
  const active = current?.status === 'READING'

  useEffect(() => {
    let live = true
    void client.bookReadings(book.id).then(result => {
      if (live) setHistory(result)
    }).catch(cause => {
      if (live) setError(errorMessage(cause))
    })
    return () => { live = false }
  }, [book.id, client, retry])

  function apply(updated: BookReading) {
    setHistory(previous => [updated, ...(previous ?? []).filter(item => item.id !== updated.id)]
      .sort((first, second) => second.readingNumber - first.readingNumber))
    onReading({ bookId: book.id, status: updated.status, currentPage: updated.currentPage })
  }

  function transition(next: Reading['status']) {
    if (!current) return
    void run(async () => apply(await client.transitionReading(book.id, current.id, next)), readingLabels[next] + '.')
  }
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
      const date = readDate ? new Date(readDate).toISOString() : undefined
      if (current && active) {
        const input = mode === 'page' ? { page } : { percentage: page }
        apply(await client.updateProgress(book.id, current.id, { ...input, readAt: date }))
      } else {
        apply(await client.startReading(book.id, date))
      }
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
          {current && <p className="reading-status">{readingLabels[current.status]}</p>}
          {current?.status !== 'WANT_TO_READ' && current && <>
            <p>Página {current.currentPage}{pages ? ` de ${pages} · ${percentage(current.currentPage)}% lido` : ''}</p>
            {pages && <progress aria-label="Progresso atual" value={current.currentPage} max={pages} />}
            {current.targetEndDate && <p className="muted">Meta de término <time dateTime={current.targetEndDate}>
              {new Date(current.targetEndDate + 'T12:00:00').toLocaleDateString('pt-BR')}
            </time></p>}
          </>}
          <div className="actions">
            {history && (!current || active) && <Button disabled={pending} onClick={() => {
              setMode('page')
              setPage(current?.currentPage ?? 0)
              setReadDate('')
              open('progress')
            }}><BookOpen size={17} aria-hidden /> {active ? 'Atualizar progresso' :
              history.length ? 'Iniciar releitura' : 'Iniciar leitura'}</Button>}
            {current?.status === 'WANT_TO_READ' && <Button disabled={pending} onClick={() => transition('READING')}>Iniciar leitura</Button>}
            {active && <Button variant="secondary" disabled={pending} onClick={() => transition('PAUSED')}>Pausar</Button>}
            {current?.status === 'PAUSED' && <Button disabled={pending} onClick={() => transition('READING')}>Retomar</Button>}
            {active && <Button variant="secondary" disabled={pending} onClick={() => transition('COMPLETED')}>Concluir</Button>}
            {current && current.status !== 'WANT_TO_READ' && <Button variant="secondary" disabled={pending}
              onClick={() => transition('ABANDONED')}>Abandonar</Button>}
            {current && <Button variant="secondary" disabled={pending} onClick={() => {
              setTargetDate(current.targetEndDate ?? '')
              open('target')
            }}>Meta de término</Button>}
            <Button variant="secondary" onClick={() => open('rating')}><Star size={17} aria-hidden /> Avaliar livro</Button>
          </div>
          {!!history?.length && <ol className="plain-list" aria-label="Histórico de leituras">
            {history.map(item => <li key={item.id}>
              {item.readingNumber}ª leitura · {readingLabels[item.status]} · {item.currentPage}/{item.pageCount} páginas
              {item.startedAt && <> · <time dateTime={item.startedAt}>
                {new Date(item.startedAt).toLocaleDateString('pt-BR')}
              </time></>}
            </li>)}
          </ol>}
          {!history && error && <Button variant="secondary" onClick={() => { setError(''); setRetry(retry + 1) }}>
            Tentar carregar leituras novamente
          </Button>}
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
      dialog === 'target' ? 'Meta de término' : active ? 'Atualizar progresso' : history?.length ? 'Iniciar releitura' : 'Iniciar leitura'} onClose={() => { if (!pending) setDialog(undefined) }}>
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
        {!readDate && <p className="date-label"><CalendarDays size={16} aria-hidden /> Hoje, {date}</p>}
        <label className="field">{active ? 'Data e hora da leitura (opcional)' : 'Data e hora de início (opcional)'}
          <input type="datetime-local" value={readDate} onChange={event => setReadDate(event.target.value)} />
        </label>
        {active && <>
          {pages && <div className="chips" aria-label="Unidade do progresso">
            <button type="button" className="chip" aria-pressed={mode === 'page'}
              onClick={() => { if (mode !== 'page') { setPage(Math.round(page * pages / 100)); setMode('page') } }}>Página</button>
            <button type="button" className="chip" aria-pressed={mode === 'percent'}
              onClick={() => { if (mode !== 'percent') { setPage(percentage(page)); setMode('percent') } }}>%</button>
          </div>}
          <label className="field">{mode === 'page' ? 'Página atual' : 'Porcentagem lida'}
            <input type="number" min="0" max={mode === 'page' ? pages : 100} step="1" required
              value={Number.isNaN(page) ? '' : page} onChange={event => setPage(event.target.valueAsNumber)} />
          </label>
          {pages && <progress aria-label="Progresso previsto" value={page || 0} max={mode === 'page' ? pages : 100} />}
        </>}
        <div className="dialog-actions">
          <button type="button" className="text-button" disabled={pending} onClick={() => setDialog(undefined)}>Cancelar</button>
          <Button type="submit" disabled={pending}>{active ? 'Salvar progresso' : history?.length ? 'Iniciar releitura' : 'Iniciar leitura'}</Button>
        </div>
      </form>}
      {dialog === 'target' && current && <form onSubmit={event => {
        event.preventDefault()
        void run(async () => apply(await client.setReadingTarget(book.id, current.id, targetDate || null)),
          targetDate ? 'Meta salva.' : 'Meta removida.')
      }}>
        <label className="field">Data de término
          <input type="date" value={targetDate} onChange={event => setTargetDate(event.target.value)} />
        </label>
        <div className="dialog-actions">
          <button type="button" className="text-button" disabled={pending} onClick={() => setTargetDate('')}>Limpar data</button>
          <Button type="submit" disabled={pending}>Salvar meta</Button>
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
