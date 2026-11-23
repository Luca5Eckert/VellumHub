import { useCallback, useEffect, useState } from 'react'
import { Check } from 'lucide-react'
import { type api, type Book } from './api'
import { Button } from './Button'
import { Cover, Logo, Retry, ScreenHeading } from './ui'

export function ReadOnboarding({ client, finish }: { client: ReturnType<typeof api>; finish: () => void }) {
  const [books, setBooks] = useState<Book[]>()
  const [selected, setSelected] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    try {
      setBooks(await client.catalog())
    } catch {
      setBooks(undefined)
    } finally {
      setLoading(false)
    }
  }, [client])

  useEffect(() => { void load() }, [load])

  async function submit() {
    setSaving(true)
    setError('')
    const failed: string[] = []
    for (const id of selected) {
      try {
        await client.likeReadBook(id)
      } catch {
        failed.push(id)
      }
    }
    if (!failed.length) finish()
    else {
      setSelected(failed)
      setBooks(current => current?.filter(book => !selected.includes(book.id) || failed.includes(book.id)))
      setError('Algumas escolhas não foram salvas. Tente novamente ou pule esta etapa.')
      setSaving(false)
    }
  }

  return <div className="onboarding">
    <header className="onboarding-header">
      <Logo />
      <button className="text-button" disabled={saving} onClick={finish}>Pular<span className="desktop-only"> por agora</span></button>
    </header>
    <div className="onboarding-card">
      <main className="onboarding-main">
        <div className="step-track complete"><span /></div>
        <p className="step-label">Passo 2 de 2</p>
        <ScreenHeading>Quais você já leu?</ScreenHeading>
        <p className="intro">Marque os livros que você leu e gostou. Assim as primeiras recomendações já chegam certeiras.</p>
        {loading ? <p role="status">Carregando livros…</p>
          : books ? (books.length ? <fieldset disabled={saving}>
            <legend className="sr-only">Livros que você leu e gostou</legend>
            <div className="read-grid">{books.map((book, index) => <label key={book.id} className="read-choice">
              <input type="checkbox" className="sr-only" aria-label={book.title} checked={selected.includes(book.id)}
                onChange={() => setSelected(current => current.includes(book.id)
                  ? current.filter(id => id !== book.id) : [...current, book.id])} />
              <Cover book={book} index={index + 1} />
              {selected.includes(book.id) && <span className="read-check"><Check size={16} aria-hidden /></span>}
              <span className="read-title">{book.title}</span>
            </label>)}</div>
          </fieldset> : <p className="muted">Ainda não há livros disponíveis no catálogo.</p>)
            : <Retry message="O catálogo está indisponível no momento." retry={() => void load()} />}
        {error && <p role="alert" className="error">{error}</p>}
      </main>
      <footer className="onboarding-footer">
        <Button disabled={saving} onClick={() => void submit()}>{saving ? 'Salvando…' : 'Ver minhas recomendações'}</Button>
      </footer>
    </div>
  </div>
}
