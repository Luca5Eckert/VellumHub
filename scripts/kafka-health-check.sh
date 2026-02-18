#!/bin/bash

# Kafka Health Check Script
# This script verifies Kafka connectivity and topic status

set -e

KAFKA_BROKER="${KAFKA_BROKER:-localhost:9092}"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"

echo "========================================="
echo "Kafka Health Check"
echo "========================================="
echo ""

# Check if Kafka container is running
echo "1. Checking if Kafka container is running..."
if docker ps | grep -q "$KAFKA_CONTAINER"; then
    echo "✓ Kafka container is running"
else
    echo "✗ Kafka container is not running"
    exit 1
fi

echo ""

# Check broker connectivity
echo "2. Testing broker connectivity..."
if docker exec "$KAFKA_CONTAINER" kafka-broker-api-versions --bootstrap-server "$KAFKA_BROKER" > /dev/null 2>&1; then
    echo "✓ Successfully connected to Kafka broker"
else
    echo "✗ Failed to connect to Kafka broker"
    exit 1
fi

echo ""

# List topics
echo "3. Listing existing Kafka topics..."
TOPICS=$(docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server "$KAFKA_BROKER" --list 2>/dev/null)
if [ -z "$TOPICS" ]; then
    echo "⚠ No topics found (this is normal for a new installation)"
else
    echo "✓ Found topics:"
    echo "$TOPICS" | while read -r topic; do
        echo "  - $topic"
    done
fi

echo ""

# Check consumer groups
echo "4. Checking consumer groups..."
GROUPS=$(docker exec "$KAFKA_CONTAINER" kafka-consumer-groups --bootstrap-server "$KAFKA_BROKER" --list 2>/dev/null)
if [ -z "$GROUPS" ]; then
    echo "⚠ No consumer groups found"
else
    echo "✓ Found consumer groups:"
    echo "$GROUPS" | while read -r group; do
        echo "  - $group"
    done
fi

echo ""

# Display topic details if topics exist
if [ -n "$TOPICS" ]; then
    echo "5. Topic details:"
    echo "$TOPICS" | while read -r topic; do
        echo ""
        echo "Topic: $topic"
        docker exec "$KAFKA_CONTAINER" kafka-topics --bootstrap-server "$KAFKA_BROKER" --describe --topic "$topic" 2>/dev/null || echo "  ✗ Could not describe topic"
    done
fi

echo ""
echo "========================================="
echo "Health Check Complete"
echo "========================================="
