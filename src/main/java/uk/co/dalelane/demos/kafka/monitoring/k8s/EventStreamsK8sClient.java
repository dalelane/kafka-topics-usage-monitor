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

import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import uk.co.dalelane.demos.kafka.monitoring.k8s.objects.EventStreamsTopic;
import uk.co.dalelane.demos.kafka.monitoring.k8s.objects.EventStreamsTopicsList;
import uk.co.dalelane.demos.kafka.monitoring.utils.Config;

public class EventStreamsK8sClient extends K8sClient<EventStreamsTopic, EventStreamsTopicsList> {

    public EventStreamsK8sClient(Config config) {
        super(config);
    }

    @Override
    public MixedOperation<EventStreamsTopic, EventStreamsTopicsList, Resource<EventStreamsTopic>> createResourceClient() {
        return client.resources(EventStreamsTopic.class, EventStreamsTopicsList.class);
    }
}
