[![Build Status](https://travis-ci.org/spalarus/osgi-sodeac-streampartitioner.svg?branch=master)](https://travis-ci.org/spalarus/osgi-sodeac-streampartitioner)
# Stream Partitioner
An OSGi-service splits streams (java.io.InputStream and java.io.OutputStream) in substreams.


## Maven

```xml
<dependency>
  <groupId>org.sodeac</groupId>
  <artifactId>org.sodeac.streampartitioner.api</artifactId>
  <version>1.0.0</version>
</dependency>
<dependency>
  <groupId>org.sodeac</groupId>
  <artifactId>org.sodeac.streampartitioner.provider</artifactId>
  <version>1.0.1</version>
</dependency>
```

## Install to local m2-Repository

```
mvn org.apache.maven.plugins:maven-dependency-plugin:2.8:get -Dartifact="org.sodeac:org.sodeac.streampartitioner.provider:1.0.1"
```

## Install to Apache Karaf / Apache ServiceMix

```
bundle:install -s mvn:org.sodeac/org.sodeac.streampartitioner.api/1.0.0
bundle:install -s mvn:org.sodeac/org.sodeac.streampartitioner.provider/1.0.1
```

## OSGi-Dependencies

Bundle-RequiredExecutionEnvironment: JavaSE-1.8

org.osgi.framework;version="[1.8,2)"<br>
org.osgi.service.component;version="[1.3,2)"<br>
org.osgi.service.log;version="[1.3,2)"<br>

## Getting Started

Inject [StreamPartitionerFactory](https://oss.sonatype.org/service/local/repositories/releases/archive/org/sodeac/org.sodeac.streampartitioner.api/1.0.0/org.sodeac.streampartitioner.api-1.0.0-javadoc.jar/!/org/sodeac/streampartitioner/api/IStreamPartitionerFactory.html) into your component.

```java
@Reference
private volatile IStreamPartitionerFactory streamPartitionerFactory = null;
```

### Example: write 3+1 substreams into one file ...

```java
IOutputStreamPartitioner outputStreamPartitioner = streamPartitionerFactory.newOutputStreamPartitioner(filetOutputStream);

OutputStream subtream = outputStreamPartitioner.createNextSubOutputStream();
subtream.write(new String("first very short substream").getBytes());
subtream.close();

subtream = outputStreamPartitioner.createNextSubOutputStream();
subtream.write(new String("second very short substream").getBytes());
subtream.close();

subtream = outputStreamPartitioner.createNextSubOutputStream();
subtream.close();

subtream = outputStreamPartitioner.createNextSubOutputStream();
subtream.write(new String("third very short substream").getBytes());
subtream.close();

filetOutputStream.close();
```

### ... read again from file and print out every substream in one line 

```java
IInputStreamPartitioner inputStreamPartitioner = streamPartitionerFactory.newInputStreamPartitioner(fileInputStream);

InputStream inputStream;
int len;
byte[] readBuffer = new byte[1024];

while((inputStream = inputStreamPartitioner.getNextSubInputStream()) != null)
{
	System.out.print("substream: ");
	while((len = inputStream.read(readBuffer)) > 0)
	{
		System.out.print(new String(readBuffer,0,len));
	}
	System.out.println();
	inputStream.close();
}
fileInputStream.close();
```

### StdOut:

```
substream: first very short substream
substream: second very short substream
substream: 
substream: third very short substream
```

## Functional principle

The StreamPartitioner seperates substreams with unique markers. A unique marker is a dynamic delimiter, so it's possible to encapsulate in a substream further sub streams.
![](https://spalarus.github.io/images/StreamPartitionerUsage.svg)
## License
[Eclipse Public License 1.0](https://github.com/spalarus/osgi-sodeac-streampartitioner/blob/master/LICENSE)

