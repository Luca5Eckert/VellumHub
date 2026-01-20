# End-to-End Test Documentation

## 📝 Objetivo

Validar que o fluxo completo de recomendação funciona corretamente:  
**User → Engagement → Kafka → Recommendation → ML → Resposta**

## 🎯 O que este teste valida

O teste end-to-end valida que todos os serviços estão se comunicando corretamente e que o sistema gera recomendações baseadas em interações reais do usuário.

## 📋 Cenário de Teste

O teste executa o seguinte fluxo:

1. **POST /auth/register** - Criar usuário "teste@exemplo.com"
2. **POST /auth/login** - Fazer login e obter token JWT
3. **POST /media** - Criar 10 mídias (5 ACTION, 5 THRILLER)
4. **POST /engagement** - Registrar 5 interações em mídias ACTION (LIKE e WATCH)
5. **Aguardar 5 segundos** - Processamento Kafka
6. **GET /api/recommendations** - Buscar recomendações personalizadas
7. **Validar** - Verificar que recomendações favorecem ACTION sobre THRILLER

## ✅ Critérios de Aceitação

- ✓ Teste passa de ponta a ponta sem erros
- ✓ Recomendações refletem as interações do usuário (mais ACTION que THRILLER)
- ✓ Todos os eventos Kafka são consumidos corretamente
- ✓ Tempo de resposta total < 30 segundos

## 🚀 Como Executar

### Opção 1: Script Automatizado (Recomendado)

O script `run_e2e_test.sh` cuida de tudo automaticamente:

```bash
# No diretório raiz do projeto
./scripts/run_e2e_test.sh
```

Este script irá:
1. Verificar se o arquivo `.env` existe (e criar se necessário)
2. Iniciar todos os serviços com Docker Compose
3. Aguardar até que todos os serviços estejam saudáveis
4. Executar o teste E2E
5. Exibir os resultados

### Opção 2: Execução Manual

Se preferir controlar cada etapa manualmente:

#### Passo 1: Criar arquivo .env

Crie um arquivo `.env` na raiz do projeto com o seguinte conteúdo:

```env
# Database Configuration
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin123

# JWT Configuration (TEST-ONLY - never use in production!)
JWT_KEY=test-secret-key-for-jwt-authentication-min-256-bits-long-key-here-for-security
JWT_EXPIRATION=86400000
```

#### Passo 2: Iniciar os Serviços

```bash
docker-compose up -d
```

#### Passo 3: Aguardar Inicialização

Aguarde 1-2 minutos para que todos os serviços inicializem. Você pode verificar o status com:

```bash
# Verificar status dos containers
docker-compose ps

# Verificar saúde dos serviços
curl http://localhost:8084/actuator/health  # User Service
curl http://localhost:8081/actuator/health  # Catalog Service
curl http://localhost:8083/actuator/health  # Engagement Service
curl http://localhost:8085/actuator/health  # Recommendation Service
curl http://localhost:5000/health           # ML Service
```

#### Passo 4: Executar o Teste

```bash
python3 scripts/e2e_test.py
```

### Configuração Avançada

Você pode personalizar o teste usando variáveis de ambiente:

```bash
# Customizar usuário de teste
export E2E_TEST_EMAIL="custom@test.com"
export E2E_TEST_PASSWORD="CustomPass123!"
export E2E_TEST_NAME="Custom Test User"

# Aumentar tempo de espera do Kafka (em segundos)
export E2E_KAFKA_WAIT=10

# Executar teste
python3 scripts/e2e_test.py
```

## 📊 Interpretando os Resultados

### Saída de Sucesso

Quando o teste passa, você verá:

```
======================================================================
   ✓ TESTE E2E PASSOU COM SUCESSO!
======================================================================

Critérios de Aceitação:
  ✓ Teste passa de ponta a ponta
  ✓ Recomendações refletem interações do usuário
  ✓ Tempo de resposta < 30 segundos (foi X.XXs)
```

### Detalhes das Etapas

Para cada etapa, o teste exibe:

- **[STEP 1]** Registro do usuário
- **[STEP 2]** Login e obtenção do token JWT
- **[STEP 3]** Criação de 10 mídias
- **[STEP 4]** Registro de 5 interações
- **[STEP 5]** Aguardo do processamento Kafka
- **[STEP 6]** Busca de recomendações
- **[STEP 7]** Validação das recomendações

