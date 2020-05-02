package org.scalasbt.ipcsocket;

public class SocketTestJNI extends SocketTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
