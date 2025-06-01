#!/bin/bash

export REGISTRY=`oc get route default-route -n openshift-image-registry --template='{{ .spec.host }}'`
docker login -u `oc whoami` -p `oc whoami --show-token` ${REGISTRY}

mvn clean package

docker build . -t ${REGISTRY}/${NAMESPACE}/topics-usage-monitor:0.0.1
docker push ${REGISTRY}/${NAMESPACE}/topics-usage-monitor:0.0.1
