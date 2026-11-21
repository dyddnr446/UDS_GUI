package uds.fixture;

public final class SystemClock implements Clock {
  @Override
  public long millis() {
    return System.currentTimeMillis();
  }
}
