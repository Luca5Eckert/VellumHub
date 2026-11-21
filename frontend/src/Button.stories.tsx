import type { Meta, StoryObj } from '@storybook/react-vite'
import { Button } from './Button'

const meta = {
  component: Button,
  parameters: { a11y: { test: 'error' } },
} satisfies Meta<typeof Button>

export default meta
type Story = StoryObj<typeof meta>
export const Primary: Story = { args: { children: 'Salvar preferências' } }
export const Secondary: Story = { args: { children: 'Voltar', variant: 'secondary' } }
export const Disabled: Story = { args: { children: 'Salvando…', disabled: true } }
