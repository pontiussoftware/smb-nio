# SMB NIO Next Generation

This is a Java NIO.2 file system provider that can be used to access CIFS/SMB file systems. CIFS is the standard file sharing protocol on the Microsoft Windows platform (e.g. to map a network drive). 

The library uses [jcifs-ng](https://github.com/AgNO3/jcifs-ng) internally which is an Open Source client library that implements the CIFS/SMB networking protocol in 100% Java.

This project is a fork of [smb-nio](https://github.com/pontiussoftware/smb-nio) which uses ``jcifs-ng`` instead of ``jcifs`` to support SMB version 2, because 
version 1 has serious security issues.

# How to use

The easiest way to use the library is to add it as a Maven dependency to your project. Just add the following entry to your pom.xml file.

```xml
<dependency>
    <groupId>com.github.jfrommann</groupId>
    <artifactId>smb-nio-ng</artifactId>
    <version>0.2.0</version>
</dependency>
```

This is how to build it yourself using maven:

```bash
mvn install
```

After building and installing the library to your local maven repository you can include it to your maven project as a dependency as shown above.

Once you have added the dependency you should be able to create SMBPath objects by using the Paths#get(URI) method of the ``smb-nio-ng`` library. 
The URI must specify the ``smb://`` scheme.

# Issues
Please report issues using the GitHub issue tracker.
