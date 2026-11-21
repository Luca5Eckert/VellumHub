import type { StorybookConfig } from '@storybook/react-vite'

export default {
  stories: ['../src/**/*.stories.tsx'],
  addons: ['@storybook/addon-a11y'],
  framework: '@storybook/react-vite',
} satisfies StorybookConfig
