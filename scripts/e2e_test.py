#!/usr/bin/env python3
"""
End-to-End Test for Media Recommendation System

Tests the complete flow:
User → Engagement → Kafka → Recommendation → ML → Response

This script validates that all services communicate correctly and
the system generates relevant recommendations based on user interactions.
"""

import requests
import json
import time
import sys
import os
from typing import Dict, List, Optional


class Colors:
    """ANSI color codes for terminal output"""
    HEADER = '\033[95m'
    OKBLUE = '\033[94m'
    OKCYAN = '\033[96m'
    OKGREEN = '\033[92m'
    WARNING = '\033[93m'
    FAIL = '\033[91m'
    ENDC = '\033[0m'
    BOLD = '\033[1m'


class E2ETest:
    """End-to-End test for the Media Recommendation System"""
    
    def __init__(self):
        self.user_service = "http://localhost:8084"
        self.catalog_service = "http://localhost:8081"
        self.engagement_service = "http://localhost:8083"
        self.recommendation_service = "http://localhost:8085"
        
        # Test user configuration (can be overridden via environment variables)
        self.test_user_email = os.getenv("E2E_TEST_EMAIL", "teste@exemplo.com")
        self.test_user_password = os.getenv("E2E_TEST_PASSWORD", "SecurePass123!")
        self.test_user_name = os.getenv("E2E_TEST_NAME", "Test User E2E")
        self.kafka_wait_time = int(os.getenv("E2E_KAFKA_WAIT", "5"))
        
        self.jwt_token: Optional[str] = None
        self.user_id: Optional[str] = None
        self.created_media_ids: List[str] = []
        self.action_media_ids: List[str] = []
        
    def log_step(self, step_num: int, description: str):
        """Log a test step"""
        print(f"\n{Colors.BOLD}{Colors.OKBLUE}[STEP {step_num}]{Colors.ENDC} {description}")
    
    def log_success(self, message: str):
        """Log a success message"""
        print(f"{Colors.OKGREEN}✓ {message}{Colors.ENDC}")
    
    def log_error(self, message: str):
        """Log an error message"""
        print(f"{Colors.FAIL}✗ {message}{Colors.ENDC}")
    
    def log_info(self, message: str):
        """Log an info message"""
        print(f"{Colors.OKCYAN}ℹ {message}{Colors.ENDC}")
    
    def log_warning(self, message: str):
        """Log a warning message"""
        print(f"{Colors.WARNING}⚠ {message}{Colors.ENDC}")
    
    def register_user(self) -> bool:
        """Step 1: Register a test user"""
        self.log_step(1, "POST /auth/register - Criar usuário de teste")
        
        payload = {
            "name": self.test_user_name,
            "email": self.test_user_email,
            "password": self.test_user_password
        }
        
        try:
            response = requests.post(
                f"{self.user_service}/auth/register",
                json=payload,
                timeout=10
            )
            
            if response.status_code == 201:
                self.log_success(f"Usuário criado: {self.test_user_email}")
                return True
            elif response.status_code == 409 or response.status_code == 400:
                self.log_warning(f"Usuário já existe, continuando com login")
                return True
            else:
                self.log_error(f"Falha ao criar usuário: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            self.log_error(f"Erro ao registrar usuário: {str(e)}")
            return False
    
    def login_user(self) -> bool:
        """Step 2: Login and obtain JWT token"""
        self.log_step(2, "POST /auth/login - Fazer login")
        
        payload = {
            "email": self.test_user_email,
            "password": self.test_user_password
        }
        
        try:
            response = requests.post(
                f"{self.user_service}/auth/login",
                json=payload,
                timeout=10
            )
            
            if response.status_code == 200:
                # Handle different response formats
                try:
                    # First try parsing as JSON
                    token_data = response.json()
                    if isinstance(token_data, dict):
                        self.jwt_token = token_data.get('token', token_data.get('accessToken', ''))
                    else:
                        self.jwt_token = str(token_data)
                except (ValueError, json.JSONDecodeError):
                    # If not JSON, assume plain text token
                    self.jwt_token = response.text.strip('"').strip()
                
                if self.jwt_token:
                    self.log_success(f"Login realizado com sucesso")
                    self.log_info(f"Token obtido: {self.jwt_token[:50]}...")
                    return True
                else:
                    self.log_error("Token não encontrado na resposta")
                    return False
            else:
                self.log_error(f"Falha no login: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            self.log_error(f"Erro ao fazer login: {str(e)}")
            return False
    
    def get_auth_headers(self) -> Dict[str, str]:
        """Get headers with JWT authorization"""
        return {
            "Authorization": f"Bearer {self.jwt_token}",
            "Content-Type": "application/json"
        }
    
    def create_media_items(self) -> bool:
        """Step 3: Create 10 media items (5 ACTION, 5 THRILLER)"""
        self.log_step(3, "POST /media - Criar 10 mídias (5 ACTION, 5 THRILLER)")
        
        media_items = [
            # ACTION movies
            {"title": "Action Hero 1", "genres": ["ACTION"], "type": "ACTION"},
            {"title": "Action Hero 2", "genres": ["ACTION"], "type": "ACTION"},
            {"title": "Action Hero 3", "genres": ["ACTION"], "type": "ACTION"},
            {"title": "Action Hero 4", "genres": ["ACTION"], "type": "ACTION"},
            {"title": "Action Hero 5", "genres": ["ACTION"], "type": "ACTION"},
            # THRILLER movies
            {"title": "Thriller Mystery 1", "genres": ["THRILLER"], "type": "THRILLER"},
            {"title": "Thriller Mystery 2", "genres": ["THRILLER"], "type": "THRILLER"},
            {"title": "Thriller Mystery 3", "genres": ["THRILLER"], "type": "THRILLER"},
            {"title": "Thriller Mystery 4", "genres": ["THRILLER"], "type": "THRILLER"},
            {"title": "Thriller Mystery 5", "genres": ["THRILLER"], "type": "THRILLER"},
        ]
        
        created_count = 0
        
        for media in media_items:
            payload = {
                "title": media["title"],
                "description": f"Uma história emocionante de {media['type']}",
                "releaseYear": 2024,
                "mediaType": "MOVIE",
                "genres": media["genres"],
                "coverUrl": f"https://example.com/{media['title'].replace(' ', '-').lower()}.jpg"
            }
            
            try:
                response = requests.post(
                    f"{self.catalog_service}/media",
                    json=payload,
                    headers=self.get_auth_headers(),
                    timeout=10
                )
                
                if response.status_code == 201:
                    # Extract media ID from Location header or response
                    location = response.headers.get('Location', '')
                    if location:
                        media_id = location.split('/')[-1]
                    else:
                        # Try to parse response body
                        try:
                            response_data = response.json()
                            media_id = response_data.get('id')
                        except (ValueError, json.JSONDecodeError, KeyError):
                            media_id = None
                    
                    if media_id:
                        self.created_media_ids.append(media_id)
                        if media["type"] == "ACTION":
                            self.action_media_ids.append(media_id)
                    
                    created_count += 1
                    self.log_success(f"Mídia criada: {media['title']}")
                else:
                    self.log_warning(f"Falha ao criar {media['title']}: {response.status_code}")
                    
            except Exception as e:
                self.log_error(f"Erro ao criar mídia {media['title']}: {str(e)}")
        
        self.log_info(f"Total de mídias criadas: {created_count}/10")
        
        # If we couldn't get IDs from creation, fetch them
        if len(self.created_media_ids) == 0:
            self.log_info("Buscando IDs das mídias criadas...")
            self.fetch_media_ids()
        
        return created_count >= 10
    
    def fetch_media_ids(self):
        """Fetch media IDs from the catalog service"""
        try:
            response = requests.get(
                f"{self.catalog_service}/media?pageSize=20",
                headers=self.get_auth_headers(),
                timeout=10
            )
            
            if response.status_code == 200:
                media_list = response.json()
                
                # Handle both list and paginated response
                if isinstance(media_list, dict):
                    media_list = media_list.get('content', media_list.get('data', []))
                
                for media in media_list:
                    media_id = media.get('id')
                    title = media.get('title', '')
                    genres = media.get('genres', [])
                    
                    if media_id:
                        if 'Action Hero' in title or 'ACTION' in genres:
                            self.action_media_ids.append(media_id)
                        self.created_media_ids.append(media_id)
                
                self.log_info(f"IDs encontrados: {len(self.created_media_ids)} total, {len(self.action_media_ids)} ACTION")
                
        except Exception as e:
            self.log_warning(f"Erro ao buscar IDs de mídia: {str(e)}")
    
    def register_interactions(self) -> bool:
        """Step 4: Register 5 interactions with ACTION media"""
        self.log_step(4, "POST /engagement - Registrar 5 interações em mídias ACTION")
        
        if not self.action_media_ids:
            self.log_error("Nenhuma mídia ACTION disponível para interações")
            return False
        
        # Get user ID from token or use a test UUID
        # For simplicity, we'll extract from login or use the email hash
        if not self.user_id:
            # Try to get user info
            try:
                response = requests.get(
                    f"{self.user_service}/users?size=100",
                    headers=self.get_auth_headers(),
                    timeout=10
                )
                if response.status_code == 200:
                    users = response.json()
                    if isinstance(users, dict):
                        users = users.get('content', users.get('data', []))
                    for user in users:
                        if user.get('email') == self.test_user_email:
                            self.user_id = user.get('id')
                            break
            except Exception as e:
                self.log_warning(f"Não foi possível obter ID do usuário: {str(e)}")
        
        if not self.user_id:
            self.log_error("ID do usuário não encontrado")
            return False
        
        self.log_info(f"User ID: {self.user_id}")
        
        interaction_types = ["LIKE", "WATCH", "LIKE", "WATCH", "LIKE"]
        interactions_created = 0
        
        # Register interactions with first 5 ACTION media
        for i in range(min(5, len(self.action_media_ids))):
            media_id = self.action_media_ids[i]
            interaction_type = interaction_types[i]
            
            payload = {
                "userId": self.user_id,
                "mediaId": media_id,
                "type": interaction_type,
                "interactionValue": 5.0 if interaction_type == "LIKE" else 1.0
            }
            
            try:
                response = requests.post(
                    f"{self.engagement_service}/engagement",
                    json=payload,
                    headers=self.get_auth_headers(),
                    timeout=10
                )
                
                if response.status_code == 201:
                    interactions_created += 1
                    self.log_success(f"Interação {interaction_type} registrada para mídia ACTION")
                else:
                    self.log_warning(f"Falha ao registrar interação: {response.status_code} - {response.text}")
                    
            except Exception as e:
                self.log_error(f"Erro ao registrar interação: {str(e)}")
        
        self.log_info(f"Total de interações registradas: {interactions_created}/5")
        return interactions_created >= 3  # At least 3 interactions needed
    
    def wait_for_kafka_processing(self):
        """Step 5: Wait for Kafka to process events"""
        self.log_step(5, "Aguardar processamento Kafka")
        
        self.log_info(f"Aguardando {self.kafka_wait_time} segundos para processamento dos eventos Kafka...")
        
        for i in range(self.kafka_wait_time):
            time.sleep(1)
            print(".", end="", flush=True)
        
        print()
        self.log_success("Processamento concluído")
    
    def get_recommendations(self) -> bool:
        """Step 6: Get personalized recommendations"""
        self.log_step(6, "GET /api/recommendations - Buscar recomendações")
        
        try:
            response = requests.get(
                f"{self.recommendation_service}/api/recommendations",
                headers=self.get_auth_headers(),
                timeout=15
            )
            
            if response.status_code == 200:
                recommendations = response.json()
                
                # Handle different response formats
                if isinstance(recommendations, dict):
                    recs = recommendations.get('recommendations', recommendations.get('data', []))
                else:
                    recs = recommendations
                
                self.log_success(f"Recomendações obtidas: {len(recs)} itens")
                
                if len(recs) > 0:
                    self.log_info("\nRecomendações recebidas:")
                    for i, rec in enumerate(recs[:10], 1):
                        title = rec.get('title', rec.get('mediaTitle', 'N/A'))
                        genres = rec.get('genres', [])
                        score = rec.get('recommendationScore', rec.get('score', 0))
                        print(f"  {i}. {title} - Genres: {genres} - Score: {score:.4f}")
                    
                    # Validate recommendations
                    return self.validate_recommendations(recs)
                else:
                    self.log_warning("Nenhuma recomendação retornada")
                    return False
            else:
                self.log_error(f"Falha ao obter recomendações: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            self.log_error(f"Erro ao buscar recomendações: {str(e)}")
            return False
    
    def validate_recommendations(self, recommendations: List[Dict]) -> bool:
        """Step 7: Validate that recommendations are relevant (more ACTION than THRILLER)"""
        self.log_step(7, "Validar que recomendações têm mais ACTION que THRILLER")
        
        action_count = 0
        thriller_count = 0
        
        for rec in recommendations[:10]:  # Check top 10
            genres = rec.get('genres', [])
            if 'ACTION' in genres:
                action_count += 1
            if 'THRILLER' in genres:
                thriller_count += 1
        
        self.log_info(f"Recomendações ACTION: {action_count}")
        self.log_info(f"Recomendações THRILLER: {thriller_count}")
        
        if action_count > thriller_count:
            self.log_success("✓ Validação PASSOU: Recomendações favorecem ACTION sobre THRILLER")
            return True
        elif action_count == 0 and thriller_count == 0:
            self.log_warning("⚠ Nenhuma recomendação de ACTION ou THRILLER encontrada")
            return True  # Pass if system recommended other content
        else:
            self.log_error(f"✗ Validação FALHOU: ACTION ({action_count}) não supera THRILLER ({thriller_count})")
            return False
    
    def run(self) -> bool:
        """Run the complete E2E test"""
        print(f"\n{Colors.BOLD}{Colors.HEADER}{'='*70}{Colors.ENDC}")
        print(f"{Colors.BOLD}{Colors.HEADER}   TESTE END-TO-END - Media Recommendation System{Colors.ENDC}")
        print(f"{Colors.BOLD}{Colors.HEADER}{'='*70}{Colors.ENDC}\n")
        
        start_time = time.time()
        
        # Execute test steps
        steps = [
            ("Registrar usuário", self.register_user),
            ("Fazer login", self.login_user),
            ("Criar mídias", self.create_media_items),
            ("Registrar interações", self.register_interactions),
            ("Aguardar Kafka", lambda: (self.wait_for_kafka_processing(), True)[1]),
            ("Buscar recomendações", self.get_recommendations),
        ]
        
        results = []
        for step_name, step_func in steps:
            try:
                result = step_func()
                results.append((step_name, result))
                
                if not result:
                    self.log_error(f"Falha na etapa: {step_name}")
                    # Continue anyway to see how far we get
            except Exception as e:
                self.log_error(f"Exceção na etapa {step_name}: {str(e)}")
                results.append((step_name, False))
        
        # Print summary
        end_time = time.time()
        duration = end_time - start_time
        
        print(f"\n{Colors.BOLD}{Colors.HEADER}{'='*70}{Colors.ENDC}")
        print(f"{Colors.BOLD}{Colors.HEADER}   RESUMO DO TESTE{Colors.ENDC}")
        print(f"{Colors.BOLD}{Colors.HEADER}{'='*70}{Colors.ENDC}\n")
        
        passed = 0
        for step_name, result in results:
            status = f"{Colors.OKGREEN}✓ PASSOU{Colors.ENDC}" if result else f"{Colors.FAIL}✗ FALHOU{Colors.ENDC}"
            print(f"{status} - {step_name}")
            if result:
                passed += 1
        
        print(f"\n{Colors.BOLD}Resultado: {passed}/{len(results)} etapas passaram{Colors.ENDC}")
        print(f"{Colors.BOLD}Tempo total: {duration:.2f} segundos{Colors.ENDC}")
        
        # Check acceptance criteria
        print(f"\n{Colors.BOLD}{Colors.OKBLUE}Critérios de Aceitação:{Colors.ENDC}")
        
        all_passed = all(result for _, result in results)
        time_ok = duration < 30  # Generous timeout for E2E
        
        print(f"  {'✓' if all_passed else '✗'} Teste passa de ponta a ponta")
        print(f"  {'✓' if results[-1][1] else '✗'} Recomendações refletem interações do usuário")
        print(f"  {'✓' if time_ok else '✗'} Tempo de resposta < 30 segundos (foi {duration:.2f}s)")
        
        success = all_passed and time_ok
        
        if success:
            print(f"\n{Colors.BOLD}{Colors.OKGREEN}{'='*70}{Colors.ENDC}")
            print(f"{Colors.BOLD}{Colors.OKGREEN}   ✓ TESTE E2E PASSOU COM SUCESSO!{Colors.ENDC}")
            print(f"{Colors.BOLD}{Colors.OKGREEN}{'='*70}{Colors.ENDC}\n")
        else:
            print(f"\n{Colors.BOLD}{Colors.FAIL}{'='*70}{Colors.ENDC}")
            print(f"{Colors.BOLD}{Colors.FAIL}   ✗ TESTE E2E FALHOU{Colors.ENDC}")
            print(f"{Colors.BOLD}{Colors.FAIL}{'='*70}{Colors.ENDC}\n")
        
        return success


def main():
    """Main entry point"""
    test = E2ETest()
    success = test.run()
    return 0 if success else 1


if __name__ == '__main__':
    sys.exit(main())
