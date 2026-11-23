import { Check } from 'lucide-react'
import { Button } from './Button'
import { genreLabel, Logo, ScreenHeading } from './ui'

interface Props {
  genres: string[]
  selected: string[]
  pending: boolean
  error: string
  editing: boolean
  onChange: (genre: string) => void
  onSave: () => void
  onSkip: () => void
}

export function Onboarding({ genres, selected, pending, error, editing, onChange, onSave, onSkip }: Props) {
  return <div className="onboarding">
    <header className="onboarding-header">
      <Logo />
      <button className="text-button" disabled={pending} onClick={onSkip}>{editing ? 'Cancelar' : <><span>Pular</span><span className="desktop-only"> por agora</span></>}</button>
    </header>
    <div className="onboarding-card">
    <main className="onboarding-main">
      <form id="genre-form" onSubmit={event => { event.preventDefault(); onSave() }}>
        {!editing && <>
          <div className="step-track"><span /></div>
          <p className="step-label">Passo 1 de 2</p>
        </>}
        <ScreenHeading>Do que você gosta de ler?</ScreenHeading>
        <p className="intro">Escolha pelo menos 3 gêneros para suas primeiras recomendações. Você pode mudar isso depois no perfil.</p>
        <fieldset disabled={pending}>
          <legend className="sr-only">Gêneros favoritos</legend>
          <div className="chips">{genres.map(genre => <label className="chip selectable" key={genre}>
            <input className="sr-only" type="checkbox" checked={selected.includes(genre)} onChange={() => onChange(genre)} />
            {selected.includes(genre) && <Check size={16} aria-hidden />}
            {genreLabel(genre)}
          </label>)}</div>
        </fieldset>
        <p className="selection-count" aria-live="polite">{selected.length} {selected.length === 1 ? 'gênero escolhido' : 'gêneros escolhidos'}</p>
        {error && <p role="alert" className="error">{error}</p>}
      </form>
    </main>
    <footer className="onboarding-footer">
      <Button type="submit" form="genre-form" disabled={pending || selected.length < 3}>
        {pending ? 'Salvando…' : editing ? 'Salvar preferências' : 'Continuar'}
      </Button>
    </footer>
    </div>
  </div>
}
