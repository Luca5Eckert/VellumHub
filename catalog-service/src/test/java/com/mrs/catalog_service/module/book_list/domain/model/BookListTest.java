package com.mrs.catalog_service.module.book_list.domain.model;

import org.junit.jupiter.api.*;
import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BookList Domain Entity Rules")
class BookListTest {

    private UUID ownerId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Creation Rules")
    class CreationTests {
        @Test
        @DisplayName("Should initialize list with owner as ADMIN member")
        void shouldInitializeCorrectly() {
            var bookList = BookList.create("Title", "Desc", TypeBookList.PRIVATE, ownerId, new ArrayList<>());

            assertThat(bookList.getUserOwner()).isEqualTo(ownerId);
            assertThat(bookList.getMemberships()).hasSize(1);
            assertThat(bookList.getMemberships().get(0).getRole()).isEqualTo(MembershipRole.ADMIN);
        }
    }

    @Nested
    @DisplayName("Read Permission Rules (canRead)")
    class ReadPermissionTests {
        @Test
        @DisplayName("Should allow access to anyone if list is PUBLIC")
        void publicListAccess() {
            var list = BookList.create("T", "D", TypeBookList.PUBLIC, ownerId, new ArrayList<>());
            assertThat(list.canRead(otherUserId)).isTrue();
            assertThat(list.canRead(null)).isTrue();
        }

        @Test
        @DisplayName("Should allow owner to read private list")
        void ownerPrivateAccess() {
            var list = BookList.create("T", "D", TypeBookList.PRIVATE, ownerId, new ArrayList<>());
            assertThat(list.canRead(ownerId)).isTrue();
        }

        @Test
        @DisplayName("Should deny intruder access to private list")
        void intruderPrivateAccess() {
            var list = BookList.create("T", "D", TypeBookList.PRIVATE, ownerId, new ArrayList<>());
            assertThat(list.canRead(otherUserId)).isFalse();
        }
    }

    @Nested
    @DisplayName("Update Rules (canUpdate)")
    class UpdateTests {
        @Test
        @DisplayName("Should allow owner or ADMIN members to update")
        void adminUpdateAccess() {
            var list = BookList.create("T", "D", TypeBookList.PRIVATE, ownerId, new ArrayList<>());
            list.addMember(otherUserId, MembershipRole.ADMIN);

            assertThat(list.canUpdate(ownerId)).isTrue();
            assertThat(list.canUpdate(otherUserId)).isTrue();
        }

        @Test
        @DisplayName("Should deny non-admin members from updating")
        void nonAdminUpdateDeny() {
            var list = BookList.create("T", "D", TypeBookList.PRIVATE, ownerId, new ArrayList<>());
            list.addMember(otherUserId, MembershipRole.VIEWER);

            assertThat(list.canUpdate(otherUserId)).isFalse();
        }
    }
}