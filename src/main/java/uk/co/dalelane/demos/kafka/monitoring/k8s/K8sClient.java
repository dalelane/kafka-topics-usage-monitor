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
package uk.co.dalelane.demos.kafka.monitoring.k8s;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.DefaultKubernetesResourceList;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import uk.co.dalelane.demos.kafka.monitoring.MonitoringException;
import uk.co.dalelane.demos.kafka.monitoring.data.UsageUpdate;
import uk.co.dalelane.demos.kafka.monitoring.k8s.objects.KafkaTopic;
import uk.co.dalelane.demos.kafka.monitoring.k8s.objects.KafkaTopicStatus;
import uk.co.dalelane.demos.kafka.monitoring.utils.Config;
import uk.co.dalelane.demos.kafka.monitoring.utils.Formats;
import uk.co.dalelane.demos.kafka.monitoring.utils.Config.Mode;

public abstract class K8sClient<T extends KafkaTopic, L extends DefaultKubernetesResourceList<T>> {

    private final Logger log = LoggerFactory.getLogger(K8sClient.class);

    private static final String ANNOTATION_KEY_PREFIX = "dalelane.co.uk/lastused-";
    private static final String ANNOTATION_KEY_TIMESTAMP = ANNOTATION_KEY_PREFIX + "timestamp";
    private static final String ANNOTATION_KEY_BYTESIN   = ANNOTATION_KEY_PREFIX + "bytesin";
    private static final String ANNOTATION_KEY_BYTESOUT  = ANNOTATION_KEY_PREFIX + "bytesout";

    protected final KubernetesClient client;
    private final Config config;


    public static K8sClient<? extends KafkaTopic, ? extends DefaultKubernetesResourceList<? extends KafkaTopic>> create(Config config) {
        if (config.getMode() == Mode.EVENTSTREAMS) {
            return new EventStreamsK8sClient(config);
        }
        else {
            return new StrimziK8sClient(config);
        }
    }


    public K8sClient(Config config) {
        this.config = config;

        log.debug("Creating Kubernetes client for accessing Kafka topic operands");
        client = new KubernetesClientBuilder().build();
    }


    // ------------------------------------------------------------------------

    public abstract MixedOperation<T, L, Resource<T>> createResourceClient();

    // ------------------------------------------------------------------------


    /**
     * Stores the provided usage updates in Kubernetes as annotations on the
     *  corresponding KafkaTopic operand.
     */
    public void submitUpdates(Map<String, UsageUpdate> lastUsageUpdatesByTopic) {
        log.debug("submitting usage updates to Kubernetes");
        MixedOperation<T, L, Resource<T>> kafkaTopicClient = createResourceClient();
        List<T> topics = kafkaTopicClient.inNamespace(config.getNamespace()).list().getItems();

        for (T topic : topics) {
            log.debug("topic {}", topic);
            KafkaTopicStatus status = topic.getStatus();

            // check if we know what topic this relates to - if not, skip it
            if (isTopicNameUnknown(topic)) continue;

            // check if we have an updated timestamp for this topic - if not, skip it
            if (!lastUsageUpdatesByTopic.containsKey(status.getTopicName())) continue;

            // check if the topic is in the cluster being monitored - if not, skip it
            if (isTopicInWrongCluster(topic)) continue;

            // add the update to the topic
            submitAnnotations(kafkaTopicClient, topic, lastUsageUpdatesByTopic.get(status.getTopicName()));
        }
    }

