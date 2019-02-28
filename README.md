### Flume-Ng-Pulsar-Sink
This is a [Flume](https://github.com/apache/flume) Sink implementation that can publish data to a [Pulsar](https://github.com/apache/pulsar) topic

### Tested version

* Tested using Apache Flume NG 1.9.0
* Tested using Apache Pulsar 2.3.0

### Build the project

```$xslt
git clone https://github.com/AmateurEvents/flume-ng-pulsar-sink
mvn clean package
```

### Setting up

#### download
```$xslt
wget http://apache.01link.hk/flume/1.9.0/apache-flume-1.9.0-bin.tar.gz
tar -zxvf apache-flume-1.9.0-bin.tar.gz
cp flume-ng-pulsar-sink/target/flume-ng-pulsar-sink-1.9.0.jar apache-flume-1.9.0-bin/lib/
```

```$xslt
wget http://mirror-hk.koddos.net/apache/pulsar/pulsar-2.3.0/apache-pulsar-2.3.0-bin.tar.gz
tar -zxvf apache-pulsar-2.3.0-bin.tar.gz
```

#### start pulsar service

```$xslt
apache-pulsar-2.3.0-bin/bin/pulsar standalone
```

#### configuration and start flume ng agent

##### flume-example.conf

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

##### start flume ng agent

```$xslt
apache-flume-1.9.0-bin/bin/flume-ng agent --conf conf/ -f apache-flume-1.9.0-bin/conf/flume-example.conf -n a1
```

##### test
```$xslt
telnet localhost 44445
```