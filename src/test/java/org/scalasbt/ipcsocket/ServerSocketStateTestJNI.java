package org.scalasbt.ipcsocket;

public class ServerSocketStateTestJNI extends ServerSocketStateTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
