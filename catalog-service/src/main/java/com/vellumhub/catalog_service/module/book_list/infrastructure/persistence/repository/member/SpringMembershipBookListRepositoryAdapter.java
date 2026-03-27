package com.vellumhub.catalog_service.module.book_list.infrastructure.persistence.repository.member;

import com.vellumhub.catalog_service.module.book_list.domain.model.BookListMembership;
import com.vellumhub.catalog_service.module.book_list.domain.port.MembershipBookListRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class SpringMembershipBookListRepositoryAdapter implements MembershipBookListRepository {

    private final JpaMembershipBookListRepository jpaMembershipBookListRepository;

    public SpringMembershipBookListRepositoryAdapter(JpaMembershipBookListRepository jpaMembershipBookListRepository) {
        this.jpaMembershipBookListRepository = jpaMembershipBookListRepository;
    }

    @Override
    public Optional<BookListMembership> findById(UUID membershipId) {
        return jpaMembershipBookListRepository.findById(membershipId);
    }

    @Override
    public void save(BookListMembership membership) {
        jpaMembershipBookListRepository.save(membership);
    }
}
