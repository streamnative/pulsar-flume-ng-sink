# Flume Ng Pulsar Sink

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Build Status](https://travis-ci.org/streamnative/flume-ng-pulsar-sink.svg?branch=master)](https://travis-ci.org/streamnative/flume-ng-pulsar-sink)


This is a [Flume](https://github.com/apache/flume) Sink implementation that can publish data to a [Pulsar](https://github.com/apache/pulsar) topic

## Compatibility

This sink is developed and tested using Apache Flume NG 1.9.0 and Apache Pulsar Client 2.3.0.

### Requirements

- [Docker](https://docs.docker.com/docker-for-mac/install/)

### Clone the project

```bash
$ git clone https://github.com/streamnative/flume-ng-pulsar-sink.git
```

### Start Pulsar Standalone

```$xslt
docker pull apachepulsar/pulsar:2.3.0
docker run -d -it -p 6650:6650 -p 8080:8080 -v $PWD/data:/pulsar/data --name pulsar-flume-standalone apachepulsar/pulsar:2.3.0 bin/pulsar standalone
```

### Start Pulsar Consumer

Start a [consumer](src/test/python/pulsar-flume.py) to consume messages from topic `flume-test-topic`.

```$xslt
docker cp src/test/python/pulsar-flume.py pulsar-flume-standalone:/pulsar
docker exec -it pulsar-flume-standalone /bin/bash
python pulsar-flume.py
```

### Setup up Flume

#### Prepare Build Environment

Open a new terminal to start a docker instance `flume` of `maven:3.6-jdk-8` in the same network as `pulsar-flume-standalone`
we started at previous step. We will use this `flume` docker instace to install Flume and Flume-Ng-Pulsar-Sink.

```$xslt
docker pull maven:3.6-jdk-8
docker run -d -it --link pulsar-flume-standalone -p 44445:44445 --name flume maven:3.6-jdk-8 /bin/bash
```

#### Install Flume

Go to the docker instance `flume`

```$xslt
docker exec -it flume /bin/bash
```

At `flume` instance:

```
wget http://apache.01link.hk/flume/1.9.0/apache-flume-1.9.0-bin.tar.gz
tar -zxvf apache-flume-1.9.0-bin.tar.gz
```

#### Install Pulsar Sink

At `flume` instance:

```$xslt
git clone https://github.com/streamnative/flume-ng-pulsar-sink
cd flume-ng-pulsar-sink
mvn clean package
cd ..
cp flume-ng-pulsar-sink/target/flume-ng-pulsar-sink-1.9.0.jar apache-flume-1.9.0-bin/lib/
exit
```

#### Configure Flume

Copy the example configurations to `flume`:

- [flume-example.conf](src/test/resources/flume-example.conf)
- [flume-env.sh](src/test/resources/flume-env.sh)

```$xslt
docker cp src/test/resources/flume-example.conf flume:/apache-flume-1.9.0-bin/conf/
docker cp src/test/resources/flume-env.sh flume:/apache-flume-1.9.0-bin/conf/
```

#### Start Flume Ng Agent

```$xslt
docker exec -it flume /bin/bash
```

At `flume` instance:

```$xslt
apache-flume-1.9.0-bin/bin/flume-ng agent --conf apache-flume-1.9.0-bin/conf/ -f apache-flume-1.9.0-bin/conf/flume-example.conf -n a1
```

### Send Data

Open another terminal, send data to port 44445 of flume

```$xslt
➜  ~ telnet localhost 44445
Trying ::1...
Connected to localhost.
Escape character is '^]'.
hello
OK
world
OK
```

At the terminal running `pulsar-consumer.py`, you will see following output:

```$xslt
'eceived message: 'hello
'eceived message: 'world
``` 

### Cleanup 

`flume` and `pulsar-flume-standalone` are running at background. Please remember to kill them at the end of this tutorial.

```bash
$ docker ps | grep pulsar-flume-standalone | awk '{ print $1 }' | xargs docker kill
$ docker ps | grep flume | awk '{ print $1 }' | xargs docker kill
```

## Installation

### Requirements

- JDK 1.8+
- Apache Maven 3.x

### Build from Source

Clone the project from Github:

```bash
$ git clone https://github.com/streamnative/flume-ng-pulsar-sink.git
```

Building the Flume Ng Sink using maven:

```bash
$ cd flume-ng-pulsar-sink
$ mvn clean package
```

Once it is built successfully, you will find a jar `flume-ng-pulsar-sink-<version>.jar` generated under `target` directory.
You can drop the built jar at your flume installation under `lib` directory.

## Usage

### Configurations

|Name|Description|Default|
|---|---|---|
|useAvroEventFormat|  Whether use avro format for event |false|
|syncMode|  Mode of send data to pulsar |true|

#### Client 

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

#### Producer
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

# License

This project is licensed under the [Apache License 2.0](LICENSE).

[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bgithub.com%2Fstreamnative%2Fflume-ng-pulsar-sink.svg?type=large)](https://app.fossa.io/projects/git%2Bgithub.com%2Fstreamnative%2Fflume-ng-pulsar-sink?ref=badge_large)

