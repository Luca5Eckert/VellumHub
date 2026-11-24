# Execução de auditoria — 24/11/2026

Fonte medida: `a9a6398373ecbfd5c7688613ad74dc548f74b2a9` (`main`). Consulte a [auditoria e matriz profissional](../../backend-engineering-audit.md). Histórico, experimentos da auditoria e validações CI têm proveniências distintas. A main avançou durante a medição com a PR #385. O relatório de arquitetura registra o delta e a CI de `1565ba01`. Os resultados locais abaixo pertencem a `a9a63983`.

## Reprodução

No checkout do SHA medido, execute os scripts a partir da branch da auditoria ou copie os novos scripts de `tools/metrics` para um checkout descartável do SHA. Use JDK 21 completo, Docker local acessível a Testcontainers, Docker Compose v2.40 ou compatível com `!override`, curl, Python 3.10+ e acesso aos repositórios Maven e de imagens. A comparação usa o perfil `reference`. `AUDIT_PROFILE=smoke` reduz a amostra sem reproduzir os volumes de referência.

```bash
AUDIT_OUTPUT=/tmp/vh-audit-reference bash tools/metrics/run-backend-audit.sh
./mvnw -B -ntp -fae -Pdistributed-e2e verify
bash tools/metrics/run-compose-audit.sh a9a6398373ecbfd5c7688613ad74dc548f74b2a9 /tmp/vh-audit-compose
```

A primeira execução grava ambiente, comandos exatos, status, logs, contagens JUnit e JSON dos harnesses em um diretório novo. O wrapper aceita parâmetros Maven adicionais, como `-s` e `-Dmaven.repo.local`. O Compose usa imagens publicadas com tag SHA, portas loopback fixas, fixtures de autenticação e chaves efêmeras. Execute em host descartável e com as portas livres. O script remove apenas os containers, volumes e diretório temporário do projeto de auditoria. Execute Maven e Compose em janelas separadas para comparações de desempenho.

## Protocolo

| Experimento | Amostra, controle e janela |
| --- | --- |
| Autonomia, projeção, freshness | Kafka 7.5 + pgvector/PG15 + Flyway e Spring Security/MockMvc com principal JWT simulado. Embeddings determinísticos. Sequencial 10, rajadas de 30 (lote 10) e 90 (lote 30) por suíte de propagação. Polling de 10 ms. Autonomia: 3 warm-ups, 30 leituras seriais com User/Catalog/Engagement ausentes, cache de domínio fora do caminho. Canary 503 nos endpoints conhecidos. Uma passagem por cenário, sem controle equivalente com as três APIs saudáveis neste harness. |
| Autonomia via rede e recuperação | Imagens da main, Gateway sobre HTTP local e Auth/PKCE real, Recommendation sem Redis de domínio. Uma fixture sintética é inserida no DB local para acionar o fallback por popularidade. Após 3 warm-ups, 30 GETs com upstream saudável e 30 com três containers efetivamente parados, concorrência 1. Uma recuperação. Mede o tempo do comando de partida até as três APIs voltarem a dar 200. A janela não mede replay de eventos ou convergência durante indisponibilidade. |
| Consistência | Suites focadas usam DB, broker/Registry onde aplicável, fixtures determinísticas e asserções de estado, offsets e contrato. Casos executados e duração são JUnit. Volume de falhas é o definido pelos testes, sem carga ou extrapolação de capacidade. |
| Ranking | Seed 2822026, 120 livros, 24 perfis, 5 interações de treino e 5 itens holdout por perfil. Baselines popularity-only e semantic-only contra 70/30 no mesmo pool. Model-backed MiniLM com 384 dimensões. Uma passagem pelo dataset. nDCG, Precision, Recall, MRR, cobertura e vazios. Não isola desempenho de HNSW nem custo de inferência. |
| Cache | OFF e ON em JVMs distintas, com PostgreSQL/Redis reais e MockMvc com principal JWT simulado. 1.000 operações × 3 repetições × c1/c8/c32 × 4 perfis (cold, warm, mixed80, mixed95) por modo. Flush e warm-up separados da janela. Catalog usa 2.200 livros com dois gêneros e aquece 12 chaves por janela. Recommendation usa 600 livros e 1.400 perfis sintéticos (seed 360036), aplica até 30 GETs de warm-up e acrescenta cenário de stampede. Catalog usa hash de resposta e contadores de keyspace. Recommendation mede hit/(hit+miss) no nível da aplicação. Uso de keyspace para Catalog é aproximação, com leituras de gerações além do acesso aos DTOs. A ordem planejada é OFF→ON, sem randomização. |
| OAuth e telemetria | Journey OIDC/PKCE de loopback com perfil local, provisionamento e checks defensivos já existentes. Prometheus alvo a alvo, logs Loki e correlação HTTP no Tempo/Gateway. Falha de Auth com JWKS quente. Google externo e traces causais Kafka exigem campanha distinta. |

