/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.flume.sink.pulsar;

import com.google.common.base.Optional;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.apache.flume.*;
import org.apache.flume.conf.Configurable;
import org.apache.flume.conf.BatchSizeSupported;
import org.apache.flume.instrumentation.SinkCounter;
import org.apache.flume.sink.AbstractSink;
import org.apache.flume.source.avro.AvroFlumeEvent;
import org.apache.pulsar.client.api.*;
import org.apache.pulsar.client.impl.auth.AuthenticationTls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.apache.flume.source.SpoolDirectorySourceConfigurationConstants.BATCH_SIZE;


public class PulsarSink extends AbstractSink implements Configurable, BatchSizeSupported {
    private static final Logger log = LoggerFactory.getLogger(PulsarSink.class);

    private long batchSize;

    private boolean useAvroEventFormat;

    private SinkCounter counter = null;

    private Producer<byte[]> producer;

    private PulsarClient client;

    private Optional<ByteArrayOutputStream> tempOutStream = Optional.absent();

    private Optional<SpecificDatumWriter<AvroFlumeEvent>> writer = Optional.absent();

    private ProducerBuilder producerBuilder;

    private ClientBuilder clientBuilder;

    private String authPluginClassName;

    private String authParamsString;

    private String tlsCertFile;

    private String tlsKeyFile;

    private Boolean useTLS;

    private Integer operationTimeout;

    private Integer numIoThreads;

    private Integer connectionsPerBroker;

    private Map<String, Object> config = new HashMap<>();

    private String serviceUrl;

    private String topicName;

    private String producerName;

    private Integer sendTimeout;

    private Boolean blockIfQueueFull;

    private Boolean enableBatching;

    private Integer batchMessagesMaxMessagesPerBatch;

    private Long batchDelay;

    private Integer messageRoutingMode;

    private Integer hashingScheme;

    private Integer compressionType;

    private Boolean enableTcpNoDelay;

    private String tlsTrustCertsFilePath;

    private Boolean allowTlsInsecureConnection;

    private Boolean enableTlsHostnameVerification;

    private Integer statsInterval;

    private Integer maxConcurrentLookupRequests;

    private Integer maxLookupRequests;

    private Integer maxNumberOfRejectedRequestPerConnection;

    private Integer keepAliveIntervalSeconds;

    private Integer connectionTimeout;

    private Integer numListenerThreads;

    private Boolean syncMode;

    //Fine to use null for initial value, Avro will create new ones if this
    // is null
    private BinaryEncoder encoder = null;

    @Override
    public void configure(Context context) {
        batchSize = context.getInteger(BATCH_SIZE, 1000);
        useAvroEventFormat = context.getBoolean("useAvroEventFormat", false);

        // client options
        serviceUrl = context.getString("serviceUrl", "localhost:6650");
        authPluginClassName = context.getString("authPluginClassName", "");
        authParamsString = context.getString("authParamsString", "");
        tlsCertFile = context.getString("tlsCertFile", "");
        tlsKeyFile = context.getString("tlsKeyFile", "");
        useTLS = context.getBoolean("useTLS", false);
        operationTimeout = context.getInteger("operationTimeout", 0);
        numIoThreads = context.getInteger("numIoThreads", 0);
        connectionsPerBroker = context.getInteger("connectionsPerBroker", 0);
        enableTcpNoDelay = context.getBoolean("enableTcpNoDelay", false);
        tlsTrustCertsFilePath = context.getString("tlsTrustCertsFilePath", "");
        allowTlsInsecureConnection = context.getBoolean("allowTlsInsecureConnection", false);
        enableTlsHostnameVerification = context.getBoolean("enableTlsHostnameVerification", false);
        statsInterval = context.getInteger("statsInterval", 0);
        maxConcurrentLookupRequests = context.getInteger("maxConcurrentLookupRequests", 0);
        maxLookupRequests = context.getInteger("maxLookupRequests", 0);
        maxNumberOfRejectedRequestPerConnection = context.getInteger("maxNumberOfRejectedRequestPerConnection", 0);
        keepAliveIntervalSeconds = context.getInteger("keepAliveIntervalSeconds", 0);
        connectionTimeout = context.getInteger("connectionTimeout", 0);
        numListenerThreads = context.getInteger("numListenerThreads", 0);

        // producer options
        topicName = context.getString("topicName", "");
        producerName = context.getString("producerName", "");
        sendTimeout = context.getInteger("sendTimeout", 10);
        blockIfQueueFull = context.getBoolean("blockIfQueueFull", false);
        enableBatching = context.getBoolean("enableBatching", false);
        batchMessagesMaxMessagesPerBatch = context.getInteger("batchMessagesMaxMessagesPerBatch", 1000);
        batchDelay = context.getLong("batchDelay", 0L);
        messageRoutingMode = context.getInteger("messageRoutingMode", -1);
        hashingScheme = context.getInteger("hashingSchema", -1);
        compressionType = context.getInteger("compressionType", -1);

        // message options
        syncMode = context.getBoolean("syncMode", true);

    }

