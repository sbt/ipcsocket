package org.scalasbt.ipcsocket;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

public class ServerSocketChannelsTest {
  private ServerSocketChannel serverChannel;
  private SocketChannel server;
  private SocketChannel client;
  private Path dir;
  private Path socketPath;

  @Before
  public void before() throws IOException, ReflectiveOperationException {
    dir = Files.createTempDirectory(ServerSocketChannelsTest.class.getSimpleName());
    socketPath = dir.resolve("socket");
    Files.deleteIfExists(socketPath);
    if (!Files.isDirectory(dir)) {
      Files.createDirectories(dir);
    }
    serverChannel =
        ServerSocketChannels.newUnixDomainSocket(socketPath.toFile().getAbsolutePath(), true);
    client = SocketChannel.open(StandardProtocolFamily.UNIX);
    client.connect(UnixDomainSocketAddress.of(socketPath.toFile().getAbsolutePath()));
    server = serverChannel.accept();
  }

  @After
  public void after() throws IOException {
    serverChannel.close();
    Files.deleteIfExists(socketPath);
    Files.deleteIfExists(dir);
  }

  private List<Byte> readAll(SocketChannel client) throws IOException {
    int res;
    final List<Byte> values = new ArrayList<>();
    do {
      final ByteBuffer buf = ByteBuffer.allocate(1);
      res = client.read(buf);
      if (res != -1) {
        values.add(buf.get(0));
      }
    } while (res != -1);
    return values;
  }

  private static final List<Integer> intValues;
  private static final List<Byte> byteValues;

  private static final byte[] byteArray() {
    final byte[] array = new byte[byteValues.size()];
    for (int i = 0; i < array.length; i++) {
      array[i] = byteValues.get(i);
    }
    return array;
  }

  static {
    final List<Integer> list =
        Stream.<Integer>iterate((int) Byte.MIN_VALUE, x -> x + 3)
            .limit(128)
            .collect(Collectors.toList());
    Collections.shuffle(list);
    intValues = Collections.unmodifiableList(list);
    byteValues =
        Collections.unmodifiableList(
            intValues.stream().map(Integer::byteValue).collect(Collectors.toList()));
  }

  @Test
  public void writeInt() throws Throwable {
    try {
      intValues.forEach(
          x -> {
            try {
              ByteBuffer bs = ByteBuffer.allocate(1);
              bs.put(Integer.valueOf(x).byteValue());
              bs.rewind();
              server.write(bs);
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
    } finally {
      server.close();
    }
    final List<Byte> actual = readAll(client);
    assertEquals(byteValues, actual);
  }

  @Test
  public void writeByteArray() throws Throwable {
    try {
      server.write(ByteBuffer.wrap(byteArray()));
    } finally {
      server.close();
    }
    final List<Byte> actual = readAll(client);
    assertEquals(byteValues, actual);
  }

  @Test
  public void writeByteArrayOffsetLength() throws Throwable {
    final int offset = 20;
    final int length = 30;
    final byte[] array = byteArray();
    try {
      ByteBuffer bs = ByteBuffer.allocate(30);
      bs.put(array, offset, length);
      bs.rewind();
      server.write(bs);
    } finally {
      server.close();
    }
    final List<Byte> actual = readAll(client);
    final List<Byte> expect = new ArrayList<>();
    for (int i = offset; i < (offset + length); i++) {
      expect.add(array[i]);
    }
    assertEquals(expect, actual);
  }

  @Test
  public void read() throws Throwable {
    try {
      client.write(ByteBuffer.wrap(byteArray()));
    } finally {
      client.close();
    }

    final List<Integer> actual = new ArrayList<>();
    int res;
    do {
      ByteBuffer bs = ByteBuffer.allocate(1);
      res = server.read(bs);
      bs.rewind();
      if (res != -1) {
        actual.add(bs.get(0) & 0xff);
      }
    } while (res != -1);
    final List<Integer> expect = intValues.stream().map(i -> i & 0xff).collect(Collectors.toList());
    assertEquals(expect, actual);
  }
}
