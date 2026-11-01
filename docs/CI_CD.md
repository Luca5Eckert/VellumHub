# VellumHub — CI/CD de serviços Java

**Estado:** implementação proposta na [PR #344](https://github.com/Luca5Eckert/VellumHub/pull/344). Código na branch não equivale a release publicada nem deploy operacional.

## Gates e confiança

| Evento | Workflow | Prova requerida |
| --- | --- | --- |
| PR → main | `.github/workflows/ci.yml` e `verify-images.yml` | Testes Maven 21/Testcontainers, E2E distribuído, build+Trivy das 6 imagens, treinamento |
| PR com Java/POM/bibliotecas/manifests | `kubernetes-m18-full-stack.yml` | Kind descartável com 6 serviços Java e 7 dependências, smoke HTTP real e rollback |
| push na main | `publish-images.yml` | Verificação do commit integrado, imagem Docker escaneada, artefato imutável, SBOM, attestation e digest GHCR |
| Sob demanda | `backend-metrics.yml` | M07–M10; não é gate de disponibilidade em produção |

O fluxo de pull request opera com `contents: read` e sem secrets de publicação. Somente o job de release na main recebe `packages: write` e permissões de atestação.

## Build once / publish many

1. Build uma única vez `ghcr.io/luca5eckert/vellumhub-<service>:<40-char-sha>` e execute Trivy contra a **mesma imagem**.
2. Na main, gere CycloneDX, exporte `docker save` e registre no manifesto a revisão Git, tag, digest **config** local, SHA-256 e tamanho do arquivo. O script `tools/ci/image_artifact.py` rejeita SHA, tag ou arquivo alterados.
3. O publish baixa os mesmos bytes de um artefato do **mesmo run**, valida o hash, carrega a imagem, confere o config digest e publica **sem reconstrução**.
4. Registre o **manifest digest do registry** e associe attestations de provenance e SBOM à referência `image@sha256:...`. Digest de config Docker **não** é igual a digest de manifesto OCI.
5. Não atualize a tag mutável `main` a cada push de serviço: a matriz não é uma transação atômica entre seis imagens. CD deve usar digests explícitos no desired state.

**Limitação:** não há push GHCR na PR; a publicação, permissões e atestados precisam de validação por um run da main antes de receber status VERIFIED.

## Kubernetes e negócio

O teste CI usa `deploy/kubernetes/overlays/ci`, derivado do overlay local, com issuer interno e cliente OAuth2 PKCE habilitados **somente em Kind descartável**. Executa as seis imagens Java reais, cinco PostgreSQL, Kafka e Redis; não usa stubs de aplicações.

- Ready: 6/6 Deployments, 7/7 StatefulSets, 60/60 probes HTTP de liveness/readiness.
- Negócio: Job Python com a jornada existente `infra/scripts/security/verify_oidc_lote2_flow.py`; Auth registra identidade UUIDv7, PKCE S256 emite JWT, Gateway consulta User, Catalog, Engagement e Recommendation, verificando 401/403 para escopos negados, ID token e token inválido.
- A/B: crie imagem Gateway candidata de digest distinto, com o mesmo JAR (metadados CI), faça rollout B e repita negócio. Injete `USER_SERVICE_URL` inválido (regressão de configuração **após** Ready), exija que a jornada autenticada detecte a quebra; restaure exatamente o template aprovado A e só aceite rollback após novo E2E positivo.
- Diagnósticos: `target/metrics/kubernetes-full-stack.json` com SHA, ambiente, tempos por fase, resultado funcional, negações e limites. Falhas devem impedir sucesso do workflow.

**Limites:** 1 nó Kind, 1 réplica Java por serviço, sem usuário real, tráfego concorrente, SLO, Argo CD operacional, Google OIDC live, disaster recovery ou teste de versão nova de código Java. O A/B CI tem JAR igual e regressão de configuração, não é benchmark de release de funcionalidade.

## Compatibilidade e segurança

A PR atualiza os seis módulos de Testcontainers 1.20.4 → 1.21.4: a versão antiga negociava Docker API 1.32, abaixo do mínimo 1.40 do Docker 29 no runner, interrompendo testes antes de iniciar containers. Nunca contornar isso pulando suites.

Regras de proteção de branch são configuração no GitHub (não neste YAML): exigir os checks estáveis `verify / Maven verify`, `verify / Distributed integration + cross-service E2E`, seis scans Docker e CodeQL. Não exigir globalmente um workflow condicional de Kind enquanto docs-only PRs puderem omiti-lo. Validar nomes reais no GitHub antes de configurar.

## Reproduzir

```bash
python3 -m unittest discover -s tools/ci -p 'test_*.py' -v
python3 -m py_compile deploy/kubernetes/scripts/benchmark-full-stack.py
mvn -B -ntp -DexcludedGroups=distributed clean verify
mvn -B -ntp -Pdistributed-e2e -pl integration-tests/distributed-e2e -am -Dgroups=distributed test
kubectl kustomize deploy/kubernetes/overlays/ci
```

O plano de evolução e a comparação auditável com o **baseline real de 31/10/2026** estão no [Obsidian Vault](https://github.com/Luca5Eckert/obsidian-vault/blob/main/projects/vellum-hub/plan/CI-CD%20-%20Plano%20de%20robustez%20e%20comparativo%202026-10-31.md). Não deduzir ganho de tempo, p95 ou ausência de flakiness de um único run ou de workloads diferentes.
