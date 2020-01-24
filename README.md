# SMB NIO Next Generation

This is a Java NIO.2 file system provider that can be used to access CIFS/SMB file systems. CIFS is the standard file sharing protocol on the Microsoft Windows 
platform (e.g. to map a network drive). 

The library uses [jcifs-ng](https://github.com/AgNO3/jcifs-ng) internally which is an Open Source client library that implements the CIFS/SMB networking 
protocol in 100% Java.

This project is a fork of [smb-nio](https://github.com/pontiussoftware/smb-nio) which uses ``jcifs-ng`` instead of ``jcifs`` to support SMB version 2, because 
version 1 has serious security issues and often is not supported anymore by file servers. Also this project implements a WatchService which is missing in the 
smb-nio library.

# How to use

## Maven dependency
The easiest way to use the library is to add it as a Maven dependency to your project. Just add the following entry to your pom.xml file.

```xml
<dependency>
    <groupId>com.github.jfrommann</groupId>
    <artifactId>smb-nio-ng</artifactId>
    <version>0.2.0</version>
</dependency>
```

## Build from source
This is how to build it yourself using maven:

```bash
mvn install
```

After building and installing the library to your local maven repository you can include it to your maven project as a dependency as shown above.

## Usage
Once you have added the dependency you should be able to create SMBPath objects by using the Paths#get(URI) method of the ``smb-nio-ng`` library. 
The URI must specify the ``smb://`` scheme.

```java
Path dir = Paths.get(new URI("smb://username:password@server/share/dir/"));
```         

Another way is to use the ``SmbFileSystemProvider`` to create a ``SmbFileSystem`` and use this for path cretation. This way your are able to pass environment 
properties and it is the only way to enable the ``SmbWatchService``.

```java  
Map<String, Object> environment = new HashMap<>(); 
environment.put(SmbFileSystemProvider.PROPERTY_KEY_USERNAME, "username");
environment.put(SmbFileSystemProvider.PROPERTY_KEY_PASSWORD, "password"); 
environment.put(SmbFileSystemProvider.PROPERTY_KEY_WATCHSERVICE_ENABLED, "true");

SmbFileSystem fileSystem = SmbFileSystemProvider.getDefault().newFileSystem(URI.create("smb://server), environment);
Path dir = fileSystem.getPath("/share", "dir/");
```   

**Hint:** URIs of directories must end with a slash (``/``), this is a requirement of ``jcifs-ng``.

# Issues
Please report issues using the GitHub issue tracker.
