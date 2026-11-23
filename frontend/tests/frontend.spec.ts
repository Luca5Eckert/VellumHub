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
    const readings: Array<{ id: string, readingNumber: number, status: string, currentPage: number,
      pageCount: number, targetEndDate: string | null }> = []
    const writes: Array<{ path: string, body: unknown }> = []
    await page.route('http://127.0.0.1:8080/api/v1/**', async route => {
      const request = route.request()
      const path = new URL(request.url()).pathname
      if (request.method() !== 'GET') {
        const body = request.postDataJSON()
        writes.push({ path, body })
        if (path.endsWith('/readings')) {
          const number = readings.length + 1
          readings.unshift({ id: `reading-${number}`, readingNumber: number,
            status: 'READING', currentPage: 0, pageCount: book.pageCount, targetEndDate: null })
          await route.fulfill({ status: 201, json: readings[0] })
          return
        }
        if (path.includes('/readings/')) {
          const current = readings[0]
          if (path.endsWith('/progress')) current.currentPage = body.page ?? body.percentage * book.pageCount / 100
          if (path.endsWith('/status')) {
            current.status = body.status
            if (body.status === 'COMPLETED') current.currentPage = book.pageCount
          }
          if (path.endsWith('/target')) current.targetEndDate = body.targetEndDate
          await route.fulfill({ json: current })
          return
        }
        await route.fulfill({ json: path.endsWith('/preferences') ? body : {} })
        return
      }
      if (path.endsWith('/recommendations') && ++recommendationAttempts === 1) {
        await route.fulfill({ status: 503, json: {} })
        return
      }
      const data = path.endsWith('/genres') ? { genres: ['Science Fiction', 'Fantasy', 'Adventure'] }
        : path.endsWith('/preferences') ? { genres: [] }
        : path.endsWith('/recommendations') ? [book]
          : path.endsWith('/readings') ? readings
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
    await page.getByRole('button', { name: 'Pausar' }).click()
    await expect(page.getByRole('button', { name: 'Retomar' })).toBeVisible()
    await page.getByRole('button', { name: 'Retomar' }).click()
    await page.getByRole('button', { name: 'Meta de término' }).click()
    await page.getByLabel('Data de término').fill('2099-12-31')
    await page.getByRole('button', { name: 'Salvar meta' }).click()
    await expect(page.getByRole('status')).toHaveText('Meta salva.')
    await page.getByRole('button', { name: 'Concluir' }).click()
    await page.getByRole('button', { name: 'Iniciar releitura' }).click()
    await page.getByRole('dialog').getByRole('button', { name: 'Iniciar releitura' }).click()
    await expect(page.getByRole('list', { name: 'Histórico de leituras' }).getByRole('listitem')).toHaveCount(2)
    await page.getByRole('button', { name: 'Abandonar' }).click()
    await expect(page.getByRole('status')).toHaveText('Abandonada.')
    expect(writes).toEqual([
      { path: '/api/v1/users/me/preferences', body: { genres: ['Science Fiction', 'Fantasy', 'Adventure'] } },
      { path: '/api/v1/engagement/reactions', body: { bookId: book.id, typeReaction: 'POSITIVE' } },
      { path: '/api/v1/engagement/rating', body: { bookId: book.id, stars: 4, review: 'Boa leitura.' } },
      { path: '/api/v1/catalog/book-progress/fixture-book/readings', body: {} },
      { path: '/api/v1/catalog/book-progress/fixture-book/readings/reading-1/progress', body: { page: 42 } },
      { path: '/api/v1/catalog/book-progress/fixture-book/readings/reading-1/status', body: { status: 'PAUSED' } },
      { path: '/api/v1/catalog/book-progress/fixture-book/readings/reading-1/status', body: { status: 'READING' } },
      { path: '/api/v1/catalog/book-progress/fixture-book/readings/reading-1/target', body: { targetEndDate: '2099-12-31' } },
      { path: '/api/v1/catalog/book-progress/fixture-book/readings/reading-1/status', body: { status: 'COMPLETED' } },
      { path: '/api/v1/catalog/book-progress/fixture-book/readings', body: {} },
      { path: '/api/v1/catalog/book-progress/fixture-book/readings/reading-2/status', body: { status: 'ABANDONED' } },
    ])
  })
}
