package org.scalasbt.ipcsocket;

public class ServerSideCloseTestJNI extends ServerSideCloseTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
