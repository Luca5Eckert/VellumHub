import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

test('signed-out view is usable by keyboard on mobile and supports dark theme', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'Sua próxima leitura' })).toBeVisible()
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

test('saves genres once, retries recommendations and submits rating and progress', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('oidc.user:http://127.0.0.1:9000:vellumhub-web', JSON.stringify({
      access_token: 'fixture-token', token_type: 'Bearer',
      profile: { sub: 'fixture-user' }, expires_at: Math.floor(Date.now() / 1000) + 300,
    }))
  })
  const book = { id: 'fixture-book', title: 'Duna', author: 'Frank Herbert',
    description: 'Uma viagem a Arrakis.', pageCount: 400, genres: ['Science Fiction'] }
  let recommendationAttempts = 0
  const writes: Array<{ path: string, body: unknown }> = []
  await page.route('http://127.0.0.1:8080/api/v1/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (request.method() !== 'GET') {
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
      : path.endsWith('/recommendations') ? [book] : book
    await route.fulfill({ json: data })
  })
  await page.goto('/')
  const save = page.getByRole('button', { name: 'Salvar preferências' })
  await expect(save).toBeDisabled()
  for (const name of book.genres.concat('Fantasy', 'Adventure')) {
    await page.getByRole('checkbox', { name }).check()
  }
  await save.click()
  await expect(page.getByRole('status')).toHaveText('Preferências salvas.')
  await expect(page.getByRole('alert')).toHaveText('As recomendações estão indisponíveis no momento.')
  await page.getByRole('button', { name: 'Tentar carregar novamente' }).click()
  await page.getByRole('button', { name: /Duna/ }).click()
  await expect(page.getByRole('heading', { name: 'Duna' })).toBeVisible()
  await page.getByRole('button', { name: 'Enviar avaliação' }).click()
  await expect(page.getByRole('status')).toHaveText('Avaliação enviada.')
  await page.getByLabel('Página atual').fill('42')
  await page.getByRole('button', { name: 'Atualizar' }).click()
  await expect(page.getByRole('status')).toHaveText('Progresso atualizado.')
  expect(writes).toEqual([
    { path: '/api/v1/users/me/preferences', body: { genres: ['Science Fiction', 'Fantasy', 'Adventure'] } },
    { path: '/api/v1/engagement/rating', body: { bookId: book.id, stars: 5 } },
    { path: '/api/v1/catalog/book-progress/fixture-book/progress', body: { newCurrentPage: 42 } },
  ])
})
