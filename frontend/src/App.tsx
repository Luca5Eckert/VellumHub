import { useEffect, useState } from 'react'
import type { User } from 'oidc-client-ts'
import { Button } from './Button'
import { Library } from './Library'
import { session } from './session'
import { Logo, ThemeButton } from './ui'

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

  const toggleTheme = () => setDark(value => !value)
  if (user === undefined) return <main className="standalone"><p role="status">Verificando sessão…</p></main>

  if (user) return <>
    {error && <p role="alert" className="global-error">{error}</p>}
    <Library key={user.profile.sub} token={user.access_token} userId={user.profile.sub} name={user.profile.name} dark={dark} toggleTheme={toggleTheme}
      signOut={() => { void session.signoutRedirect().catch(() => setError('Não foi possível sair. Tente novamente.')) }} />
  </>

  return <div className="entry">
    <aside className="entry-brand">
      <Logo />
      <div><h2>Cada leitura, com um motivo.</h2>
        <p>Registre o que você lê, veja seu ritmo e receba recomendações que explicam por que combinam com você.</p></div>
      <div className="entry-covers" aria-hidden="true">
        <span className="cover cover-placeholder" data-tone="1" />
        <span className="cover cover-placeholder" data-tone="2" />
        <span className="cover cover-placeholder" data-tone="3" />
      </div>
    </aside>
    <main className="entry-main">
      <div className="entry-theme"><ThemeButton dark={dark} toggle={toggleTheme} /></div>
      <div className="entry-form">
        <div className="entry-mobile-logo"><Logo /></div>
        <h1>Entrar</h1>
        <p className="muted">Acompanhe suas leituras e receba recomendações com o motivo de cada uma.</p>
        {error && <p role="alert" className="error">{error}</p>}
        <Button onClick={() => { void session.signinRedirect().catch(() => setError('Não foi possível abrir o serviço de login.')) }}>Entrar</Button>
      </div>
    </main>
  </div>
}
