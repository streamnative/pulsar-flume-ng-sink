### Flume-Ng-Pulsar-Sink
This is a [Flume](https://github.com/apache/flume) Sink implementation that can publish data to a [Pulsar](https://github.com/apache/pulsar) topic

### Tested version

* Tested using Apache Flume NG 1.9.0
* Tested using Apache Pulsar 2.3.0

### Configurations

|Name|Description|Default|
|---|---|---|
|useAvroEventFormat|  Whether use avro format for event |false|

#### client 

|Name|Description|Default|
|---|---|---|
|serviceUrl| Whether non-persistent topics are enabled on the broker |localhost:6650|
|authPluginClassName| name of the Authentication-Plugin you want to use |""|
|authParamsString| string which represents parameters for the Authentication-Plugin, e.g., "key1:val1,key2:val2" |""|
|tlsCertFile| path of tls cert file |""|
|tlsKeyFile| path of tls key file |""|
|useTLS| Whether to turn on TLS, if to start, use protocol pulsar+ssl |false|
|operationTimeout| Set the operation timeout (default: 30 seconds) |30s|
|numIoThreads| Set the number of threads to be used for handling connections to brokers |1|
|numListenerThreads| Set the number of threads to be used for message listeners |1|
|connectionsPerBroker| Sets the max number of connection that the client library will open to a single broker. |1|
|enableTcpNoDelay| Configure whether to use TCP no-delay flag on the connection, to disable Nagle algorithm. |false|
|tlsTrustCertsFilePath| Set the path to the trusted TLS certificate file |false|
|allowTlsInsecureConnection| Configure whether the Pulsar client accept untrusted TLS certificate from broker |false|
|enableTlsHostnameVerification| whether to enable TLS hostname verification |false|
|statsInterval| the interval between each stat info |60|
|maxConcurrentLookupRequests| Number of concurrent lookup-requests allowed to send on each broker-connection to prevent overload on broker. |60|
|maxLookupRequests| Number of max lookup-requests allowed on each broker-connection to prevent overload on broker. |60|
|maxNumberOfRejectedRequestPerConnection| Set max number of broker-rejected requests in a certain time-frame (30 seconds) after which current connection will be closed and client creates a new connection that give chance to connect a different broker |50|
|keepAliveIntervalSeconds| Set keep alive interval in seconds for each client-broker-connection. |30|
|connectionTimeout| Set the duration of time to wait for a connection to a broker to be established. |30|

#### producer
|Name|Description|Default|
|---|---|---|
|topicName| Specify the topic this producer will be publishing on. |""|
|producerName| Specify a name for the producer |""|
|sendTimeout| Set the send timeout |30s|
|blockIfQueueFull| Set whether the send and sendAsync operations should block when the outgoing message queue is full. |false|
|enableBatching| Control whether automatic batching of messages is enabled for the producer |true|
|batchMessagesMaxMessagesPerBatch| maximum number of messages in a batch |1000|
|batchDelay| the batch delay |1ms|
|messageRoutingMode| the message routing mode, SinglePartition,RoundRobinPartition, CustomPartition(0,1,2) |1|
|hashingSchema| JavaStringHash,Murmur3_32Hash(0,1) |0|
|compressionType| NONE,LZ4,ZLIB,ZSTD(0,1,2,3) |0|


### Usage example in Docker

#### start pulsar service
```$xslt
docker pull apachepulsar/pulsar:2.3.0
docker run -d -it -p 6650:6650 -p 8080:8080 -v $PWD/data:/pulsar/data --name pulsar-flume-standalone apachepulsar/pulsar:2.3.0 bin/pulsar standalone
```

#### start consumer script pulsar-flume.py

```$xslt
import pulsar

client = pulsar.Client('pulsar://localhost:6650')
consumer = client.subscribe('test',
                            subscription_name='testProducer')

while True:
    msg = consumer.receive()
    print("Received message: '%s'" % msg.data())
    consumer.acknowledge(msg)

client.close()
```

```$xslt
docker cp pulsar-flume.py pulsar-flume-standalone:/pulsar
docker exec -it pulsar-flume-standalone /bin/bash
python pulsar-flume.py
```

### install and set up flume 

### setting up

#### init java and maven environment
```$xslt
docker pull maven:3.6-jdk-8
docker run -d -it --link pulsar-flume-standalone -p 44445:44445 --name flume
```

#### download and build
```$xslt
docker exec -it flume /bin/bash
git clone https://github.com/AmateurEvents/flume-ng-pulsar-sink
cd flume-ng-pulsar-sink
mvn clean package
cd ..
wget http://apache.01link.hk/flume/1.9.0/apache-flume-1.9.0-bin.tar.gz
tar -zxvf apache-flume-1.9.0-bin.tar.gz
cp flume-ng-pulsar-sink/target/flume-ng-pulsar-sink-1.9.0.jar apache-flume-1.9.0-bin/lib/
```

#### set up flume

##### copy flume-example.conf and config file to flume conf

###### flume-example.conf
```$xslt
# example.conf: A single-node Flume configuration

# Name the components on this agent
a1.sources = r1
a1.sinks = k1
a1.channels = c1

# Describe/configure the source
a1.sources.r1.type = netcat
a1.sources.r1.bind = localhost
a1.sources.r1.port = 44445


## Describe the sink
a1.sinks.k1.type = org.apache.flume.sink.pulsar.PulsarSink
a1.sinks.k1.serviceUrl = 127.0.0.1:6650
a1.sinks.k1.topicName = test
a1.sinks.k1.producerName = testProducer

# Use a channel which buffers events in memory
a1.channels.c1.type = memory
a1.channels.c1.capacity = 1000
a1.channels.c1.transactionCapacity = 1000

# Bind the source and sink to the channel
a1.sources.r1.channels = c1
a1.sinks.k1.channel = c1
```

###### flume-env.sh
```$xslt
export JAVA_HOME=/docker-java-home

FLUME_CLASSPATH=/docker-java-home/lib
```

```$xslt
docker cp flume-example.conf flume:/apache-flume-1.9.0-bin/conf/
docker cp flume-env.sh flume:/apache-flume-1.9.0-bin/conf/
```


##### start flume ng agent

```$xslt
docker exec -it flume /bin/bash
apache-flume-1.9.0-bin/bin/flume-ng agent --conf apache-flume-1.9.0-bin/conf/ -f apache-flume-1.9.0-bin/conf/flume-example.conf -n a1
```

##### test
```$xslt
telnet localhost 44445
```

### Configuration Options