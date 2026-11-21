package uds.analysis;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** evidenceId prefixes: LOG: / READ: / FINDING: / WRITE: / ACCESS: */
public final class EvidenceIdGenerator {

  public static final String LOG = "LOG:";
  public static final String READ = "READ:";
  public static final String FINDING = "FINDING:";
  public static final String WRITE = "WRITE:";
  public static final String ACCESS = "ACCESS:";

  private final AtomicLong seq = new AtomicLong();

  public String next(String prefix) {
    if (prefix == null || prefix.isBlank()) {
      throw new IllegalArgumentException("prefix");
    }
    String p = prefix.endsWith(":") ? prefix : prefix + ":";
    return p + seq.incrementAndGet() + "-" + UUID.randomUUID();
  }

  public String log() {
    return next(LOG);
  }

  public String read() {
    return next(READ);
  }

  public String finding() {
    return next(FINDING);
  }

  public String write() {
    return next(WRITE);
  }

  public String access() {
    return next(ACCESS);
  }
}
