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
package uk.co.dalelane.demos.kafka.monitoring.prometheus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import uk.co.dalelane.demos.kafka.monitoring.MonitoringException;
import uk.co.dalelane.demos.kafka.monitoring.utils.Config;
import uk.co.dalelane.demos.kafka.monitoring.utils.Formats;
import uk.co.dalelane.demos.kafka.monitoring.utils.Config.Mode;



public class PrometheusClient {

    private final Logger log = LoggerFactory.getLogger(PrometheusClient.class);

    private final static String BASE_PATH = "/api/v1";
    private final static String INSTANT_QUERY_PATH = BASE_PATH + "/query";

    private final static String PROMETHEUS_CA_PATH = "creds/prometheus-ca.crt";
    private final static String K8S_TOKEN_PATH = "creds/k8s-token";

    private final String clusterNameKey;

    private HttpClient client;

    private Config config;

    public PrometheusClient(Config config) throws MonitoringException {
        this.config = config;

        clusterNameKey = (config.getMode() == Mode.EVENTSTREAMS) ?
            "eventstreams_ibm_com_cluster" :
            "strimzi_io_cluster";

        try {
            log.debug("Creating HTTP client for Prometheus API");
            client = HttpClient
                .newBuilder()
                .sslContext(setupSslContext())
                .build();
        }
        catch (GeneralSecurityException | IOException e) {
            throw new MonitoringException("Failed to create Prometheus client", e);
        }
    }


    public List<PrometheusData> query(String query) throws MonitoringException {
        try {
            String url  = config.getPrometheusUrl() + INSTANT_QUERY_PATH + "?query=" + URLEncoder.encode(query, "UTF-8");
            log.debug("prometheus query {}", url);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + Files.readString(getToken()).trim())
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            String responseStr = response.body();
            log.debug(responseStr);
            JsonNode root = mapper.readTree(responseStr);

            return parse(root);
        }
        catch (JsonProcessingException e) {
            throw new MonitoringException("Failed to parse Prometheus API response", e);
        }
        catch (IOException | InterruptedException e) {
            throw new MonitoringException("Failed to submit Prometheus query", e);
        }
    }


    private List<PrometheusData> parse(JsonNode prometheusQueryResponse) throws MonitoringException {
        JsonNode results = prometheusQueryResponse.path("data").path("result");
        if (results.isArray()) {
            List<PrometheusData> parsedData = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                JsonNode result = results.get(i);

                JsonNode metricData = result.path("metric");
                if (isTopicInCorrectCluster(metricData) && isTopicInCorrectNamespace(metricData)) {
                    String topic = metricData.path("topic").asText();
                    JsonNode values = result.path("value");
                    Instant timestamp = Formats.parseTimestamp(values.get(0).asDouble());
                    Long totalbytes = values.get(1).asLong();

                    PrometheusData nextData = new PrometheusData(topic, timestamp, totalbytes);
                    log.debug("prometheus data {}", nextData);
                    parsedData.add(nextData);
                }
            }
            return parsedData;
        }
        else {
            throw new MonitoringException("Unexpected results");
        }
    }


    private boolean isTopicInCorrectCluster(JsonNode metricData) {
        return metricData.path(clusterNameKey).asText().equals(config.getCluster());
    }

    private boolean isTopicInCorrectNamespace(JsonNode metricData) {
        return metricData.path("namespace").asText().equals(config.getNamespace());
    }


    // ------------------------------------------------------------------------

    private static SSLContext setupSslContext() throws CertificateException, KeyStoreException, NoSuchAlgorithmException, IOException, KeyManagementException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        FileInputStream fis = new FileInputStream(PROMETHEUS_CA_PATH);
        X509Certificate caCert = (X509Certificate) cf.generateCertificate(fis);

        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null);
        ks.setCertificateEntry("prometheus-ca", caCert);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), null);
        return sslContext;
    }

    private Path getToken() throws FileNotFoundException {
        File runningInKubernetes = new File("/var/run/secrets/kubernetes.io/serviceaccount/token");
        File runningLocally = new File(K8S_TOKEN_PATH);

        if (runningInKubernetes.exists()) {
            return runningInKubernetes.toPath();
        }
        else if (runningLocally.exists()) {
            return runningLocally.toPath();
        }
        else {
            throw new FileNotFoundException("Kubernetes service token");
        }
    }
}