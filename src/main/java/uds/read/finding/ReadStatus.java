package uds.read.finding;

/** CSV expected_status 1:1 (FR-16). */
public enum ReadStatus {
  NORMAL,
  ABNORMAL,
  REJECTED,
  PARSE_ERROR_DID_MISMATCH,
  PARSE_ERROR_LENGTH,
  ACCESS_DENIED
}
