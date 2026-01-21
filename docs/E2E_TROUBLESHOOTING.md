# E2E Testing - Guia Completo de Solução de Problemas

## 🔍 Problemas Identificados e Soluções

### Problema 1: Erro 401 ao Criar Mídias

**Causa Raiz**: O endpoint `/media` requer role `ADMIN`, mas o registro padrão cria usuários com role `USER`.

**Solução Implementada**:
- Novo script de seed (`seed-e2e-data.sql`) que cria usuários e mídias diretamente no banco
- Teste melhorado (`e2e_test_improved.py`) que busca mídias existentes ao invés de criar novas
- Script de orquestração (`run_e2e_complete.sh`) que executa o seed automaticamente

### Problema 2: Porta 8085 (Recommendation Service) Não Responde

**Causas Possíveis**:
1. Serviço não iniciou completamente
2. Serviço travou durante a inicialização
3. Dependências (Kafka, PostgreSQL) não estavam prontas

**Solução Implementada**:
- Health checks robustos com retries
- Espera adequada para infraestrutura (PostgreSQL, Kafka)
- Verificação individual de cada serviço
- Mensagens de erro diagnósticas

### Problema 3: Health Checks Falhando

**Causa**: Serviços Spring Boot levam tempo para inicializar completamente.

**Solução Implementada**:
- Espera inicial de 20 segundos após `docker-compose up`
- Health checks com retry (até 180 segundos total)
- Feedback visual do progresso
- Continua com aviso se algum serviço falhar

## 🚀 Como Usar a Nova Solução

### Opção 1: Script Completo Automatizado (RECOMENDADO)

```bash
./scripts/run_e2e_complete.sh
```

Este script:
1. ✅ Verifica pré-requisitos (Docker, docker-compose)
2. ✅ Inicia todos os serviços
3. ✅ Aguarda PostgreSQL e Kafka
4. ✅ Verifica saúde de todos os microserviços
5. ✅ Executa seed de dados de teste
6. ✅ Roda o teste E2E melhorado
7. ✅ Reporta resultados com dicas de troubleshooting

### Opção 2: Passo a Passo Manual

```bash
# 1. Iniciar serviços
docker-compose up -d

# 2. Aguardar 2 minutos para inicialização completa
sleep 120

# 3. Verificar se serviços estão rodando
docker-compose ps

# 4. Executar seed de dados
docker exec -i media-db psql -U admin < scripts/seed-e2e-data.sql

# 5. Rodar teste melhorado
python3 scripts/e2e_test_improved.py
```

## 📊 Diferenças entre Versão Antiga e Nova

| Aspecto | Versão Antiga | Versão Nova |
|---------|---------------|-------------|
| **Criação de Mídia** | Tenta criar via API (401 error) | Usa mídias pré-seeded |
| **Role do Usuário** | USER (sem permissão) | Usa dados seeded |
| **Health Checks** | Falha rápido | Retry com timeout adequado |
| **Seed de Dados** | Não implementado | Script SQL automático |
| **Diagnósticos** | Mensagens básicas | Dicas detalhadas de troubleshooting |
| **Orquestração** | Manual | Totalmente automatizada |

## 🔧 Troubleshooting Avançado

### Se o teste ainda falha...

#### 1. Verificar Status dos Serviços

```bash
docker-compose ps
```

Todos devem estar "Up". Se algum estiver "Exit" ou "Restarting":

```bash
docker-compose logs [nome-do-servico]
```

#### 2. Verificar Logs de Erro

```bash
# Recommendation Service (porta 8085)
docker-compose logs recommendation-service | tail -50

# User Service
docker-compose logs user-service | tail -50

# ML Service
docker-compose logs ml-service | tail -50
```

#### 3. Verificar Conectividade de Rede

```bash
# Testar cada serviço individualmente
curl http://localhost:8084/actuator/health  # User
curl http://localhost:8081/actuator/health  # Catalog
curl http://localhost:8083/actuator/health  # Engagement
curl http://localhost:8085/actuator/health  # Recommendation
curl http://localhost:5000/health           # ML
```

#### 4. Verificar Dados no Banco

```bash
# Conectar ao PostgreSQL (use o usuário do seu .env)
docker exec -it media-db psql -U admin

# Verificar usuários
\c user_db
SELECT id, email, role FROM users;

# Verificar mídias
\c catalog_db
SELECT id, title FROM media WHERE id LIKE 'media-action%';

# Sair
\q
```

**IMPORTANTE**: Se você receber erro "role does not exist", verifique qual usuário está configurado no .env:

```bash
# Verificar usuário configurado
cat .env | grep POSTGRES_USER

# Use o usuário correto (exemplo se for "postgres" ao invés de "admin")
docker exec -it media-db psql -U postgres
```

#### 4.1 Seed Script Falha com "role does not exist"

**Sintoma**: 
```
psql: FATAL: role "admin" does not exist
```

**Solução**:

1. Verifique o usuário no .env:
```bash
cat .env | grep POSTGRES_USER
# Deve mostrar: POSTGRES_USER=admin
```

