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
package uk.co.dalelane.demos.kafka.monitoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import uk.co.dalelane.demos.kafka.monitoring.data.UsageUpdate;
import uk.co.dalelane.demos.kafka.monitoring.k8s.K8sClient;
import uk.co.dalelane.demos.kafka.monitoring.k8s.objects.KafkaTopic;
import uk.co.dalelane.demos.kafka.monitoring.prometheus.PrometheusClient;
import uk.co.dalelane.demos.kafka.monitoring.prometheus.PrometheusData;
import uk.co.dalelane.demos.kafka.monitoring.utils.Config;



public class TopicsUsageMonitor {

    private static final Logger log = LoggerFactory.getLogger(TopicsUsageMonitor.class);

    private final PrometheusClient prometheus;
    private final K8sClient<? extends KafkaTopic, ? extends DefaultKubernetesResourceList<? extends KafkaTopic>> kubernetes;

    private final Config config;


    public TopicsUsageMonitor() throws MonitoringException {
        config = new Config();

        prometheus = new PrometheusClient(config);
        kubernetes = K8sClient.create(config);
    }

    public void run() throws MonitoringException {
        while (true) {
            log.info("updating");

            // retrieve current known usage from Kubernetes annotations
            Map<String, UsageUpdate> knownTopics = kubernetes.getTopicsWithUsageTimestamps();

            // get the latest usage data from Prometheus
            Map<String, UsageUpdate> updates = new HashMap<>();
            updates = runQuery(config.getBytesInMetric(),  updates, knownTopics);
            updates = runQuery(config.getBytesOutMetric(), updates, knownTopics);

            // store any usage changes in Kubernetes annotations
            kubernetes.submitUpdates(updates);

            // reset before next poll
            log.info("update complete");
            knownTopics = null;
            updates = null;
            pause();
        }
    }


    private void pause() {
        try {
            Thread.sleep(config.getPollIntervalSeconds() * 1000);
        }
        catch (InterruptedException e) {
            log.debug("Interrupt", e);
        }
    }


    private Map<String, UsageUpdate> runQuery(String query, Map<String, UsageUpdate> usageUpdates, Map<String, UsageUpdate> knownTopics) throws MonitoringException {
        log.debug("Retrieving latest {} info from Prometheus", query);
        List<PrometheusData> data = prometheus.query(query);
        for (PrometheusData result : data) {
            if (knownTopics.containsKey(result.topic())) {
                if (usageUpdates.containsKey(result.topic())) {
                    // we have seen this topic before, and have already decided that we
                    //  need to submit an update for this topic - just need to add the
                    //  value from this query
                    UsageUpdate topicUsageInfo = usageUpdates.get(result.topic());
                    applyUpdate(topicUsageInfo, query, result);
                }
                else {
                    // we have seen this topic before - but not yet decided that the data has changed
                    UsageUpdate topicUsageInfo = knownTopics.get(result.topic());
                    if (dataHasChanged(topicUsageInfo, query, result)) {
                        applyUpdate(topicUsageInfo, query, result);
                        usageUpdates.put(result.topic(), topicUsageInfo);
                    }
                    // else {
                    //     // metrics data already in k8s matches value from Prometheus
                    // }
                }
            }
            // else {
            //     // not seen this topic before - prometheus has usage data
            //     //  about a topic not known in Kubernetes - ignore
            // }
        }

        return usageUpdates;
    }



    /**
     * Returns true if the value retrieved from Prometheus (value) is
     *  different from the data stored for the topic in Kubernetes (topicUsageInfo).
     */
    private boolean dataHasChanged(UsageUpdate topicUsageInfo, String query, PrometheusData data) {
        if (query.equals(config.getBytesInMetric())) {
            return data.totalbytes() != topicUsageInfo.getLastBytesIn();
        }
        else if (query.equals(config.getBytesOutMetric())) {
            return data.totalbytes() != topicUsageInfo.getLastBytesOut();
        }
        return false;
    }

    /**
     * Updates topicUsageInfo with the latest information from Prometheus.
     */
    private void applyUpdate(UsageUpdate topicUsageInfo, String query, PrometheusData data) {
        if (query.equals(config.getBytesInMetric())) {
            topicUsageInfo.setLastBytesIn(data.totalbytes(), data.timestamp());
        }
        else if (query.equals(config.getBytesOutMetric())) {
            topicUsageInfo.setLastBytesOut(data.totalbytes(), data.timestamp());
        }
    }


    // ------------------------------------------------------------------------

    public static void main(String[] args) {
        TopicsUsageMonitor monitor;
        try {
            monitor = new TopicsUsageMonitor();
            monitor.run();
        }
        catch (MonitoringException e) {
            log.error("Fatal exception", e);
        }
    }
}