    /**
     * Retrieves usage info from the annotations on KafkaTopic operands.
     */
    public Map<String, UsageUpdate> getTopicsWithUsageTimestamps() throws MonitoringException {
        log.debug("Getting Kafka topic information from Kubernetes");
        Map<String, UsageUpdate> topicTimestamps = new HashMap<>();

        MixedOperation<T, L, Resource<T>> kafkaTopicClient = createResourceClient();
        List<T> topics = kafkaTopicClient.inNamespace(config.getNamespace()).list().getItems();
        log.debug("found {} topics", topics.size());

        for (T topic : topics) {
            log.debug("topic {}", topic);

            // check if we know what topic this relates to - if not, skip it
            if (isTopicNameUnknown(topic)) continue;

            // check if the topic is in the cluster being monitored - if not, skip it
            if (isTopicInWrongCluster(topic)) continue;

            UsageUpdate update = null;

            if (noLastUsedTimeAnnotation(topic)) {
                // check if the topic has any existing usage annotations - if not, add an initial annotation
                update = new UsageUpdate(topic.getStatus().getTopicName());

                submitAnnotations(kafkaTopicClient, topic, update);
            }
            else {
                // retrieve existing usage annotation
                update = createUpdate(topic);
            }

            topicTimestamps.put(topic.getStatus().getTopicName(), update);
        }

        log.debug("{} topics with timestamps", topicTimestamps.size());
        return topicTimestamps;
    }


    // ------------------------------------------------------------------------


    private boolean isTopicNameUnknown(KafkaTopic topic) {
        KafkaTopicStatus status = topic.getStatus();
        return status == null || status.getTopicName() == null;
    }

    private boolean isTopicInWrongCluster(KafkaTopic topic) {
        ObjectMeta metadata = topic.getMetadata();
        Map<String, String> labels = metadata.getLabels();
        return labels == null || !config.getCluster().equals(labels.get(config.getK8sAnnotationPrefix() + "/cluster"));
    }

    private boolean noLastUsedTimeAnnotation(KafkaTopic topic) {
        ObjectMeta metadata = topic.getMetadata();
        Map<String, String> annotations = metadata.getAnnotations();
        return annotations == null ||
               !annotations.containsKey(ANNOTATION_KEY_TIMESTAMP) ||
               !annotations.containsKey(ANNOTATION_KEY_BYTESIN) ||
               !annotations.containsKey(ANNOTATION_KEY_BYTESOUT);
    }


    // ------------------------------------------------------------------------


    private void submitAnnotations(MixedOperation<T, L, Resource<T>> kafkaTopicClient,
                                   T topic,
                                   UsageUpdate updateInfo)
    {
        log.debug("updating {}", topic.getStatus().getTopicName());
        kafkaTopicClient
            .inNamespace(config.getNamespace())
            .withName(topic.getMetadata().getName())
            .patch(addAnnotations(topic, updateInfo));
    }

    private T addAnnotations(T topic, UsageUpdate update) {
        ObjectMeta metadata = topic.getMetadata();
        Map<String, String> annotations = metadata.getAnnotations();
        if (annotations == null) {
            annotations = new HashMap<>();
        }
        annotations.put(ANNOTATION_KEY_BYTESIN,   Long.toString(update.getLastBytesIn()));
        annotations.put(ANNOTATION_KEY_BYTESOUT,  Long.toString(update.getLastBytesOut()));
        annotations.put(ANNOTATION_KEY_TIMESTAMP, Formats.convertTimestamp(update.getTimestamp()));

        metadata.setAnnotations(annotations);
        topic.setMetadata(metadata);
        return topic;
    }

    private UsageUpdate createUpdate(T topic) throws MonitoringException {
        ObjectMeta metadata = topic.getMetadata();
        Map<String, String> annotations = metadata.getAnnotations();
        try {
            return new UsageUpdate(
                topic.getStatus().getTopicName(),
                Long.parseLong(annotations.get(ANNOTATION_KEY_BYTESIN)),
                Long.parseLong(annotations.get(ANNOTATION_KEY_BYTESOUT)),
                Formats.convertTimestamp(annotations.get(ANNOTATION_KEY_TIMESTAMP)));
        }
        catch (NumberFormatException nfe) {
            // return null;
            throw new MonitoringException("Invalid annotations");
        }
    }
}
