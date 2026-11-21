import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: 'tests',
  use: {
    baseURL: 'http://127.0.0.1:9100',
    launchOptions: { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE || undefined },
  },
  webServer: [
    { command: 'npm run dev', url: 'http://127.0.0.1:9100' },
    { command: 'npm run storybook -- --ci --host 127.0.0.1', url: 'http://127.0.0.1:6006' },
  ],
})