    @Override
    public long getBatchSize() {
        return batchSize;
    }


    @Override
    public Status process() throws EventDeliveryException {
        Status result = Status.READY;
        Channel channel = getChannel();
        Transaction transaction = null;
        Event event = null;

        try {
            transaction = channel.getTransaction();
            transaction.begin();
            long processedEvents = 0;
            for (; processedEvents < batchSize; processedEvents += 1) {
                event = channel.take();

                if (event == null) {
                    // no events available in the channel
                    break;
                }
                if (processedEvents == 0) {
                    result = Status.BACKOFF;
                    counter.incrementBatchEmptyCount();
                } else if (processedEvents < batchSize) {
                    counter.incrementBatchUnderflowCount();
                } else {
                    counter.incrementBatchCompleteCount();
                }
                TypedMessageBuilder<byte[]> newMessage = producer.newMessage();
                if (event.getHeaders() != null) {
                    if (event.getHeaders().get("key") != null) {
                        newMessage = newMessage.key(event.getHeaders().get("key"));
                    }
                    newMessage.value(serializeEvent(event, useAvroEventFormat)).properties(event.getHeaders());
                } else {
                    newMessage.value(serializeEvent(event, useAvroEventFormat));
                }
                if (syncMode) {
                    newMessage.send();
                } else {
                    newMessage.sendAsync();
                }
            }
            if (!syncMode) {
                producer.flush();
            }
            transaction.commit();
        } catch (Exception ex) {
            log.error("Failed to publish events", ex);
            counter.incrementEventWriteOrChannelFail(ex);
            result = Status.BACKOFF;
            if (transaction != null) {
                try {
                    // If the transaction wasn't committed before we got the exception, we
                    // need to rollback.
                    transaction.rollback();
                } catch (RuntimeException e) {
                    log.error("Transaction rollback failed: " + e.getLocalizedMessage());
                    log.debug("Exception follows.", e);
                } finally {
                    transaction.close();
                    transaction = null;
                }
            }
        } finally {
            if (transaction != null) {
                transaction.close();
            }
        }
        return result;
    }

    @Override
    public synchronized void start() {
        try{
            log.info("start pulsar producer");
            initPulsarClient();
            initPulsarProducer();
            this.counter = new SinkCounter("flume-sink");
            super.start();
        } catch (Exception e) {
            log.error("init pulsar failed:{}", e.getMessage());
        }

    }

    @Override
    public synchronized void stop() {
        try{
            log.info("stop pulsar producer");
            producer.close();
            client.close();
        } catch (Exception e) {
            log.error("stop pulsar failed");
        }
        super.stop();
    }

    private void initPulsarClient() {
        try {
            clientBuilder = PulsarClient.builder();
            if (authPluginClassName.length() > 0 && authParamsString.length() > 0) {
                clientBuilder.authentication(authPluginClassName, authPluginClassName);
            }
            if (useTLS) {
                clientBuilder.serviceUrl("pulsar://+ssl" + serviceUrl);
            } else {
                clientBuilder.serviceUrl("pulsar://" + serviceUrl);
            }
            if (tlsCertFile.length() > 0 && tlsKeyFile.length() > 0) {
                Map<String, String> authParams = new HashMap<>();
                authParams.put("tlsCertFile", tlsCertFile);
                authParams.put("tlsKeyFile", tlsKeyFile);
                Authentication tlsAuth = AuthenticationFactory
                        .create(AuthenticationTls.class.getName(), authParams);
                clientBuilder.authentication(tlsAuth);
            }
            if (operationTimeout > 0) {
                clientBuilder.operationTimeout(operationTimeout, TimeUnit.SECONDS);
            }
            if (numIoThreads > 0) {
                clientBuilder.ioThreads(numIoThreads);
            }
            if (numListenerThreads > 0) {
                clientBuilder.listenerThreads(numListenerThreads);
            }
            if (connectionsPerBroker > 0) {
                clientBuilder.connectionsPerBroker(connectionsPerBroker);
            }
            if (enableTcpNoDelay) {
                clientBuilder.enableTcpNoDelay(enableTcpNoDelay);
            }
            if (tlsTrustCertsFilePath.length() > 0) {
                clientBuilder.tlsTrustCertsFilePath(tlsTrustCertsFilePath);
            }
            if (allowTlsInsecureConnection) {
                clientBuilder.allowTlsInsecureConnection(allowTlsInsecureConnection);
            }
            if (enableTlsHostnameVerification) {
                clientBuilder.enableTlsHostnameVerification(enableTlsHostnameVerification);
            }
            if (statsInterval > 0) {
                clientBuilder.statsInterval(statsInterval, TimeUnit.SECONDS);
            }
            if (maxConcurrentLookupRequests > 0) {
                clientBuilder.maxConcurrentLookupRequests(maxConcurrentLookupRequests);
            }
            if (maxLookupRequests > 0) {
                clientBuilder.maxLookupRequests(maxLookupRequests);
            }
            if (maxNumberOfRejectedRequestPerConnection > 0) {
                clientBuilder.maxNumberOfRejectedRequestPerConnection(maxNumberOfRejectedRequestPerConnection);
            }
            if (keepAliveIntervalSeconds > 0) {
                clientBuilder.keepAliveInterval(keepAliveIntervalSeconds, TimeUnit.SECONDS);
            }
            if (connectionTimeout > 0) {
                clientBuilder.connectionTimeout(connectionTimeout, TimeUnit.SECONDS);
            }
            client = clientBuilder.build();
        } catch(Exception e) {
            log.error("init pulsar client failed:{}", e.getMessage());
        }
    }

