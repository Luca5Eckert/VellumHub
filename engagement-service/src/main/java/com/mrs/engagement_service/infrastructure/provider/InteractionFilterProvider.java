package com.mrs.engagement_service.infrastructure.provider;

import com.mrs.engagement_service.application.dto.filter.InteractionFilter;
import com.mrs.engagement_service.domain.model.Interaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class InteractionFilterProvider {

    /**
     * Creates a Specification based on interaction filters and user ID.
     */
    public Specification<Interaction> of(
            InteractionFilter interactionFilter,
            UUID userId
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(criteriaBuilder.equal(root.get("userId"), userId));

            if (interactionFilter != null) {

                if (interactionFilter.hasType()) {
                    predicates.add(criteriaBuilder.equal(root.get("type"), interactionFilter.type()));
                }

                if (interactionFilter.from() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                            root.get("timestamp"),
                            interactionFilter.from().toLocalDateTime()
                    ));
                }

                if (interactionFilter.to() != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(
                            root.get("timestamp"),
                            interactionFilter.to().toLocalDateTime()
                    ));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}