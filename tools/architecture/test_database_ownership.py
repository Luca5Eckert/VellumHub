import unittest
from check_database_ownership import check


OWNERS = [
    ("auth", "auth-postgres", "vellum_auth"),
    ("catalog", "postgres-catalog", "catalog_db"),
    ("engagement", "postgres-engagement", "engagement_db"),
    ("user", "postgres-user", "user_db"),
    ("recommendation", "postgres-recommendation", "recommendation_db"),
]


def compose(url="jdbc:postgresql://postgres-catalog:5432/catalog_db", duplicated=False):
    definitions = []
    for service, host, db in OWNERS:
        source = url if service == "catalog" else (
            "jdbc:postgresql://postgres-catalog:5432/catalog_db"
            if duplicated and service == "user"
            else f"jdbc:postgresql://{host}:5432/{db}"
        )
        definitions.append(f"  {host}:\n    environment:\n      POSTGRES_DB: {db}\n")
        url_key = "VELLUM_AUTH_DB_URL" if service == "auth" else "SPRING_DATASOURCE_URL"
        definitions.append(
            f"  {service}-service:\n    environment:\n      {url_key}: {source}\n"
        )
    return "services:\n" + "".join(definitions) + "volumes:\n  data:\n"


class DatabaseOwnershipTest(unittest.TestCase):
    def test_correct_isolation_wiring(self):
        self.assertEqual([], check(self.with_domain_credentials(compose())))

    def test_wrong_database_endpoint_is_rejected(self):
        self.assertTrue(check(compose(url="jdbc:postgresql://postgres-user:5432/user_db")))

    def test_two_services_sharing_endpoint_is_rejected(self):
        self.assertTrue(check(compose(duplicated=True)))

    def test_missing_services_are_rejected(self):
        self.assertTrue(check("services:\n  catalog-service:\n"))

    def test_shared_database_credentials_are_rejected(self):
        self.assertTrue(any("POSTGRES_CATALOG_USER" in issue for issue in check(compose())))

    @staticmethod
    def with_domain_credentials(source):
        for owner, host, _db in OWNERS:
            key = owner.upper()
            app_user = "VELLUM_AUTH_DB_USERNAME" if owner == "auth" else "SPRING_DATASOURCE_USERNAME"
            app_password = "VELLUM_AUTH_DB_PASSWORD" if owner == "auth" else "SPRING_DATASOURCE_PASSWORD"
            source = source.replace(
                f"  {owner}-service:\n    environment:\n",
                f"  {owner}-service:\n    environment:\n"
                f"      {app_user}: ${{POSTGRES_{key}_USER:?required}}\n"
                f"      {app_password}: ${{POSTGRES_{key}_PASSWORD:?required}}\n"
            ).replace(
                f"  {host}:\n    environment:\n",
                f"  {host}:\n    environment:\n"
                f"      POSTGRES_USER: ${{POSTGRES_{key}_USER:?required}}\n"
                f"      POSTGRES_PASSWORD: ${{POSTGRES_{key}_PASSWORD:?required}}\n"
            )
        return source

    def test_domain_credentials_are_required(self):
        self.assertEqual([], check(self.with_domain_credentials(compose())))


if __name__ == "__main__":
    unittest.main()
