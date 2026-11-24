# CI/CD — VellumHub Java Backend

Os workflows abaixo estão na `main`. Um run verde de PR não é release GHCR, GitOps nem deployment operacional, e aprovação de PR não valida permissões de publicação na `main`.

## Fluxo de entrega — imagem única por execução

| Evento | Ordem obrigatória | Resultado |
| --- | --- | --- |
| PR → main (inclusive alterações de docs) | `ci.yml` → `verify-images.yml` → `kubernetes-m18-full-stack.yml` | Maven/Testcontainers, integração distribuída, seis builds+Trivy, Kind com **os mesmos bytes**, OAuth2/PKCE e 3 jornadas de negócio |
| push na main | `publish-images.yml` → verify → Kind → publish → release-set | Revalida **o commit integrado**, publica imagens SHA-bound somente após Kind e gera manifesto completo 6/6 |
| Benchmark explícito | `backend-metrics.yml` | Evidências M07–M10; separado do gate operacional |

**Trade-off atual:** Kind roda em **toda PR** para evitar falsos negativos na proteção de branch, mesmo em docs-only. Otimizar depois com um check agregador obrigatório e seleção por paths que não permita skips silenciosos.

1. **Build once, scan once:** `verify-images.yml` constrói seis imagens `ghcr.io/luca5eckert/vellumhub-<service>:<commit-sha>`, executa Trivy e gera SBOM CycloneDX. Arquiva a própria imagem Docker comprimida, com hash SHA-256 do arquivo, config digest local e tag/SHA via `tools/ci/image_artifact.py`.
2. **Integrar a imagem efetivamente aprovada:** o workflow reutilizável Kind baixa **os seis arquivos criados e analisados no mesmo workflow run**. Valida arquivo/tag/SHA e Docker config digest antes de `kind load`; sem segundo Docker build. Compila apenas um candidato B adicional de metadados CI para testar atualização/regressão.
3. **Publicar somente após Kind:** `publish-images.yml` depende explicitamente da conclusão de `verify` **e** `kind-functional-gate`. O publish baixa exatamente os mesmos arquivos de imagem, valida integridade e publica SHA-tag no GHCR. O digest **OCI manifest** retornado pelo registry é registrado e atestado; não confundir com o Docker **config digest**.
4. **Conjunto de release:** `tools/ci/release_manifest.py` só gera `vellumhub-release-manifest-<sha>` após os **seis** jobs de publicação bem-sucedidos; rejeita faltas, SHA misto, nomes errados, digest inválido ou SBOM ausente. Nenhuma tag mutável `:main` é atualizada por serviço. GHCR pode receber commits parciais antes de um job falhar, mas eles **não são promovidos** como conjunto aprovado.
5. **CD futuro:** um ambiente real deve consumir somente as referências imutáveis do manifesto aprovado, atualizar o desired state explicitamente e verificar resultados. O workflow não faz deploy em produção nem Argo CD.

No PR, `github.sha` representa o merge ref testado pelo GitHub; no push à main, representa o commit integrado. São fontes diferentes, então uma nova execução é intencional e necessária. Dentro de **cada run**, verifica-se identidade entre scan, Kind e release.

## Qualidade de backend no Kind

O overlay `deploy/kubernetes/overlays/ci` usa issuer e cliente OAuth2/PKCE de testes, sem credenciais de produção. O teste:
- exige **6/6 Java Deployments + 7/7 StatefulSets Ready** e 60/60 health checks;
- executa `verify_kind_business_flows.py` em Job efêmero, com três **classes distintas** de asserções: **(1)** identidade/PKCE e leituras autorizadas de User, Catalog, Engagement e Recommendation; **(2)** criação, leitura e exclusão de lista privada no Catalog persistida no PostgreSQL; **(3)** proibição de criação da lista sem `catalog:personal:write`, incluindo as negativas de JWT e papel ADMIN herdadas do harness;
- efetua A→B do Gateway (config digest diferente, **mesmo JAR Java**), induz quebra da rota User mesmo com Gateway Ready, exige detecção pela jornada funcional e só aprova rollback após restabelecer imagem A e passar novamente o E2E;
- registra fonte SHA, imagens, timings, asserções e limites em `target/metrics/kubernetes-full-stack.json`. `kind_gate_contract.py` exige os marcadores do OAuth original **e** das jornadas adicionais; saída do Job sem tais provas não é sucesso.

**Não comprovado por este Kind:** atualização entre dois bytecodes Java distintos, evento Kafka→projeção de recomendação no cluster, tráfego simultâneo, alta disponibilidade, multi-node, p95 de produção, Google OIDC live ou Argo CD.

## Segurança, teste e diagnóstico

- PRs têm `contents: read`. Só os jobs de publicação na main têm `packages: write`, `id-token: write` e `attestations: write`. A função `actions/attest@v4` gera provenance/SBOM ligada ao digest do GHCR. A execução da main concluiu as duas etapas de atestação em cada uma das seis imagens. [Run de publicação](https://github.com/Luca5Eckert/VellumHub/actions/runs/45299684920)
- A incompatibilidade de Testcontainers 1.20.4 e Docker 29 foi corrigida nos seis POMs para Testcontainers 1.21.4; nenhuma suíte foi desabilitada.
- `python3 -m unittest discover -s tools/ci -p 'test_*.py' -v` cobre rejeição de arquivos adulterados, release-set incompleto, SHA/digest errado e falsos verdes no Job Kubernetes.
- Os rulesets da main retornados em 24/11/2026 exigiam Maven verify, Analyze Java e cinco scans (Gateway, User, Catalog, Engagement e Recommendation). Scan de Auth e Kind rodaram como parte dos workflows e estavam fora da lista de checks obrigatórios retornada pela API. [Regras aplicáveis à main](https://api.github.com/repos/Luca5Eckert/VellumHub/rules/branches/main)
- Há risco de supply chain residual em `@v4`, binário Kind baixado sem checksum e imagens-base em tags mutáveis. Pinning de SHA/digest e atualização automatizada ainda não estão implementados.

## Medições e interpretação

A referência anterior, o protocolo com n mínimo e os resultados executados estão no [plano de CI/CD do Obsidian Vault](https://github.com/Luca5Eckert/obsidian-vault/pull/21). **Não inferir ganho de velocidade ou redução de erros da presença de novas etapas:** somente runs completos comparáveis provam o depois. A publicação, as atestações e o manifesto com seis imagens foram verificados no run de publicação da `main` para o SHA `a9a6398373ecbfd5c7688613ad74dc548f74b2a9`. [Run de publicação](https://github.com/Luca5Eckert/VellumHub/actions/runs/45299684920)
