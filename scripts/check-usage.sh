#!/bin/bash

#
# How to get the usage information for a Kafka topic
#   stored by the Topics Usage Monitor
#

if [ -z "$1" ]; then
  echo "Usage: $0 <KafkaTopic name>"
  exit 1
fi

INPUT_NAME="$1"
TOPIC_NAME="${INPUT_NAME##*/}"

if command -v oc >/dev/null 2>&1; then
  KUBECTL="oc"
elif command -v kubectl >/dev/null 2>&1; then
  KUBECTL="kubectl"
else
  echo "Error: 'oc' and 'kubectl' could not be found in your PATH." >&2
  exit 1
fi

# Reads the "topic last used" timestamp from the annotation
ANNOTATION_VALUE=$(${KUBECTL} get kafkatopic "$TOPIC_NAME" -o jsonpath="{.metadata.annotations['dalelane\.co\.uk/lastused-timestamp']}")

# Error case - no annotation found (Is the Topics Usage Monitor running?)
if [ -z "$ANNOTATION_VALUE" ]; then
  echo "$TOPIC_NAME has no usage annotation"
  exit 0
fi

# Error case - annotation has been modified to something unexpected
if ! [[ "$ANNOTATION_VALUE" =~ ^[0-9]+$ ]]; then
  echo "Annotation value for $TOPIC_NAME is not a valid number: $ANNOTATION_VALUE"
  exit 1
fi

# Special case - Prometheus metrics have no mention of this topic
if [ "$ANNOTATION_VALUE" -eq 0 ]; then
  echo "$TOPIC_NAME has never been used"
  exit 0
fi

# General case - annotation contains a timestamp in a milliseconds-since-epoch format
TIMESTAMP_SECONDS=$((ANNOTATION_VALUE / 1000))
HUMAN_READABLE_DATE=$(date -r "$TIMESTAMP_SECONDS" '+%Y-%m-%d %H:%M:%S')
echo "$TOPIC_NAME last used at $HUMAN_READABLE_DATE"