    private void initPulsarProducer() {
        try {
            producerBuilder = client.newProducer();
            if(topicName.length() > 0) {
                producerBuilder = producerBuilder.topic(topicName);
            }
            if (producerName.length() > 0) {
                producerBuilder = producerBuilder.producerName(producerName);
            }
            if (sendTimeout > 0) {
                producerBuilder.sendTimeout(sendTimeout, TimeUnit.SECONDS);
            } else {
                producerBuilder.sendTimeout(10, TimeUnit.SECONDS);
            }
            if (blockIfQueueFull) {
                producerBuilder.blockIfQueueFull(blockIfQueueFull);
            }
            if (enableBatching) {
                producerBuilder.enableBatching(enableBatching);
            }
            if (batchMessagesMaxMessagesPerBatch > 0){
                producerBuilder.batchingMaxMessages(batchMessagesMaxMessagesPerBatch);
            }
            if (batchDelay > 0) {
                producerBuilder.batchingMaxPublishDelay(batchDelay, TimeUnit.MILLISECONDS);
            }
            if (MessageRoutingMode.SinglePartition.equals(messageRoutingMode)) {
                producerBuilder.messageRoutingMode(MessageRoutingMode.SinglePartition);
            } else if (MessageRoutingMode.CustomPartition.equals(messageRoutingMode)) {
                producerBuilder.messageRoutingMode(MessageRoutingMode.CustomPartition);
            } else if (MessageRoutingMode.RoundRobinPartition.equals(messageRoutingMode)) {
                producerBuilder.messageRoutingMode(MessageRoutingMode.RoundRobinPartition);
            }
            if (HashingScheme.JavaStringHash.equals(hashingScheme)) {
                producerBuilder.hashingScheme(HashingScheme.JavaStringHash);
            } else if (HashingScheme.Murmur3_32Hash.equals(hashingScheme)) {
                producerBuilder.hashingScheme(HashingScheme.Murmur3_32Hash);
            }
            if (CompressionType.LZ4.equals(compressionType)) {
                producerBuilder.compressionType(CompressionType.LZ4);
            } else if (CompressionType.ZLIB.equals(compressionType)) {
                producerBuilder.compressionType(CompressionType.ZLIB);
            } else if (CompressionType.ZSTD.equals(compressionType)) {
                producerBuilder.compressionType(CompressionType.ZSTD);
            } else if (CompressionType.NONE.equals(compressionType)) {
                producerBuilder.compressionType(CompressionType.NONE);
            }
            producer = producerBuilder.create();
        } catch (Exception e) {
            log.error("init pulsar producer failed:{}", e.getMessage());
        }
    }

    private byte[] serializeEvent(Event event, boolean useAvroEventFormat) throws IOException {
        byte[] bytes;
        if (useAvroEventFormat) {
            if (!tempOutStream.isPresent()) {
                tempOutStream = Optional.of(new ByteArrayOutputStream());
            }
            if (!writer.isPresent()) {
                writer = Optional.of(new SpecificDatumWriter<AvroFlumeEvent>(AvroFlumeEvent.class));
            }
            tempOutStream.get().reset();
            AvroFlumeEvent e = new AvroFlumeEvent(toCharSeqMap(event.getHeaders()),
                    ByteBuffer.wrap(event.getBody()));
            encoder = EncoderFactory.get().directBinaryEncoder(tempOutStream.get(), encoder);
            writer.get().write(e, encoder);
            encoder.flush();
            bytes = tempOutStream.get().toByteArray();
        } else {
            bytes = event.getBody();
        }
        return bytes;
    }

    private static Map<CharSequence, CharSequence> toCharSeqMap(Map<String, String> stringMap) {
        Map<CharSequence, CharSequence> charSeqMap = new HashMap<CharSequence, CharSequence>();
        for (Map.Entry<String, String> entry : stringMap.entrySet()) {
            charSeqMap.put(entry.getKey(), entry.getValue());
        }
        return charSeqMap;
    }
}
