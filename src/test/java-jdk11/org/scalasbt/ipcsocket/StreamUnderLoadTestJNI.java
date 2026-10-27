package org.scalasbt.ipcsocket;

public class StreamUnderLoadTestJNI extends StreamUnderLoadTest {
  @Override
  boolean useJNI() {
    return true;
  }
}
