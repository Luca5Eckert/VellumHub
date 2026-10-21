import unittest
from check_database_ownership import check

def compose(url="jdbc:postgresql://postgres-catalog:5432/catalog_db", duplicated=False):
    definitions = []
    for service, host, db in [
        ("catalog", "postgres-catalog", "catalog_db"),
        ("engagement", "postgres-engagement", "engagement_db"),
        ("user", "postgres-user", "user_db"),
        ("recommendation", "postgres-recommendation", "recommendation_db"),
    ]:
        source = url if service == "catalog" else (
            "jdbc:postgresql://postgres-catalog:5432/catalog_db" if duplicated and service == "user"
            else f"jdbc:postgresql://{host}:5432/{db}"
        )
        definitions.append(f"  {host}:\n    environment:\n      POSTGRES_DB: {db}\n")
        definitions.append(f"  {service}-service:\n    environment:\n      SPRING_DATASOURCE_URL: {source}\n")
    return "services:\n" + "".join(definitions) + "volumes:\n  data:\n"


class DatabaseOwnershipTest(unittest.TestCase):
    def test_correct_isolation_wiring(self):
        self.assertEqual([], check(compose()))

    def test_wrong_database_endpoint_is_rejected(self):
        self.assertTrue(check(compose(url="jdbc:postgresql://postgres-user:5432/user_db")))

    def test_two_services_sharing_endpoint_is_rejected(self):
        self.assertTrue(check(compose(duplicated=True)))

    def test_missing_services_are_rejected(self):
        self.assertTrue(check("services:\n  catalog-service:\n"))
