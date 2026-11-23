# Instalação nova: Kafka Avro + Apicurio Registry (sem cutover legado)

Use este procedimento para ambientes **sem dados legados**: ele seleciona as rotas Avro já na primeira inicialização. Instalações com dados JSON existentes seguem os runbooks de cutover ([estados de wire format](architecture/states/event-wire-format.md)).

## O que é ativado

| Emissor | Rota | Consumidores |
| --- | --- | --- |
| Catalog | Book Changed: `book-events.avro` | Engagement e Recommendation |
| Catalog | Reading Progress: `reading-progress-events.avro` | Engagement e Recommendation |
| Engagement | Ratings: `engagement.ratings.v1.avro` | Recommendation |
| Engagement | Reactions: `engagement.reactions.v1.avro` | Recommendation |
| User | Preferences: `user-preference-events.avro` | Recommendation |

Os subjects de `infra/schema-registry/contracts.json` são registrados antes da criação de fatos. Usa-se Confluent-compatible Avro, `TopicRecordNameStrategy`, `BACKWARD_TRANSITIVE` e `auto.register.schemas=false`. O Registry 3.1.7 persiste metadados/IDs em PostgreSQL dedicado; Kafka e dados dos aplicativos continuam em volumes separados.

Nesta instalação nova, as cinco rotas publicam e consomem Avro. O overlay desativa os listeners JSON canônicos e de compatibilidade em Engagement e Recommendation. O Compose padrão mantém esses listeners para instalações existentes, que devem seguir o procedimento de migração de cada stream.

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
4. Aguarda a API e provisiona explicitamente os cinco tópicos Avro no Kafka local (3 partições, fator de replicação 1); publica schemas aprovados somente no Registry local e verifica policies, versões e IDs.
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

Os arquivos Kustomize antigos `local` e `prod` **não foram alterados**. Em AWS/staging/produção, primeiro é necessário provisionar os cinco tópicos Avro com partições, retenção e fator de replicação compatíveis com o cluster, escolher endpoints de Kafka/DB, provisionar Registry persistente com OIDC/roles e TLS, garantir autorização de **leitura de schema** para produtores/consumidores, registrar schemas por workflow aprovado e criar overlay ambiental protegido. Os clientes Avro atuais usam URL sem suporte explícito a bearer token OIDC; **não declare que o overlay de produção com OIDC já funciona** nem use esta configuração local insegura em produção. Esse requisito não autoriza credenciais no Git.

## Evidência e limites

- Testes distribuídos Kafka + SQL + Registry dos streams: `.github/workflows/canonical-streams-avro-acceptance.yml`.
- A nova CI valida flags de Compose/Kustomize, a compatibilidade da rede e o Registry persistente; **um render estático não equivale a E2E da stack inteira**.
- `bootstrap_fresh.py` aguarda healthchecks, mas esses checks não substituem testes de jornada real (criar livro, ler, avaliar, reagir, reconciliar projeções). Não há evidência de deploy AWS ou cutover de ambiente antigo neste procedimento.

## Preferências no Kafka

O bootstrap registra `user-preference-events.avro` e ativa o publisher de User
e o consumidor ordenado de Recommendation. Cada usuário começa na revisão
Avro zero. A revisão Avro gravada no outbox determina o formato de cada
envio, inclusive nas tentativas posteriores.

Em uma instalação existente, interrompa temporariamente as escritas de
preferências e aguarde o outbox JSON e o lag do grupo
`recommendation_service_group` chegarem a zero. Provisione o tópico, o subject
e as ACLs. Ative `USER_PREFERENCE_AVRO_CONSUMER_ENABLED=true` e
`USER_PREFERENCE_JSON_CONSUMER_ENABLED=false` em Recommendation. Depois da
prontidão do consumidor, ative `USER_PREFERENCE_AVRO_WRITE_ENABLED=true`
em todas as réplicas de User e retome as escritas. Use
`USER_PREFERENCE_AVRO_REGISTRY_URL` nos dois serviços.

O workflow de aceitação de streams canônicos tem uma suíte de preferências
com Kafka, Registry e PostgreSQL descartáveis. Para executá-la localmente:

```bash
./mvnw -B -ntp -pl services/recommendation-service -am \
  -Dgroups=preference-avro-e2e -Dsurefire.failIfNoSpecifiedTests=false test
```

O consumidor aplica a projeção, a identidade do evento e o checkpoint
na mesma transação. Reentregas idênticas são ignoradas, e lacunas e colisões
entram na recuperação ordenada. Após a primeira escrita em Avro, o serviço
rejeita voltar aquele usuário ao JSON. Uma reversão do formato exige um
procedimento de migração coordenado.

A CI também verifica o caminho HTTP autenticado do User até a projeção do
Recommendation, com rollback, retry do outbox após registrar o schema e
reentrega do mesmo evento. Execute o teste focado com Docker e Java 21:

```sh
./mvnw -B -Pdistributed-e2e -pl integration-tests/distributed-e2e -am \
  -Dtest=UserPreferenceAvroE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
```
