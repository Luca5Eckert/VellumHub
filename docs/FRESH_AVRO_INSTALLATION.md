# Instalação nova: Kafka Avro + Apicurio Registry (sem cutover legado)

Use este procedimento para ambientes **sem dados legados**: ele seleciona as rotas Avro já na primeira inicialização. Instalações com dados JSON existentes seguem os runbooks de cutover ([estados de wire format](architecture/states/event-wire-format.md)).

## O que é ativado

| Emissor | Rota | Consumidores |
| --- | --- | --- |
| Catalog | Book Changed: `book-events.avro` | Engagement e Recommendation |
| Catalog | Reading Progress: `reading-progress-events.avro` | Engagement e Recommendation |
| Engagement | Ratings: `engagement.ratings.v1.avro` | Recommendation |
| Engagement | Reactions: `engagement.reactions.v1.avro` | Recommendation |

Os oito subjects de `infra/schema-registry/contracts.json` são registrados antes da criação de fatos. Usa-se Confluent-compatible Avro, `TopicRecordNameStrategy`, `BACKWARD_TRANSITIVE` e `auto.register.schemas=false`. O Registry 3.1.7 persiste metadados/IDs em PostgreSQL dedicado; Kafka e dados dos aplicativos continuam em volumes separados.

**Não desabilite globalmente os listeners JSON:** `KAFKA_LEGACY_CONSUMERS_ENABLED=true` permanece propositalmente enquanto existirem rotas legadas independentes (por exemplo, preferências). Os quatro publishers acima são exclusivamente Avro nesta configuração e não duplicam eventos nesses tópicos JSON. Não é uma conversão de dados preexistentes.

## Compose local: caminho de instalação nova

Requisitos: Docker Compose v2, Python 3.11+, `.env` com senhas distintas e fortes, chaves de assinatura RSA Auth na pasta `VELLUM_AUTH_KEY_DIR` (consulte `.env.example` e os runbooks de autenticação). Adicione `REGISTRY_DB_PASSWORD` ao `.env` e mantenha esse arquivo fora do Git. Não use valores `change_me` ou `replace-`.

Na raiz do repositório:

```bash
cp .env.example .env
# Edite .env, gere suas chaves Auth e sete REGISTRY_DB_PASSWORD.
python3 tools/schema_registry/bootstrap_fresh.py --check
python3 tools/schema_registry/bootstrap_fresh.py --new-installation
```

O bootstrap executa, em ordem:
1. Valida a interpolação de **ambos** os arquivos Compose, flags de writers/listeners, manifesto e configuração Auth; recusa volumes/containers preexistentes nos projetos.
2. Inicia apenas Kafka/Zookeeper da stack principal (cria a rede de apps).
3. Inicia Registry e PostgreSQL em **outro** projeto Compose, com rede compartilhada declarada por `infra/compose/schema-registry-app-network.yaml`; continua independente para `down` e preserva a vinculação após recreação.
4. Aguarda a API e provisiona explicitamente os quatro tópicos Avro no Kafka local (3 partições, fator de replicação 1); publica schemas aprovados somente no Registry local e verifica policies, versões e IDs.
5. Somente após isso inicia os aplicativos com `infra/compose/avro-fresh.yaml`, aguarda healthchecks e verifica os IDs mais uma vez.

Em caso de interrupção de uma **instalação comprovadamente nova**, corrija o erro e use `--resume`; esta opção não confere que os volumes são novos. Nunca use `--resume` em uma instalação existente para 'ativar Avro'. Não execute `down -v` se desejar preservar Kafka, Outbox ou schemas.

Para verificar posteriormente sem alterar o Registry: `python3 tools/schema_registry/registry_gate.py verify` (contra 127.0.0.1:18081). Use `docker compose --project-directory . -f compose.yaml -f infra/compose/avro-fresh.yaml ps` para status do aplicativo e `docker compose --project-directory . -f infra/compose/schema-registry.yaml -f infra/compose/schema-registry-app-network.yaml ps` para o Registry. O Registry local fica exposto apenas no loopback; **não use o overlay sem OIDC como serviço público**.

## Kubernetes / Kind novo

O overlay `deploy/kubernetes/overlays/local-avro-fresh` inclui a infraestrutura local existente e os mesmos flags de Avro, mas **não** incorpora o Registry: este deve ser instalado e ter os contratos registrados separadamente, antes dos aplicativos. **Não aplique diretamente sobre um cluster com bancos ou tópicos JSON já populados.** O bootstrap separa registro de contratos e início dos aplicativos:

```bash
# Pré-requisito: namespace, os secrets existentes da stack e imagens carregadas/publicadas.
kubectl apply -f deploy/kubernetes/base/namespace.yaml
# Provisionar schema-registry-db Secret (username,password,url), conforme
# docs/SCHEMA_REGISTRY_OPERATIONS.md. Não versionar segredos.
kubectl apply -k deploy/kubernetes/schema-registry/overlays/local
kubectl -n vellumhub rollout status statefulset/registry-postgres --timeout=8m
kubectl -n vellumhub rollout status deployment/schema-registry --timeout=8m
kubectl -n vellumhub port-forward svc/schema-registry 18081:8080
# Em outro terminal, com port-forward ativo:
python3 tools/schema_registry/registry_gate.py publish --ephemeral
python3 tools/schema_registry/registry_gate.py verify
# Somente então:
kubectl apply -k deploy/kubernetes/overlays/local-avro-fresh
kubectl -n vellumhub rollout status deployment/catalog-service --timeout=10m
kubectl -n vellumhub rollout status deployment/engagement-service --timeout=10m
kubectl -n vellumhub rollout status deployment/recommendation-service --timeout=10m
```

Os arquivos Kustomize antigos `local` e `prod` **não foram alterados**. Em AWS/staging/produção, primeiro é necessário provisionar os quatro tópicos Avro com partições, retenção e fator de replicação compatíveis com o cluster, escolher endpoints de Kafka/DB, provisionar Registry persistente com OIDC/roles e TLS, garantir autorização de **leitura de schema** para produtores/consumidores, registrar schemas por workflow aprovado e criar overlay ambiental protegido. Os clientes Avro atuais usam URL sem suporte explícito a bearer token OIDC; **não declare que o overlay de produção com OIDC já funciona** nem use esta configuração local insegura em produção. Esse requisito não autoriza credenciais no Git.

## Evidência e limites

- Testes distribuídos Kafka + SQL + Registry dos streams: `.github/workflows/canonical-streams-avro-acceptance.yml`.
- A nova CI valida flags de Compose/Kustomize, a compatibilidade da rede e o Registry persistente; **um render estático não equivale a E2E da stack inteira**.
- `bootstrap_fresh.py` aguarda healthchecks, mas esses checks não substituem testes de jornada real (criar livro, ler, avaliar, reagir, reconciliar projeções). Não há evidência de deploy AWS ou cutover de ambiente antigo neste procedimento.