### Códigos de Status HTTP Esperados

| Endpoint | Método | Status Esperado |
|----------|--------|-----------------|
| `/auth/register` | POST | 201 Created (primeira vez) ou 409 Conflict (já existe) |
| `/auth/login` | POST | 200 OK |
| `/media` | POST | 201 Created |
| `/engagement` | POST | 201 Created |
| `/api/recommendations` | GET | 200 OK |

## 🐛 Troubleshooting

### Problema: Serviços não inicializam

**Solução:**
```bash
# Verificar logs dos serviços
docker-compose logs [service-name]

# Exemplo:
docker-compose logs user-service
docker-compose logs kafka
```

### Problema: Erro de conexão com banco de dados

**Solução:**
```bash
# Verificar se o PostgreSQL está rodando
docker-compose ps postgres

# Reiniciar PostgreSQL
docker-compose restart postgres
```

### Problema: Recomendações não são geradas

**Possíveis causas:**

1. **Kafka não processou os eventos:** Aumente o tempo de espera no teste
2. **ML Service não está rodando:** Verifique `curl http://localhost:5000/health`
3. **UserProfile não foi criado:** Verifique os logs do recommendation-service

**Solução:**
```bash
# Verificar logs do Kafka
docker-compose logs kafka

# Verificar logs do Recommendation Service
docker-compose logs recommendation-service

# Verificar logs do ML Service
docker-compose logs ml-service
```

### Problema: JWT inválido ou expirado

**Solução:**
```bash
# Verificar se JWT_KEY está correto no .env
cat .env | grep JWT_KEY

# Reiniciar user-service
docker-compose restart user-service
```

### Problema: Teste falha na criação de mídias (permissão negada)

**Causa:** O usuário de teste não tem privilégios de ADMIN

**Solução:** 
O teste foi projetado para usar autenticação JWT. Se necessário, você pode criar um usuário admin manualmente no banco de dados ou ajustar as permissões temporariamente.

## 🔍 Verificação Manual dos Dados

Após executar o teste, você pode verificar manualmente os dados criados:

### Verificar Usuários
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8084/users
```

### Verificar Mídias
```bash
curl http://localhost:8081/media
```

### Verificar Interações
```bash
curl http://localhost:8083/engagement/user/<user-id>
```

### Verificar Recomendações
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8085/api/recommendations
```

### Acessar Banco de Dados
```bash
# Conectar ao PostgreSQL
docker exec -it media-db psql -U admin

# Dentro do PostgreSQL:
\c user_db
SELECT * FROM users;

\c catalog_db
SELECT * FROM media;

\c engagement_db
SELECT * FROM interactions;

\c recommendation_db
SELECT * FROM user_profiles;
SELECT * FROM recommendations;
```

## 📈 Métricas e Performance

O teste mede:

- **Tempo total de execução:** Deve ser < 30 segundos
- **Taxa de sucesso:** Todas as 7 etapas devem passar
- **Latência de recomendação:** Tempo para gerar recomendações
- **Precisão:** Recomendações devem favorecer o gênero mais interagido

## 🔄 Executando Múltiplas Vezes

Para executar o teste múltiplas vezes:

```bash
# Limpar dados anteriores (opcional)
docker-compose down -v
docker-compose up -d

# Aguardar inicialização
sleep 60

# Executar teste
python3 scripts/e2e_test.py
```

**Nota:** O teste foi projetado para ser idempotente - pode ser executado múltiplas vezes sem limpar dados.

## 📝 Notas Importantes

1. **Portas utilizadas:**
   - User Service: 8084
   - Catalog Service: 8081
   - Engagement Service: 8083
   - Recommendation Service: 8085
   - ML Service: 5000

2. **Genres disponíveis:** ACTION, THRILLER, HORROR, COMEDY

3. **Tipos de interação:** LIKE, DISLIKE, WATCH

4. **Usuário de teste:**
   - Email: `teste@exemplo.com`
   - Senha: `SecurePass123!`

## 🤝 Contribuindo

Para adicionar novos cenários de teste:

1. Edite `scripts/e2e_test.py`
2. Adicione novos métodos na classe `E2ETest`
3. Adicione as etapas na lista `steps` no método `run()`
4. Atualize esta documentação

## 📚 Referências

- [README Principal](../README.md)
- [Docker Compose](../docker-compose.yml)
- [API Reference no README](../README.md#api-reference)
