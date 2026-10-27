package org.scalasbt.ipcsocket;

public class UnixSocketPathTestJNI extends UnixSocketPathTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
