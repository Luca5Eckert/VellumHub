package com.vellumhub.kafka.contracts;

public final class KafkaConsumerGroups {

    public static final String ENGAGEMENT_SERVICE = "engagement-service";
    public static final String ENGAGEMENT_SERVICE_DLT = "engagement-service-dlt-group";

    public static final String RECOMMENDATION_SERVICE = "recommendation-service";
    public static final String RECOMMENDATION_SERVICE_DLT = "recommendation-service-dlt-group";
    public static final String RECOMMENDATION_USER_PROFILE = "recommendation_service_group";
    public static final String RECOMMENDATION_GROUP = "recommendation-group";
    public static final String RECOMMENDATION_GROUP_TEST = "recommendation-group-test";

    private KafkaConsumerGroups() {
    }
}
