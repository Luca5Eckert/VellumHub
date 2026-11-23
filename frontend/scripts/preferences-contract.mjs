import { readFile, writeFile } from 'node:fs/promises'
import openapiTS, { astToString } from 'openapi-typescript'

const contractFile = new URL('../contracts/preferences.openapi.json', import.meta.url)
const typesFile = new URL('../src/preferences.generated.ts', import.meta.url)
const [mode, source] = process.argv.slice(2)
const check = mode === '--check'

async function writeOrCheck(file, value) {
  if (!check) {
    await writeFile(file, value)
  } else if (await readFile(file, 'utf8') !== value) {
    throw new Error(`Contract drift in ${file.pathname}. Regenerate from the live Gateway.`)
  }
}

function canonical(value) {
  if (Array.isArray(value)) return value.map(canonical)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.keys(value).sort().map(key => [key, canonical(value[key])]))
  }
  return value
}

function extract(spec) {
  const paths = {}
  for (const path of ['/users/genres', '/users/me/preferences']) {
    if (!spec.paths?.[path]) throw new Error(`Missing OpenAPI path: ${path}`)
    paths[`/api/v1${path}`] = spec.paths[path]
  }
  const schemas = {}
  function collect(value) {
    if (!value || typeof value !== 'object') return
    const prefix = '#/components/schemas/'
    if (typeof value.$ref === 'string' && value.$ref.startsWith(prefix)) {
      const name = value.$ref.slice(prefix.length)
      if (!Object.hasOwn(schemas, name)) {
        const schema = spec.components?.schemas?.[name]
        if (!schema) throw new Error(`Unresolved OpenAPI schema: ${name}`)
        schemas[name] = schema
        collect(schema)
      }
    }
    Object.values(value).forEach(collect)
  }
  collect(paths)
  const oauth2 = structuredClone(spec.components.securitySchemes.oauth2)
  // Auth's origin is configured per environment, while the OAuth paths and scopes are contract data.
  const flow = oauth2.flows.authorizationCode
  flow.authorizationUrl = new URL(flow.authorizationUrl).pathname
  flow.tokenUrl = new URL(flow.tokenUrl).pathname
  return canonical({
    openapi: spec.openapi,
    info: { title: 'VellumHub Gateway — User Preferences', version: spec.info.version },
    paths,
    components: { schemas, securitySchemes: { oauth2 } },
  })
}

if (!['--check', '--write'].includes(mode)) throw new Error('Use --check or --write.')
let spec
if (source) {
  if (/^https?:\/\//.test(source)) {
    const response = await fetch(source, { signal: AbortSignal.timeout(15_000) })
    if (!response.ok) throw new Error(`OpenAPI returned HTTP ${response.status}`)
    spec = await response.json()
  } else {
    spec = JSON.parse(await readFile(source, 'utf8'))
  }
  spec = extract(spec)
  await writeOrCheck(contractFile, JSON.stringify(spec, null, 2) + '\n')
} else {
  spec = JSON.parse(await readFile(contractFile, 'utf8'))
}
await writeOrCheck(typesFile, astToString(await openapiTS(spec)))
