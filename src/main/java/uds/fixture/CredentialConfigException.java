package uds.fixture;

public final class CredentialConfigException extends RuntimeException {
  public static final String CREDENTIAL_CONFIG_ERROR = "CREDENTIAL_CONFIG_ERROR";

  private final String code;

  public CredentialConfigException(String message) {
    super(message);
    this.code = CREDENTIAL_CONFIG_ERROR;
  }

  public String code() {
    return code;
  }
}