## Histórico rastreado

- Em `e4322184d39bd46de5508438e5f23d0c18fe80bb`, o [run 41245745067](https://github.com/Luca5Eckert/VellumHub/actions/runs/41245745067) foi bem-sucedido. A [consolidação versionada](../../README.md#audited-reference-31-october-2026) registra 30/30 leituras, zero conexões observadas e p95 de 28,17 ms, além de 16/16 casos de idempotência. A transferência do ZIP bruto retornou HTTP 403 neste ambiente. Os números históricos deste relatório vêm da consolidação versionada. O arquivo bruto da CI pode ser consultado pela página do run se disponível ao leitor.
- A [PR #287](https://github.com/Luca5Eckert/VellumHub/pull/287) registra 60/60 e p95 de 15,98–19,86 ms em duas execuções de referência anteriores. A origem dos 24 ms do currículo não foi identificada no histórico de README, docs e métricas nem nas PRs #286, #287 e #327 consultadas.
- Os 16 casos se referem a `ConsumerIdempotencyDistributedTest`. As garantias do produtor Outbox são cobertas por classes separadas, inclusive `BookOutboxPostgresDistributedTest`, `BookOutboxKafkaDistributedTest` e `EngagementOutboxKafkaDistributedTest`.
- Os benchmarks históricos de Redis de [Catalog](https://github.com/Luca5Eckert/VellumHub/actions/runs/43666479401) e [Recommendation](https://github.com/Luca5Eckert/VellumHub/actions/runs/43650750262) usam workloads e serviços diferentes. Não se atribui resultado do Catalog a Recommendation.
- No SHA atual, [publicação e Kind](https://github.com/Luca5Eckert/VellumHub/actions/runs/45299684920), [aceitação Avro](https://github.com/Luca5Eckert/VellumHub/actions/runs/45299684643), [Registry](https://github.com/Luca5Eckert/VellumHub/actions/runs/45299684547) e [OAuth scopes/RBAC](https://github.com/Luca5Eckert/VellumHub/actions/runs/45299684373) concluíram com sucesso. A execução do Registry inclui o módulo `schema-evolution-poc` com Kafka e Registry persistente, além de asserções de compatibilidade e idempotência do registro. A aceitação Avro verifica consumo nos três serviços de domínio envolvidos e o caminho HTTP→outbox→Avro→Recommendation de preferências. São verificações remotas independentes das execuções abaixo. [Jobs por SHA](main-ci-runs.json) · [Rulesets observados](main-rules.json)

## Execução neste ambiente

As rodadas e códigos de saída estão registrados em UTC. [Status por experimento](execution-status.tsv)

Runner Linux x86_64, Docker 29.9.2 e Compose 2.40.3. Docker reportou 3 CPUs e 10.452.459.520 bytes. O cgroup do shell anunciou quota de 2 CPUs e limite de 8 GiB. Maven 3.9.11, JDK Temurin 21.0.13.1 e driver de armazenamento Docker `vfs`. O diretório Maven foi movido para a workspace. O Java precisou de configuração do proxy da cloud e de um JDK completo extraído de imagem oficial. O ambiente inicial fornecia JRE sem `javac`. Os resultados locais são observações da cloud, sensíveis a contenção e versões das imagens.

### Autonomia e projeção — reference Kafka/pgvector

**30/30 GETs autenticados** via MockMvc com principal JWT simulado, resultado materializado validado e **zero** conexões aos três endpoints instrumentados. p50 **15,64 ms**, p95 **54,01 ms**, p99 **56,02 ms**. Para n=30 o p99 coincide com o extremo pelo estimador do harness. O resultado funcional coincide com o histórico. O p95 ficou **25,84 ms** acima dos 28,17 ms documentados em 31/10. O alvo informal de 24 ms não apareceu. SHA, máquina, estado do JVM, versões e carga concorrente da inicialização do Compose variaram. A contribuição de cada fator não foi isolada.

| Cenário | Projetados | DLT inesperada | Eventos/s observados | p50 / p95 / p99 (ms) | Lag máximo amostrado | Catch-up | Duração |
| --- | ---: | ---: | ---: | --- | ---: | ---: | ---: |
| Sequencial | 10/10 | 0 | 2,87 | 104,64 / 1.956,33 / 1.956,33 | 1 | 94,75 ms | 3.483,09 ms |
| Rajada de 30 | 30/30 | 0 | 44,53 | 373,54 / 505,00 / 505,56 | 30 | 530,00 ms | 673,70 ms |
| Rajada de 90 | 90/90 | 0 | 35,08 | 1.601,97 / 2.206,71 / 2.355,76 | 89 | 2.384,28 ms | 2.565,79 ms |

O catch-up nesta tabela é o do benchmark de projeção com Kafka ativo. O cálculo inclui a observação por polling. Compare a rajada de 90 com os 137,57 eventos/s e o p95 de 511,67 ms registrados no histórico, considerando a diferença de ambiente. O [JSON bruto sanitizado](projection-read-samples.json) remove IDs e campos de erro livres e preserva observações de tempo, contagens e status. [Metadados](projection-metadata.json)

### Evento até mudança visível no ranking

A primeira tentativa encerrou ao esperar o Kafka atingir `RUNNING`, antes de produzir métricas. Na repetição isolada, Kafka, PostgreSQL/pgvector, o consumidor de Rating e a consulta canônica de ranking refletiram **130/130** interações no primeiro lugar em leitura MockMvc com principal JWT simulado. Duração JUnit, com infraestrutura, **44,41 s**.

| Cenário | Refletidos | p50 / p95 / p99 (ms) | Duração |
| --- | ---: | --- | ---: |
| Sequencial | 10/10 | 55,17 / 1.937,01 / 1.937,01 | 2.457,94 ms |
| Rajada de 30 | 30/30 | 194,82 / 368,70 / 407,90 | 408,91 ms |
| Rajada de 90 | 90/90 | 918,05 / 1.090,39 / 1.099,69 | 1.157,80 ms |

O p95 da maior rajada foi 1.090,39 ms, frente a 732,74 ms no histórico documentado. Houve também um primeiro evento lento na janela sequencial. A duração inclui polling e observação round-robin. As suítes de projeção e freshness começaram em processos e condições diferentes. Compare seus resultados separadamente. [Amostras sanitizadas](freshness-samples.json)

### Plano SQL com pgvector/HNSW

No PostgreSQL 15.20 com pgvector 0.8.7, aplicamos as migrações SQL versionadas a um banco descartável e geramos 6.000 vetores sintéticos de 384 dimensões e um perfil (seed PostgreSQL 0,42). A consulta canônica de `RankingQueries` foi extraída do código, com pool 200, limit 10, offset 0 e sem itens excluídos. Fizemos três execuções consecutivas sem warm-up explícito, concorrência 1 e sem reset de cache entre execuções. A reprodução é `bash tools/metrics/run-ranking-plan-audit.sh /tmp/vh-ranking-plan.txt`.

| Query | Plano em `book_features` | Execução (ms) |
| --- | --- | ---: |
| SQL canônico, 70/30 | Scan sequencial + top-N sort em 3/3 | 13,798 / 10,330 / 6,215 |
| Controle KNN, uma chave de distância | Index Scan HNSW | 1,061 |
| Mesmo controle, index scan desabilitado | Scan sequencial + top-N sort | 3,224 |

O controle HNSW recuperou **6 dos 10** resultados do controle exato nesse único perfil, recall@10 **0,60**. O controle simplifica a ordenação e o tamanho do pool. Ele confirma a capacidade de usar o índice. Seu tempo não é uma comparação A/B do ranker canônico. A amostra de uma consulta por controle, a ordem fixa e a carga concorrente impedem conclusão de ganho de velocidade ou recall geral. Não há latência de inferência no experimento. [EXPLAIN bruto com BUFFERS](ranking-explain.txt)

### Idempotência Kafka/PostgreSQL

`ConsumerIdempotencyDistributedTest`: **16/16**, zero falhas, erros ou skips, 102,5 s de suíte (07:45:49–07:48:37 BRT, incluindo preparação Maven e containers). Reproduz a contagem histórica. Os casos cobrem rollback da projeção/checkpoint de Book, ReadingProgress e Rating, redelivery serial e concorrente, lacunas em quatro streams, colisões de identidade e quarentena com DLT mantendo offset pendente. Parte dos casos invoca o consumidor no contexto de teste. Casos de redelivery de Rating e quarentena de Book atravessam o broker e verificam o efeito/offset. O total não é um volume de eventos nem uma contagem de testes de publicação do outbox. [Casos JUnit](ConsumerIdempotencyDistributedTest.json)

### Produtores Outbox e ensaios Avro no reator

A execução local de `./mvnw -B -ntp -fae -Pdistributed-e2e verify` validou **14 casos** em `JdbcOutboxStoreDistributedTest`, `BookOutboxPostgresDistributedTest` e `BookOutboxKafkaDistributedTest`. Outro teste Kafka comprovou ReadingProgress. Os produtores Avro de Book e ReadingProgress passaram três casos no total, e os dois ensaios de cutover passaram um caso cada. As três suítes distribuídas de publicação Kafka, produtor Avro e cutover de Engagement passaram **4/4**. Essas suítes complementam os 16 casos do consumidor.

`BookAvroRegistryFailureDistributedTest` encerrou com erro de ambiente antes da asserção central. O daemon rejeitou `pause` porque o runtime não oferece `cgroup.freeze`. Um container mínimo da mesma máquina reproduziu a falha com `docker pause`. Esta execução local do reator, portanto, tem um erro de infraestrutura. Os gates remotos com Kafka e Registry ficam registrados separadamente por SHA. [JUnit selecionado do reator local](outbox-verify.json) · [Controle de pause](docker-pause-probe.txt)

O reator terminou com erro também no `ReadingProgressAvroRecommendationDistributedTest`. O RootlessKit encontrou uma porta ocupada ao criar a rede do Kafka, antes das asserções do teste. O E2E cruzado ficou fora desta execução local por dependência de módulos com erro. Os módulos independentes concluíram. [Resumo do reator](reactor-summary.txt)

O smoke `RatingServiceApplicationTests.contextLoads` passou em 41,11 s apesar de retries do KafkaAdmin na inicialização. A asserção do smoke se limita ao carregamento do contexto. Para integração com broker e efeitos observáveis, as suítes distribuídas e os gates de release dão evidência mais direta.

### Qualidade offline do ranking

Uma execução sem warm-up, 120 embeddings produzidos pela pipeline real. A suíte com startup, migração, inferência, persistência e medições levou **100,7 s**. A instrumentação atual agrega essas etapas. O tempo total fornece duração do ensaio. A latência de inferência e o consumo por estratégia exigem amostragem própria.

| Ranker | Precision@10 | Recall@10 | nDCG@10 | MRR@10 | Cobertura | Vazios |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Popularidade | 0,0417 | 0,0833 | 0,0662 | 0,1173 | 10,83% | 0% |
| Semântico | 0,5000 | 1,0000 | 1,0000 | 1,0000 | 100% | 0% |
| 70/30 | 0,2708 | 0,5417 | 0,5988 | 0,8727 | 54,17% | 0% |

Pelo nDCG@10, no mesmo perfil, o 70/30 venceu popularidade em **23/24** (um empate) e ficou atrás do semântico em **22/24** (dois empates). O score da variante semântica deve ser interpretado junto da construção do corpus sintético e das preferências/holdout. O efeito de popularidade em tráfego real continua em aberto. O [JSON agregado](ranking-quality-aggregates.json) preserva métricas e contagens pareadas. Os arquivos locais do harness mantêm amostras por perfil, excluídas do repositório por convenção. [Metadados da avaliação](ranking-quality-metadata.json)

### OAuth 2.0/OIDC e telemetria em imagens publicadas

No Compose de laboratório, as imagens dos seis serviços foram puxadas pela tag do SHA auditado. O cliente local verificou registro com UUIDv7, provisionamento Auth→User, Authorization Code + PKCE, relay do bearer, leituras nas quatro APIs e as negativas defensivas de token, escopo e papel. Com Auth parado, o token já emitido seguiu aceito pela rota Gateway→User com JWKS quente. Essa etapa usa Auth e serviços reais, enquanto o perfil local usa HTTP loopback e uma identidade descartável. [Saída dos probes OIDC](oidc-probes.txt) · [Digests OCI](compose-image-digests.txt)

No reator local, `FederatedOidcLifecycleIntegrationTest` passou **11/11** com provedor OIDC simulado e PostgreSQL. Cobriu vinculação canônica, expiração e exclusão de sessão, logout, retomada entre réplicas e rejeição de sessões de identidades desabilitadas. [Casos JUnit](FederatedOidcLifecycleIntegrationTest.json)

O primeiro Compose com OIDC e observabilidade mostrou logs dos seis serviços no Loki, trace HTTP no Tempo e incremento da métrica de Gateway, além de fontes/dashboards provisionados no Grafana. A consulta aos targets encontrou cinco scrapes ativos. Auth estava down porque o perfil `local-poc` abre administração em 9000 e o Prometheus do Compose base consulta 9001. O overlay descartável do script final mantém 9001 via `MANAGEMENT_SERVER_PORT`. A inicialização também exigiu leituras diretas no healthcheck do Recommendation para evitar o proxy do runner. Uma tentativa paralela ficou sem espaço no armazenamento Docker `vfs`. Após limpar os recursos da própria auditoria e isolar o Compose, a rodada com o overlay corrigido obteve **6/6 scrapes**, logs dos seis serviços e correlação do trace HTTP com a métrica do Gateway. [Probes iniciais](telemetry-initial-probes.txt) · [Probes da repetição](telemetry-probes.txt) · [Metadados Compose](compose-environment.txt)

### Redis A/B

Os dois modos concluíram no perfil `reference`. Catalog teve **72.000 GETs**, zero erros e hashes de resposta iguais em todos os 36 pares de janela. Recommendation teve **72.246 GETs**, contando stampede, com asserções de sucesso e sem erros de cache nas janelas da tabela. Com cache quente a c32, os dois eliminaram as consultas preparadas Hibernate nas três janelas (Catalog, 7.500→0. Recommendation, 6.000→0). A carga fria c32 preservou a contagem SQL, pagando o custo adicional do cache.

Valores abaixo são medianas das três repetições de cada percentil/throughput, com **OFF → ON**. O hit ratio agrega contagens das três janelas ON. Em Catalog ele é a razão de **comandos Redis** observados no keyspace, incluindo acessos auxiliares e gerações. Em Recommendation ele é o hit ratio de **requisições de cache** reportado pela aplicação.

| Serviço e cenário | p50 (ms) | p95 (ms) | p99 (ms) | GETs/s | Hit ratio ON |
| --- | ---: | ---: | ---: | ---: | ---: |
| Catalog frio c32 | 17,33 → 39,96 | 67,23 → 81,95 | 83,56 → 111,36 | 1.270,93 → 719,71 | 0% |
| Catalog quente c32 | 17,36 → 6,94 | 78,14 → 33,83 | 105,56 → 54,13 | 1.102,81 → 2.871,40 | 50% de comandos |
| Catalog misto80 c32 | 15,75 → 10,34 | 63,47 → 57,03 | 82,17 → 67,18 | 1.307,24 → 1.633,13 | 40% de comandos |
| Catalog misto95 c32 | 17,17 → 8,46 | 80,86 → 36,08 | 116,18 → 47,59 | 1.043,00 → 2.188,83 | 47,5% de comandos |
| Recommendation frio c32 | 20,52 → 44,46 | 89,37 → 93,24 | 114,81 → 180,06 | 968,15 → 613,32 | 0% |
| Recommendation quente c32 | 16,50 → 5,73 | 92,97 → 32,56 | 131,67 → 37,10 | 903,43 → 3.664,54 | 100% |
| Recommendation misto80 c32 | 19,16 → 8,38 | 93,66 → 38,70 | 119,79 → 53,63 | 891,50 → 2.213,43 | 80% |
| Recommendation misto95 c32 | 19,76 → 7,33 | 92,18 → 40,97 | 120,54 → 56,03 | 948,22 → 2.372,81 | 95% |

O primeiro Catalog OFF parou no timeout de inicialização de conexão Redis (150 ms), antes das métricas. O par usa a repetição isolada de OFF, executada depois de ON e sem alterar o timeout. Os números de c32 são sensíveis à quota de CPU, às pressões do host, ao warm-up e ao padrão de chaves. Em particular, OFF de Recommendation compartilhou parte da janela com tentativas do Compose. O ensaio dá uma comparação local com as duas configurações e repetição interna, com evidência mais forte para equivalência e eliminação de SQL que para um fator fixo de velocidade. Os resultados de p95 quente são distintos das referências históricas. A telemetria de CPU, GC, ranking e Redis está nos JSONs de origem. Catalog exporta dados por repetição e planos de SQL com IDs sintéticos omitidos. Recommendation exporta também os tempos individuais.

- Catalog [OFF](catalog-cache-off.json) · [ON](catalog-cache-on.json)
- Recommendation [OFF](recommendation-cache-off.json.gz) · [ON](recommendation-cache-on.json.gz)

Catalog passou **3/3** verificações de comportamento em contextos Spring com infraestrutura real. As suítes exercitam a inicialização e leitura sem Redis, visibilidade entre dois contextos com invalidação e rollback, e a fronteira HTTP autenticada. [JUnit de comportamento de cache do Catalog](catalog-cache-correctness.json)

Recommendation passou **2/2** testes de comportamento. O cache aquecido refletiu um evento Kafka confirmado sem esperar o TTL, e o hit continuou sujeito à autorização. A c32 com chaves quentes, a mediana da CPU do processo por repetição foi 970→480 ms. O timer somado do ranking caiu de 29.316,10 ms para zero. Em chaves frias, a CPU medida foi 900→1.840 ms e o timer somado ficou em 30.514,89→31.541,10 ms. O timer inclui espera e concorrência, e a CPU cobre o processo inteiro. Os valores comparam o caminho de leitura do mesmo ranker e deixam o custo isolado de cada estratégia de inferência em aberto. [JUnit de comportamento de cache de Recommendation](RecommendationCacheFreshnessIT.json)

### HTTP real com três containers parados

O Gateway encaminhou chamadas HTTP de loopback com bearer emitido por Auth. A fixture de um livro no PostgreSQL local exercitou o fallback de cold-start por popularidade. O cache de Recommendation estava OFF. O runner verificou a presença do livro em todas as respostas. As duas janelas foram seriais com três requisições de warm-up e uma passagem, sem carga externa.

| Janela | Leituras válidas | Erros | p50 / p95 / p99 (ms) | Duração | GETs/s |
| --- | ---: | ---: | --- | ---: | ---: |
| Três upstreams ativos | 30/30 | 0% | 45,24 / 95,47 / 100,60 | 1,47 s | 20,38 |
| User, Catalog e Engagement parados | 30/30 | 0% | 37,08 / 187,85 / 923,34 | 2,25 s | 13,31 |

O p99 é o máximo observado nesta amostra. As janelas medem rede local, Gateway e bearer assinado, enquanto a reprodução histórica usa MockMvc, projeção povoada por Kafka e canary instrumentada. O ensaio HTTP tem um único livro preexistente e mede comportamento durante a parada, sem inferência causal sobre a diferença de latência ou sobre capacidade.

**Recuperação das rotas não confirmada em 180 s.** As três rotas de origem responderam inicialmente HTTP 500. Depois do restart, houve HTTP 403 nas três rotas até a última amostra (179,22 s), mesmo com containers saudáveis. Foram 144 rodadas: 61 com três 500, 2 mistas e 81 com três 403. O Gateway registrou falhas de conexão durante a subida, classificadas no log como `AnnotatedConnectException`. A causa do 403 ficou em aberto. Os suspeitos a discriminar incluem endereço interno reaproveitado no restart versus autorização após a subida. Um passo seguinte é registrar a resolução DNS e o destino antes/depois do restart e comparar Gateway com uma sonda direta ao serviço, sem publicar o bearer. Esse resultado cobre a autonomia de leitura e identifica uma lacuna separada de recuperação end-to-end. [Amostras HTTP e sondas de recuperação](compose-read-window.json)

Um controle separado no mesmo Docker iniciou três containers leves e os reiniciou na ordem C, A, B. Os endereços foram redistribuídos, com A recebendo o anterior de B, B o anterior de C e C o anterior de A. O comando `bash tools/metrics/run-docker-address-audit.sh` reproduz o controle. Essa observação sustenta testar resolução e cache de DNS do Gateway na próxima rodada da aplicação. Os endereços da rodada HTTP original não foram coletados. A versão entregue do runner registra a associação de endereços aos nomes previamente observados, sem gravar os endereços. [Saída do controle Docker](docker-address-probe.txt)
