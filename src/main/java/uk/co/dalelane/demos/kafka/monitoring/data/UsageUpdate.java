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
package uk.co.dalelane.demos.kafka.monitoring.data;

import java.time.Instant;

public class UsageUpdate {

    private final String topicName;
    private Long lastBytesIn;
    private Long lastBytesOut;
    private Instant timestamp;

    public UsageUpdate(String topicName) {
        this.topicName = topicName;
        this.lastBytesIn = 0L;
        this.lastBytesOut = 0L;
        this.timestamp = Instant.ofEpochMilli(0L);
    }

    public UsageUpdate(String topicName, Long lastBytesIn, Long lastBytesOut, Instant timestamp) {
        this.topicName = topicName;
        this.lastBytesIn = lastBytesIn;
        this.lastBytesOut = lastBytesOut;
        this.timestamp = timestamp;
    }

    public String getTopicName() {
        return topicName;
    }

    public Long getLastBytesIn() {
        return lastBytesIn;
    }

    public Long getLastBytesOut() {
        return lastBytesOut;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setLastBytesIn(Long lastBytesIn, Instant timestamp) {
        this.lastBytesIn = lastBytesIn;

        if (timestamp.isAfter(this.timestamp)) {
            this.timestamp = timestamp;
        }
    }

    public void setLastBytesOut(Long lastBytesOut, Instant timestamp) {
        this.lastBytesOut = lastBytesOut;

        if (timestamp.isAfter(this.timestamp)) {
            this.timestamp = timestamp;
        }
    }

    @Override
    public String toString() {
        return "UsageUpdate [topicName=" + topicName + ", lastBytesIn=" + lastBytesIn + ", lastBytesOut=" + lastBytesOut
                + ", timestamp=" + timestamp + "]";
    }
}
