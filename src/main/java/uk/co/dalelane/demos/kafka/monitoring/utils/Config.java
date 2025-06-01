/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package uk.co.dalelane.demos.kafka.monitoring.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.dalelane.demos.kafka.monitoring.MonitoringException;



/**
 * Provides validated access to configuration from environment variables.
 */
public class Config {

    private final Logger log = LoggerFactory.getLogger(Config.class);


    /** Kubernetes namespace where the Kafka cluster is running */
    private static final String NAMESPACE = "NAMESPACE";

    /** Name of the Kafka cluster */
    private static final String CLUSTER = "CLUSTER";

    /** Whether to monitor a Strimzi cluster ("strimzi") or an Event Streams cluster ("eventstreams") */
    private static final String MODE = "MODE";

    /** Metric to use to identify per-topic bytes in */
    private static final String BYTES_IN_METRIC = "BYTES_IN_METRIC";

    /** Metric to use to identify per-topic bytes out */
    private static final String BYTES_OUT_METRIC = "BYTES_OUT_METRIC";

    /** URL for querying Prometheus */
    private static final String PROMETHEUS_URL = "PROMETHEUS_URL";

    /** How frequently (in seconds) to poll for metrics updates */
    private static final String POLL_INTERVAL_SECONDS = "POLL_INTERVAL_SECONDS";


    private final String namespace;
    private final String cluster;
    private final Mode mode;
    private final String bytesInMetric;
    private final String bytesOutMetric;
    private final String prometheusUrl;
    private final int pollIntervalSeconds;


    public Config() throws MonitoringException {
        namespace = getRequiredVar(NAMESPACE);
        cluster = getRequiredVar(CLUSTER);
        bytesInMetric = getVar(BYTES_IN_METRIC, "kafka_server_brokertopicmetrics_bytesin_total");
        bytesOutMetric = getVar(BYTES_OUT_METRIC, "kafka_server_brokertopicmetrics_bytesout_total");
        prometheusUrl = getVar(PROMETHEUS_URL, "https://thanos-querier.openshift-monitoring.svc:9091");
        pollIntervalSeconds = getVar(POLL_INTERVAL_SECONDS, 60 * 60);

        String modeStr = getVar(MODE, "strimzi");
        mode = "eventstreams".equalsIgnoreCase(modeStr) ? Mode.EVENTSTREAMS : Mode.STRIMZI;
    }

    private String getRequiredVar(String key) throws MonitoringException {
        if (System.getenv(key) == null) {
            throw new MonitoringException("Missing required environment variable " + key);
        }
        String value = System.getenv(key);
        log.info("{} = {}", key, value);
        return value;
    }
    private String getVar(String key, String defaultValue) {
        String value = defaultValue;
        if (System.getenv(key) != null) {
            value = System.getenv(key);
        }
        log.info("{} = {}", key, value);
        return value;
    }
    private int getVar(String key, int defaultValue) throws MonitoringException {
        int value = defaultValue;
        if (System.getenv(key) != null) {
            try {
                value = Integer.parseInt(System.getenv(key));
            }
            catch (NumberFormatException nfe) {
                throw new MonitoringException("Unexpected environment variable " + key, nfe);
            }
        }
        log.info("{} = {}", key, value);
        return value;
    }
    private String trimSlashes(String input) {
        if (input.endsWith("/")) {
            return input.substring(0, input.length() - 1);
        }
        return input;
    }


    public String getNamespace() {
        return namespace;
    }
    public String getCluster() {
        return cluster;
    }
    public int getPollIntervalSeconds() {
        return pollIntervalSeconds;
    }
    public String getBytesInMetric() {
        return bytesInMetric;
    }
    public String getBytesOutMetric() {
        return bytesOutMetric;
    }
    public String getPrometheusUrl() {
        return trimSlashes(prometheusUrl);
    }
    public Mode getMode() {
        return mode;
    }
    public String getK8sAnnotationPrefix() {
        if (mode == Mode.EVENTSTREAMS) {
            return "eventstreams.ibm.com";
        }
        else {
            return "strimzi.io";
        }
    }


    public static enum Mode {
        STRIMZI, EVENTSTREAMS
    }
}
