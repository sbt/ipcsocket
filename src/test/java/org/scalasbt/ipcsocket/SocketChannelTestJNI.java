package org.scalasbt.ipcsocket;

public class SocketChannelTestJNI extends SocketChannelTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
