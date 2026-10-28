import tempfile
import unittest
from pathlib import Path

from check_service_boundaries import check


def pom(artifact, deps=""):
    return f"""<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>test</groupId><artifactId>{artifact}</artifactId>
    <dependencies>{deps}</dependencies></project>"""


class BoundariesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        services = ("auth", "catalog", "engagement", "user", "recommendation", "gateway")
        modules = "".join(f"<module>services/{s}-service</module>" for s in services)
        (self.root / "pom.xml").write_text(
            f'<project xmlns="http://maven.apache.org/POM/4.0.0"><modules>{modules}</modules></project>'
        )
        for name in services:
            path = self.root / "services" / f"{name}-service"
            path.mkdir(parents=True)
            (path / "pom.xml").write_text(pom(f"{name}-service"))

    def test_clean_reactor_passes(self):
        self.assertEqual([], check(self.root))

    def test_cross_service_dependency_fails(self):
        target = self.root / "services/catalog-service/pom.xml"
        target.write_text(pom("catalog-service", """
          <dependency><groupId>com.example</groupId>
          <artifactId>engagement-service</artifactId></dependency>"""))
        self.assertTrue(any("catalog-service" in e and "engagement-service" in e for e in check(self.root)))

    def test_profile_dependency_fails(self):
        target = self.root / "services/user-service/pom.xml"
        target.write_text(pom("user-service").replace("</project>", """
         <profiles><profile><id>production</id><dependencies><dependency>
         <groupId>test</groupId><artifactId>gateway-service</artifactId>
         </dependency></dependencies></profile></profiles></project>"""))
        self.assertTrue(check(self.root))

    def test_missing_service_fails_closed(self):
        (self.root / "services/gateway-service/pom.xml").unlink()
        self.assertTrue(check(self.root))

    def test_shared_contract_is_allowed(self):
        target = self.root / "services/catalog-service/pom.xml"
        target.write_text(pom("catalog-service", """
          <dependency><groupId>com.vellumhub</groupId>
          <artifactId>kafka-contracts</artifactId></dependency>"""))
        self.assertEqual([], check(self.root))


if __name__ == "__main__":
    unittest.main()
