# SMB NIO.2
This is a Java NIO.2 file system provider that can be used to access CIFS/SMB file systems. CIFS is the standard file sharing protocol on the Microsoft Windows platform (e.g. to map a network drive). 

This library uses [jCIFS](https://jcifs.samba.org/) internally, which is an Open Source client library that implements the CIFS/SMB networking protocol in 100% Java. 

# Dependencies
This library requires the [jCIFS](https://jcifs.samba.org/) library as a dependency.

# How to use
The easiest way to use the library is to add it as a Maven dependency to your project. Just add the following entry to your pom.xml file.

```xml
<dependencies>
  <dependency>
    <groupId>ch.pontius.nio</groupId>
    <artifactId>smb-nio</artifactId>
    <version>0.5-RELEASE</version>
  </dependency>
</dependencies>
```

Once you have added the dependency, you should be able to create SMBPath objects by using the Paths#get(URI) method of the Java NIO.2 library. The URI must specify the 'smb://' scheme.

# Issues
Please report issues using the GitHub issue tracker.
