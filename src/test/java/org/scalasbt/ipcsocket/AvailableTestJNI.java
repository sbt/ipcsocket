package org.scalasbt.ipcsocket;

public class AvailableTestJNI extends AvailableTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