2. Se o usuário for diferente, use o correto no seed:
```bash
# Exemplo se POSTGRES_USER=postgres
docker exec -i media-db psql -U postgres < scripts/seed-e2e-data.sql
```

3. Ou atualize o .env para usar "admin":
```env
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin123
```

4. Depois reinicie completamente:
```bash
docker-compose down -v
docker-compose up -d
sleep 120
docker exec -i media-db psql -U admin < scripts/seed-e2e-data.sql
```

#### 4.2 Erro 401 - Usuário sem permissão para criar mídia

**Sintoma**: 
```
✗ Falha ao buscar mídias: 401
⚠ Falha na etapa: Buscar mídias existentes
```

**Causa**: O usuário de teste `teste@exemplo.com` tem role USER e não pode criar mídias. As mídias precisam ser criadas através do seed.

**Solução - Opção 1 (SQL Seed - Recomendado)**:
```bash
# Verifique se PostgreSQL está rodando
docker-compose ps media-db

# Execute o seed SQL
docker exec -i media-db psql -U admin < scripts/seed-e2e-data.sql

# Verifique se os dados foram criados
docker exec -it media-db psql -U admin -d user_db -c "SELECT email, role FROM users WHERE email LIKE '%e2e%';"
docker exec -it media-db psql -U admin -d catalog_db -c "SELECT COUNT(*) FROM media;"
```

**Solução - Opção 2 (Python Seed)**:
```bash
# Instale dependências (se necessário)
pip3 install psycopg2-binary

# Execute o seeder Python
python3 scripts/seed_e2e_python.py
```

**Solução - Opção 3 (Manual via SQL)**:
```bash
# Conecte ao banco
docker exec -it media-db psql -U admin

# Crie o usuário admin
\c user_db
INSERT INTO users (id, name, email, password, role, created_at, updated_at)
VALUES (
    'e2e-admin-uuid-0000-0000-000000000001',
    'E2E Admin User',
    'admin@e2e.test',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    'ADMIN',
    NOW(),
    NOW()
);

# Crie mídias de teste
\c catalog_db
INSERT INTO media (id, title, description, release_year, media_type, cover_url, created_at, updated_at)
VALUES 
    ('media-action-uuid-0000-0000-000000000001', 'Action Hero 1', 'Uma história emocionante de ACTION', 2024, 'MOVIE', 'https://example.com/action-hero-1.jpg', NOW(), NOW()),
    ('media-action-uuid-0000-0000-000000000002', 'Action Hero 2', 'Uma história emocionante de ACTION', 2024, 'MOVIE', 'https://example.com/action-hero-2.jpg', NOW(), NOW());

# Adicione gêneros
INSERT INTO media_genres (media_id, genres) VALUES ('media-action-uuid-0000-0000-000000000001', 'ACTION');
INSERT INTO media_genres (media_id, genres) VALUES ('media-action-uuid-0000-0000-000000000002', 'ACTION');

\q
```

**Depois de seed, execute o teste novamente**:
```bash
python3 scripts/e2e_test.py
```

#### 5. Resetar Completamente

Se nada funcionar, reset completo:

```bash
# Parar e remover tudo
docker-compose down -v

# Remover imagens (opcional)
docker-compose down --rmi all

# Reconstruir e iniciar
docker-compose build --no-cache
docker-compose up -d

# Aguardar inicialização
sleep 120

# Rodar teste completo
./scripts/run_e2e_complete.sh
```

## 📝 Dados de Teste Seeded

### Usuários
- **Admin**: `admin@e2e.test` / `SecurePass123!` (role: ADMIN)
- **User**: `teste@exemplo.com` / `SecurePass123!` (role: USER)

### Mídias
- **ACTION**: 5 mídias (IDs: `media-action-uuid-0000-0000-000000000001` até `...005`)
- **THRILLER**: 5 mídias (IDs: `media-thriller-uuid-0000-0000-000000000001` até `...005`)

## 🎯 Fluxo do Teste Melhorado

```
1. Verificar saúde de todos os serviços (com retries)
   ↓
2. Registrar/verificar usuário teste
   ↓
3. Fazer login e obter JWT token
   ↓
4. Buscar mídias existentes do catálogo (seeded)
   ↓
5. Registrar 5 interações com mídias ACTION
   ↓
6. Aguardar processamento Kafka (5s configurável)
   ↓
7. Buscar recomendações
   ↓
8. Validar que recomendações foram geradas
```

## 💡 Melhorias Implementadas

1. **Health Checks Robustos**: Retries com timeout adequado
2. **Seed Automático**: Dados de teste criados automaticamente
3. **Melhor Diagnóstico**: Mensagens claras sobre o que falhou
4. **Configurável**: Variáveis de ambiente para customização
5. **Não Destrutivo**: Usa `ON CONFLICT DO NOTHING` no seed
6. **Idempotente**: Pode rodar múltiplas vezes sem problemas

## 📞 Suporte

Se o problema persistir:

1. Verifique os requisitos do sistema (Docker, memória disponível)
2. Veja a documentação completa em `docs/E2E_TEST_GUIDE.md`
3. Colete logs de todos os serviços:
   ```bash
   docker-compose logs > all-logs.txt
   ```
4. Abra uma issue com os logs e descrição do problema
