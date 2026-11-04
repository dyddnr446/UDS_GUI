package uds.fixture.config;

/** Injectable time source for later delay/NRC 0x37 checks. */
public interface Clock {
  long millis();
}
