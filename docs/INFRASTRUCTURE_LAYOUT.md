# Infraestrutura local e artefatos de build

## Princípios

O VellumHub tem seis artefatos Java independentes: Auth, Gateway, User,
Catalog, Engagement e Recommendation. A definição de build é compartilhada em
`infra/docker/java/Dockerfile`, mas cada serviço mantém imagem, dependências
Maven e implantação próprias. A matriz de `.github/workflows/verify-images.yml`
é o contrato para as imagens examinadas por Trivy e entregues ao Kind; o
workflow de publicação **reusa os mesmos bytes** (sem rebuild).

| Serviço | Estágio de dependências | Runtime | Healthcheck |
| --- | --- | --- | --- |
| Auth | deps-jwt | Alpine JRE 21 | porta de gerenciamento 9001 |
| Gateway | deps-jwt | Alpine JRE 21 | 8080 |
| User | deps-kafka | Alpine JRE 21 | 8080 |
| Catalog | deps-avro | Alpine JRE 21 | 8080 |
| Engagement | deps-avro | Alpine JRE 21 | 8080 |
| Recommendation | deps-avro | Debian JRE 21 + libstdc++6 | 8080 |

O Auth continua com API na porta 9000. O agente OpenTelemetry é distribuído
com as seis imagens, mas não é ativado por padrão no Auth. O usuário runtime
é não-root (UID 10001). Tests e checks são executados fora dos builds de imagem
na CI; `-Dmaven.test.skip=true` no Dockerfile não substitui a validação Maven.

## Execução local: contratos entre arquivos Compose

`compose.yaml` é o único Compose na raiz e continua sendo detectado por
`docker compose up -d`. Os outros arquivos vivem em `infra/compose/`.
Para overlays, especifique **primeiro** `-f compose.yaml`, depois `-f infra/compose/...`:
o Compose resolve caminhos relativos de bind mounts e build a partir do primeiro
arquivo, preservando `./.secrets`, `./.local` e `./infra` da raiz.

Para os ambientes **independentes** (Schema Registry e Auth PoC), execute da
raiz do repositório com `--project-directory .` para preservar a resolução
explícita de caminhos e o contexto do Dockerfile:

```bash
# Independente: não compartilha o projeto da stack principal.
REGISTRY_DB_PASSWORD='substitua-por-um-valor-local' docker compose --project-directory . -f infra/compose/schema-registry.yaml config --quiet

# Auth PoC: requer as variáveis de ambiente/PEMs do runbook antes de iniciar.
docker compose --project-directory . -f infra/compose/tests/auth-poc.yaml --profile auth-poc config --quiet
```


- `compose.yaml`: topologia principal; apps, bancos, Kafka, Redis e
  observabilidade opt-in
- `infra/compose/kafka-secure.yaml`: overlay opcional de SASL/ACL local
- `infra/compose/oidc-local.yaml`: harness local de PKCE e provisionamento
- `infra/compose/google-federation.yaml`: extensão opt-in de federação Google
- `infra/compose/tests/auth-poc.yaml`: fixture isolada para Auth e seu banco
- `infra/compose/schema-registry.yaml`: Registry SQL opt-in em projeto
  Compose e rede independentes (consulte `SCHEMA_REGISTRY_OPERATIONS.md`)

A resolução de serviços deve usar nomes Compose, não IDs ou nomes fixos de
containers. O Alloy descobre logs pelo label `com.docker.compose.service`.
Isso permite criar projetos locais com `-p` sem conflitar `container_name`.

Os Dockerfiles PostgreSQL locais foram removidos. Catalog, User e Engagement
usam `postgres:15-alpine`; Auth e Registry usam `postgres:16-alpine`;
Recommendation usa `pgvector/pgvector:pg15`. Schemas e extensões são
geridos por migrations Flyway. Volumes nomeados continuam separados.

## Kubernetes

- `deploy/kubernetes/base/` mantém contratos de aplicação;
- `overlays/local/infrastructure.yaml` mantém Redis e Kafka;
- `overlays/local/databases.yaml` reúne os cinco PostgreSQL locais;
- `overlays/prod/` **não** implanta esses bancos locais.

A duplicação de configuração Compose/Kubernetes é uma fronteira intencional
entre runtimes diferentes. Diferença explícita: Compose usa Confluent Kafka
7.5 com ZooKeeper para o harness de ACL; Kind usa Apache Kafka 3.9 em KRaft.
A migração para um broker único exige comprovar novamente o contrato de ACL
SEC-14, o comportamento de replay e o Kind E2E; não está incluída aqui.
Redis é fixado em 7.4-alpine nos dois ambientes locais.

## Verificação rápida

```bash
./mvnw -B -ntp -DexcludedGroups=distributed clean verify
python3 -m unittest discover -s tools/ci -p 'test_*.py' -v
docker compose config --quiet
kubectl kustomize deploy/kubernetes/overlays/local
```

O root Maven Wrapper utiliza Maven 3.9.11, diferente do Maven 3.9.9
do Dockerfile. Antes de padronizar as versões, executar verificação cruzada
para evitar divergência de build. Este documento não certifica rollout
em produção.
