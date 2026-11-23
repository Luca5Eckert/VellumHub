import { readFile, writeFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import StyleDictionary from 'style-dictionary'

const source = fileURLToPath(new URL('../tokens.json', import.meta.url))
const output = new URL('../src/tokens.generated.css', import.meta.url)

const dictionary = new StyleDictionary({
  source: [source],
  usesDtcg: true,
  platforms: { css: { transformGroup: 'css', files: [{ destination: 'tokens.css', format: 'theme' }] } },
  hooks: {
    formats: {
      theme: ({ dictionary }) => {
        const themes = [
          ['light', ':root'],
          ['dark', ':root[data-theme="dark"]'],
        ]
        return '/* Generated from tokens.json. */\n' + themes.map(([theme, selector]) => {
          const declarations = dictionary.allTokens.filter(token => token.path[0] === theme)
            .map(token => `  --${token.path.slice(1).join('-')}: ${token.$value};`).join('\n')
          return `${selector} {\n  color-scheme: ${theme};\n${declarations}\n}\n`
        }).join('\n')
      },
    },
  },
})
const [file] = await dictionary.formatPlatform('css')
if (process.argv.includes('--check')) {
  if (await readFile(output, 'utf8') !== file.output) throw new Error('Run npm run tokens to refresh the CSS')
} else {
  await writeFile(output, file.output)
}
