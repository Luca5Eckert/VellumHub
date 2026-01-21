# E2E Test Quick Reference

## 🚀 Como Executar

```bash
# Modo Automatizado (RECOMENDADO)
./scripts/run_e2e_test.sh

# Modo Manual
docker-compose up -d
sleep 120

# Seeding (escolha uma opção)
docker exec -i media-db psql -U admin < scripts/seed-e2e-data.sql  # SQL
# OU
pip3 install psycopg2-binary && python3 scripts/seed_e2e_python.py  # Python

# Execute o teste
python3 scripts/e2e_test.py
```

## ⚠️ Pré-requisitos IMPORTANTES

**Antes de executar, você DEVE ter um arquivo `.env` na raiz do projeto:**

```env
# Database Configuration
POSTGRES_USER=admin
POSTGRES_PASSWORD=admin123

# JWT Configuration
# ⚠️ CRITICAL: Set BOTH JWT_KEY and JWT_SECRET to the SAME value!
JWT_KEY=test-secret-key-for-jwt-authentication-min-256-bits-long-key-here-for-security
JWT_SECRET=test-secret-key-for-jwt-authentication-min-256-bits-long-key-here-for-security
JWT_EXPIRATION=86400000
```

**IMPORTANTE**: 
- Diferentes serviços podem usar `JWT_KEY` ou `JWT_SECRET`
- Ambos devem ter EXATAMENTE o mesmo valor
- Se não forem iguais, você terá erros 401 (Unauthorized)

## 🗄️ Seeding de Dados de Teste

**Por que o seed é necessário?**
O usuário de teste padrão (`teste@exemplo.com`) tem role USER e NÃO pode criar mídias. As mídias precisam ser criadas através do seed, que também cria um usuário ADMIN.

**Método 1: SQL Seed (Via Docker)**
```bash
docker exec -i media-db psql -U admin < scripts/seed-e2e-data.sql
```

**Método 2: Python Seed (Programático)**
```bash
pip3 install psycopg2-binary
python3 scripts/seed_e2e_python.py
```

**Verificar se o seed funcionou:**
```bash
pip3 install psycopg2-binary
python3 scripts/verify_e2e_data.py
```

## 📋 Scripts Disponíveis

- `run_e2e_test.sh` - Orquestração completa (recomendado)
- `e2e_test.py` - Teste E2E principal
- `seed-e2e-data.sql` - Seed SQL (admin user + mídias)
- `seed_e2e_python.py` - Seed Python (alternativa)
- `verify_e2e_data.py` - Verifica se dados de teste existem
- `generate_password_hash.py` - Gera hashes BCrypt

## ✅ O que o Teste Faz

O teste valida o fluxo completo:
1. ✅ Verifica saúde de todos os serviços
2. ✅ Registra/verifica usuário de teste
3. ✅ Faz login e obtém JWT token
4. ✅ Busca mídias do catálogo (dados pré-seeded)
5. ✅ Registra 5 interações com mídias ACTION
6. ✅ Aguarda processamento Kafka
7. ✅ Busca recomendações
8. ✅ Valida que recomendações foram geradas

## 🔧 Troubleshooting Rápido

**Erro: "role admin does not exist"**
```bash
# Verifique o usuário no .env
cat .env | grep POSTGRES_USER

# Se for diferente de "admin", use o usuário correto
docker exec -i media-db psql -U postgres < scripts/seed-e2e-data.sql
```

**Erro: 401 Unauthorized ao buscar mídias**
```bash
# O seed não foi executado, execute-o:
docker exec -i media-db psql -U admin < scripts/seed-e2e-data.sql

# Ou use o Python seed:
python3 scripts/seed_e2e_python.py

# Verifique se funcionou:
python3 scripts/verify_e2e_data.py
```

**Erro: JWT inválido (401 em todos endpoints)**
```bash
# Adicione JWT_SECRET ao .env (deve ser igual a JWT_KEY)
echo "JWT_SECRET=test-secret-key-for-jwt-authentication-min-256-bits-long-key-here-for-security" >> .env

# Reinicie os serviços
docker-compose down && docker-compose up -d
```

## Manual Execution

```bash
# 1. Start services
docker-compose up -d

# 2. Wait for services to be ready (1-2 minutes)
sleep 60

# 3. Run test
python3 scripts/e2e_test.py

# 4. View results
# Check test output for pass/fail status

# 5. Optional: View service logs
docker-compose logs -f recommendation-service
docker-compose logs -f ml-service
```

## Test Scenarios

The E2E test covers:

✅ User Registration (`POST /auth/register`)
✅ User Login (`POST /auth/login`)
✅ Media Creation (`POST /media`) - 10 items
✅ Engagement Tracking (`POST /engagement`) - 5 interactions
✅ Kafka Event Processing
✅ Recommendation Generation (`GET /api/recommendations`)
✅ Recommendation Validation

## Expected Results

- **Status**: All steps should PASS
- **Time**: < 30 seconds total
- **Recommendations**: Should favor ACTION genre over THRILLER
- **HTTP Codes**: 
  - Register: 201 (first time) or 409 (already exists)
  - Login: 200
  - Create Media: 201
  - Create Engagement: 201
  - Get Recommendations: 200

## Troubleshooting

### Services not starting
```bash
docker-compose logs [service-name]
docker-compose restart [service-name]
```

### Test fails
```bash
# Check individual service health
curl http://localhost:8084/actuator/health  # User
curl http://localhost:8081/actuator/health  # Catalog
curl http://localhost:8083/actuator/health  # Engagement
curl http://localhost:8085/actuator/health  # Recommendation
curl http://localhost:5000/health           # ML
```

### Clean restart
```bash
docker-compose down -v
docker-compose up -d
sleep 60
python3 scripts/e2e_test.py
```

## Documentation

Full documentation: [docs/E2E_TEST_GUIDE.md](../docs/E2E_TEST_GUIDE.md)
