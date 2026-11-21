package uds.fixture;

/** Injectable time source for later delay/NRC 0x37 checks. */
public interface Clock {
  long millis();
}
