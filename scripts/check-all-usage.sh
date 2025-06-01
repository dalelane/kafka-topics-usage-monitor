#!/bin/bash

#
# How to get the usage information for all Kafka topics
#   as captured by the Topics Usage Monitor
#

if command -v oc >/dev/null 2>&1; then
  KUBECTL="oc"
elif command -v kubectl >/dev/null 2>&1; then
  KUBECTL="kubectl"
else
  echo "Error: 'oc' and 'kubectl' could not be found in your PATH." >&2
  exit 1
fi

for topic in $(${KUBECTL} get kafkatopic -o name); do
    ./scripts/check-usage.sh "$topic"
done
