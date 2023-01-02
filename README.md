IPC Socket
==========

IPC Socket is a Java wrapper around interprocess communication (IPC) using `java.net.ServerSocket` and `java.net.Socket` as the API.

On Unix-like systems, it uses Unix Domain Socket. The path is a filesystem path name.

On Windows, IPC is implemented using Named Pipe. The path must refer to an entry in `\\?\pipe\` or `\\.\pipe\`.

See unit tests for the details.

### Module ID

```scala
libraryDependencies += "org.scala-sbt.ipcsocket" % "ipcsocket" % "1.6.1"
```

### Examples

Check out the [examples directory](./examples).

### Why not just use TCP/IP?

TCP/IP is open to everyone on the network, if someone can get hold of your machine, the person could connect to it.
This raises security concerns for some usages (like build tools) since it could lead to arbitrary code execution if used without authentication.

### License

Apache v2

The server socket code was originally taken from Nailgun project, and client-side was added.
