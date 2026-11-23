import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

test('signed-out view is usable by keyboard on mobile and supports dark theme', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeVisible()
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: 'Tema escuro' })).toBeFocused()
  await page.keyboard.press('Space')
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark')
  await page.keyboard.press('Tab')
  await expect(page.getByRole('button', { name: 'Entrar' })).toBeFocused()
  expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([])
})

test('Storybook Button supports keyboard activation and passes accessibility audit', async ({ page }) => {
  await page.goto('http://127.0.0.1:6006/iframe.html?id=button--primary&viewMode=story')
  const button = page.getByRole('button', { name: 'Salvar preferências' })
  await expect(button).toBeVisible()
  await button.evaluate(element => element.onclick = () => element.setAttribute('data-activated', 'true'))
  await button.focus()
  await page.keyboard.press('Enter')
  await expect(button).toHaveAttribute('data-activated', 'true')
  expect((await new AxeBuilder({ page }).include('#storybook-root').analyze()).violations).toEqual([])
})

for (const { viewport, colorScheme } of [
  { viewport: { width: 390, height: 844 }, colorScheme: 'light' },
  { viewport: { width: 1440, height: 900 }, colorScheme: 'dark' },
] as const) {
  test(`saves onboarding, rates a book and updates an active reading at ${viewport.width}px in ${colorScheme} mode`, async ({ page }) => {
    await page.setViewportSize(viewport)
    await page.emulateMedia({ colorScheme })
    await page.addInitScript(() => {
      sessionStorage.setItem('oidc.user:http://127.0.0.1:9000:vellumhub-web', JSON.stringify({
        access_token: 'fixture-token', token_type: 'Bearer',
        profile: { sub: 'fixture-user' }, expires_at: Math.floor(Date.now() / 1000) + 300,
      }))
    })
    const book = { id: 'fixture-book', title: 'Duna', author: 'Frank Herbert',
      description: 'Uma viagem a Arrakis.', pageCount: 400, genres: ['Science Fiction'] }
    let recommendationAttempts = 0
    let readingStarted = false
    const writes: Array<{ path: string, body: unknown }> = []
    await page.route('http://127.0.0.1:8080/api/v1/**', async route => {
      const request = route.request()
      const path = new URL(request.url()).pathname
      if (request.method() !== 'GET') {
        if (path.endsWith('/status')) readingStarted = true
        if (path.endsWith('/progress') && !readingStarted) {
          await route.fulfill({ status: 404 })
          return
        }
        writes.push({ path, body: request.postDataJSON() })
        await route.fulfill({ json: path.endsWith('/preferences') ? request.postDataJSON() : {} })
        return
      }
      if (path.endsWith('/recommendations') && ++recommendationAttempts === 1) {
        await route.fulfill({ status: 503, json: {} })
        return
      }
      const data = path.endsWith('/genres') ? { genres: ['Science Fiction', 'Fantasy', 'Adventure'] }
        : path.endsWith('/preferences') ? { genres: [] }
        : path.endsWith('/recommendations') ? [book]
          : path.endsWith('/reading-list') ? []
            : path.endsWith('/books') ? [book] : book
      await route.fulfill({ json: data })
    })
    await page.goto('/')
    const save = page.getByRole('button', { name: 'Continuar' })
    await expect(save).toBeDisabled()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    for (const name of ['Ficção científica', 'Fantasia', 'Aventura']) {
      await page.getByRole('checkbox', { name }).press('Space')
    }
    await save.click()
    await expect(page.getByRole('heading', { name: 'Quais você já leu?' })).toBeFocused()
    await page.getByRole('checkbox', { name: 'Duna' }).press('Space')
    await page.getByRole('button', { name: 'Ver minhas recomendações' }).click()
    await expect(page.getByText('Preferências salvas.')).toBeVisible()
    await expect(page.getByRole('alert')).toHaveText('As recomendações estão indisponíveis no momento.')
    await page.getByRole('button', { name: 'Tentar carregar novamente' }).click()
    await page.getByRole('button', { name: 'Por que este livro?' }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    expect((await new AxeBuilder({ page }).include('dialog').analyze()).violations).toEqual([])
    await expect(page.getByText('Gêneros em comum')).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).toHaveCount(0)
    await page.getByRole('button', { name: 'Ver livro' }).click()
    await expect(page.getByRole('heading', { name: 'Duna' })).toBeVisible()
    await page.getByRole('button', { name: 'Avaliar livro' }).click()
    await page.getByRole('radio', { name: '5 de 5 · Adorei' }).press('ArrowLeft')
    await page.getByLabel('Resenha (opcional)').fill('Boa leitura.')
    await page.getByRole('button', { name: 'Salvar avaliação' }).click()
    await expect(page.getByRole('status')).toHaveText('Avaliação enviada.')
    await expect(page.getByRole('button', { name: 'Atualizar progresso' })).toHaveCount(0)
    await page.getByRole('button', { name: 'Iniciar leitura' }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Iniciar leitura' }).click()
    await expect(page.getByRole('status')).toHaveText('Leitura iniciada.')
    await page.getByRole('button', { name: 'Atualizar progresso' }).click()
    await page.getByLabel('Página atual').fill('42')
    await page.getByRole('button', { name: 'Salvar progresso' }).click()
    await expect(page.getByRole('status')).toHaveText('Progresso atualizado.')
    expect(writes).toEqual([
      { path: '/api/v1/users/me/preferences', body: { genres: ['Science Fiction', 'Fantasy', 'Adventure'] } },
      { path: '/api/v1/engagement/reactions', body: { bookId: book.id, typeReaction: 'POSITIVE' } },
      { path: '/api/v1/engagement/rating', body: { bookId: book.id, stars: 4, review: 'Boa leitura.' } },
      { path: '/api/v1/catalog/book-progress/fixture-book/status',
        body: { status: 'READING', currentPage: 0, startedAt: expect.any(String) } },
      { path: '/api/v1/catalog/book-progress/fixture-book/progress', body: { newCurrentPage: 42 } },
    ])
  })
}
