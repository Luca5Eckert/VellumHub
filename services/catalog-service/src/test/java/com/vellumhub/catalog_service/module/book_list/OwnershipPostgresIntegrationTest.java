package com.vellumhub.catalog_service.module.book_list;

import com.vellumhub.catalog_service.module.book_list.application.query.list.GetAllBookListQuery;
import com.vellumhub.catalog_service.module.book_list.application.query.list.GetBookListByIdQuery;
import com.vellumhub.catalog_service.module.book_list.application.query.member.GetAllMembershipOfBookListQuery;
import com.vellumhub.catalog_service.module.book_list.application.query.member.GetMembershipQuery;
import com.vellumhub.catalog_service.module.book_list.application.use_case.list.GetAllBookListUseCase;
import com.vellumhub.catalog_service.module.book_list.application.use_case.list.GetBookListByIdUseCase;
import com.vellumhub.catalog_service.module.book_list.application.use_case.member.GetAllMembershipsOfBookListUseCase;
import com.vellumhub.catalog_service.module.book_list.application.use_case.member.GetMembershipBookListUseCase;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListResourceNotFoundException;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@ActiveProfiles("prod")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class OwnershipPostgresIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("catalog_ownership_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:65535");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("spring.kafka.admin.fail-fast", () -> "false");
        registry.add("management.health.kafka.enabled", () -> "false");
        registry.add("jwt.public-key-location", () -> "classpath:security/test-public.pem");
        registry.add("server.port", () -> "0");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired GetBookListByIdUseCase getById;
    @Autowired GetAllBookListUseCase findAll;
    @Autowired GetMembershipBookListUseCase getMembership;
    @Autowired GetAllMembershipsOfBookListUseCase getMembers;

    private UUID owner;
    private UUID member;
    private UUID outsider;
    private UUID privateList;
    private UUID publicList;
    private UUID memberMembership;

    @BeforeEach
    void seed() {
        owner = UUID.randomUUID();
        member = UUID.randomUUID();
        outsider = UUID.randomUUID();
        privateList = UUID.randomUUID();
        publicList = UUID.randomUUID();
        memberMembership = UUID.randomUUID();

        insertList(privateList, "PRIVATE", owner);
        insertList(publicList, "PUBLIC", owner);
        insertMembership(UUID.randomUUID(), privateList, owner, "OWNER");
        insertMembership(memberMembership, privateList, member, "VIEWER");
        insertMembership(UUID.randomUUID(), publicList, owner, "OWNER");
    }

    @Test
    void privateListIsVisibleOnlyToOwnerOrMemberButPublicListIsDiscoverable() {
        assertThat(getById.execute(GetBookListByIdQuery.of(owner, privateList)).getId())
                .isEqualTo(privateList);
        assertThat(getById.execute(GetBookListByIdQuery.of(member, privateList)).getId())
                .isEqualTo(privateList);
        assertThatThrownBy(() -> getById.execute(GetBookListByIdQuery.of(outsider, privateList)))
                .isInstanceOf(BookListResourceNotFoundException.class);
        assertThat(getById.execute(GetBookListByIdQuery.of(outsider, publicList)).getId())
                .isEqualTo(publicList);
    }

    @Test
    void listDiscoveryFiltersPrivateResourcesInPostgresNotOnlyControllers() {
        var query = GetAllBookListQuery.of(null, null, null, null, null, null, outsider, 0, 50);
        var outsiderIds = findAll.execute(query).map(BookList::getId).toList();
        assertThat(outsiderIds).contains(publicList).doesNotContain(privateList);

        var memberQuery = GetAllBookListQuery.of(null, null, null, null, null, null, member, 0, 50);
        assertThat(findAll.execute(memberQuery).map(BookList::getId).toList())
                .contains(privateList, publicList);
    }

    @Test
    void membershipsArePrivateEvenWhenTheBookListItselfIsReadable() {
        assertThat(getMembership.execute(GetMembershipQuery.of(memberMembership, member)).getUserId())
                .isEqualTo(member);
        assertThat(getMembership.execute(GetMembershipQuery.of(memberMembership, owner)).getUserId())
                .isEqualTo(member);
        assertThatThrownBy(() -> getMembership.execute(GetMembershipQuery.of(memberMembership, outsider)))
                .isInstanceOf(BookListResourceNotFoundException.class);

        assertThat(getMembers.execute(GetAllMembershipOfBookListQuery.of(privateList, member)))
                .hasSize(2);
        assertThatThrownBy(() -> getMembers.execute(GetAllMembershipOfBookListQuery.of(privateList, outsider)))
                .isInstanceOf(BookListResourceNotFoundException.class);
        assertThatThrownBy(() -> getMembers.execute(GetAllMembershipOfBookListQuery.of(publicList, outsider)))
                .isInstanceOf(BookListResourceNotFoundException.class);
    }

    private void insertList(UUID id, String type, UUID userOwner) {
        jdbc.update("""
                INSERT INTO book_lists(id, title, description, type, user_owner, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, now(), now())
                """, id, "Owned list", "Test description", type, userOwner);
    }

    private void insertMembership(UUID id, UUID listId, UUID userId, String role) {
        jdbc.update("""
                INSERT INTO book_list_memberships(id, book_list_id, user_id, role, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                """, id, listId, userId, role);
    }
}
