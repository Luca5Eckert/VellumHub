package com.mrs.engagement_service.infrastructure.provider;

import com.mrs.engagement_service.module.rating.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class RatingFilterProvider {

    /**
     * Creates a Specification based on rating filters and user ID.
     */
    public Specification<Rating> of(
            RatingFilter ratingFilter,
            UUID userId
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));

            if (ratingFilter != null) {

                if (ratingFilter.hasMinStars()) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("stars"), ratingFilter.minStars()));
                }

                if (ratingFilter.hasMaxStars()) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("stars"), ratingFilter.maxStars()));
                }

                if (ratingFilter.from() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                            root.get("timestamp"),
                            ratingFilter.from().toLocalDateTime()
                    ));
                }

                if (ratingFilter.to() != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(
                            root.get("timestamp"),
                            ratingFilter.to().toLocalDateTime()
                    ));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}