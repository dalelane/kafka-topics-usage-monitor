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

import java.time.Instant;

public class Formats {

    /** Returns a timestamp represented by the numerical value. */
    public static Instant parseTimestamp(double timestampSeconds) {
        long seconds = (long) timestampSeconds;
        long nanos = (long) ((timestampSeconds - seconds) * 1_000_000_000);

        return Instant.ofEpochSecond(seconds, nanos);
    }


    public static String convertTimestamp(Instant timestampInstant) {
        long timestampLong = timestampInstant.toEpochMilli();
        return Long.toString(timestampLong);
    }


    public static Instant convertTimestamp(String timestampString) {
        long timestampLong = Long.valueOf(timestampString);
        return Instant.ofEpochMilli(timestampLong);
    }
}
