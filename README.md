# SMB NIO.2

[![Maven Central](https://img.shields.io/maven-central/v/ch.pontius.nio/smb-nio.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22ch.pontius.nio%22%20AND%20a:%22smb-nio%22)

This is a Java NIO.2 file system provider that can be used to access CIFS/SMB 1.0 and 2.0 file systems. CIFS is the standard file sharing protocol on the Microsoft Windows platform (e.g. to map a network drive). 

This library uses [jcifs-ng](https://github.com/AgNO3/jcifs-ng/) internally, which is an Open Source client library that implements the CIFS/SMB networking protocol in 100% Java. 

# Dependencies
This library requires the [jcifs-ng](https://github.com/AgNO3/jcifs-ng/) library as a dependency.

# How to use
The easiest way to use the library is to add it as a Maven dependency to your project. Just add the following entry to your pom.xml file.

```xml
<dependencies>
  <dependency>
    <groupId>ch.pontius.nio</groupId>
    <artifactId>smb-nio</artifactId>
    <version>0.11.0</version>
  </dependency>
</dependencies>
```

Once you have added the dependency, you should be able to create SMBPath objects by using the Paths#get(URI) method of the Java NIO.2 library. The URI must be constructed using the 'smb://' scheme. If you want to use credentials, there are several ways to do so. You can either do so directly:

```java
final Path dir = Paths.get(new URI("smb://username:password@host/share/dir1/dir2/"));
```

Or you can manually create an SMB file system using the following snippet. In this case, the default credentials will always be used.

```java
final Map<String, Object> env = new HashMap<>(); 
env.put("jcifs.smb.client.username", "<username>");
env.put("jcifs.smb.client.password", "<password>"); 
env.put("jcifs.smb.client.domain", "<domain>");

SmbFileSystem fileSystem = SMBFileSystemProvider().newFileSystem(URI.create("smb://host), env);
Path dir = fileSystem.getPath("/dir1", "dir2/");
```

Last but not least it is also possible to set the aforementioned properties as global system properties when starting the JVM. A complete list of properties supported by [jcifs-ng](https://github.com/AgNO3/jcifs-ng/) can be found on their GitHub repository.

**Important:** Please note that paths to directories **MUST** end with a trailing slash due to requirements of jcifs-ng.

# Issues
Please report issues using the GitHub issue tracker.
