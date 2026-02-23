package com.mrs.engagement_service.module.rating.infrastructure.repository;

import com.mrs.engagement_service.share.provider.RatingFilterProvider;
import com.mrs.engagement_service.module.rating.domain.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class RatingRepositoryAdapter implements RatingRepository {

    public final RatingRepositoryJpa ratingRepositoryJpa;
    public final RatingFilterProvider ratingFilterProvider;

    public RatingRepositoryAdapter(RatingRepositoryJpa ratingRepositoryJpa, RatingFilterProvider ratingFilterProvider) {
        this.ratingRepositoryJpa = ratingRepositoryJpa;
        this.ratingFilterProvider = ratingFilterProvider;
    }

    @Override
    public Rating save(Rating rating) {
        return ratingRepositoryJpa.save(rating);
    }

    @Override
    public Page<Rating> findAll(UUID userId, RatingFilter ratingFilter, PageRequest pageRequest) {
        Specification<Rating> ratingSpecification = ratingFilterProvider.of(
                ratingFilter,
                userId
        );

        return ratingRepositoryJpa.findAll(ratingSpecification, pageRequest);
    }

    @Override
    public boolean existsByUserIdAndBookId(UUID userId, UUID bookId) {
        return ratingRepositoryJpa.existsByUserIdAndBookId(userId, bookId);
    }

    @Override
    public Optional<Rating> findById(long id) {
        return ratingRepositoryJpa.findById(id);
    }

    @Override
    public boolean existsbyId(Long ratingId) {
        return ratingRepositoryJpa.existsById(ratingId);
    }

    @Override
    public void deleteById(Long ratingId) {
        ratingRepositoryJpa.deleteById(ratingId);
    }

    @Override
    public Page<Rating> findAllByBookId(int bookId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);

        return ratingRepositoryJpa.findAllByBookId(bookId, pageRequest);
    }


}
