package com.mrs.engagement_service.module.rating.domain.filter;

import com.mrs.engagement_service.module.rating.domain.exception.FilterRatingIllegalArgumentException;

import java.time.OffsetDateTime;

/**
 * Immutable filter for rating searches with custom exception handling.
 */
public record RatingFilter(
        Integer minStars,
        Integer maxStars,
        OffsetDateTime from,
        OffsetDateTime to
) {
    public RatingFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new FilterRatingIllegalArgumentException("Invalid date range: 'from' must be before 'to'.");
        }
        if (minStars != null && (minStars < 0 || minStars > 5)) {
            throw new FilterRatingIllegalArgumentException("minStars must be between 0 and 5.");
        }
        if (maxStars != null && (maxStars < 0 || maxStars > 5)) {
            throw new FilterRatingIllegalArgumentException("maxStars must be between 0 and 5.");
        }
        if (minStars != null && maxStars != null && minStars > maxStars) {
            throw new FilterRatingIllegalArgumentException("minStars cannot be greater than maxStars.");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer minStars;
        private Integer maxStars;
        private OffsetDateTime from;
        private OffsetDateTime to;

        public Builder minStars(Integer minStars) {
            this.minStars = minStars;
            return this;
        }

        public Builder maxStars(Integer maxStars) {
            this.maxStars = maxStars;
            return this;
        }

        public Builder from(OffsetDateTime from) {
            this.from = from;
            return this;
        }

        public Builder to(OffsetDateTime to) {
            this.to = to;
            return this;
        }

        public RatingFilter build() {
            return new RatingFilter(minStars, maxStars, from, to);
        }
    }

    public boolean hasMinStars() {
        return minStars != null;
    }

    public boolean hasMaxStars() {
        return maxStars != null;
    }
}