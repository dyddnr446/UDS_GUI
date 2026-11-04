package uds.io;

import java.util.List;

/** Frames plus isolated line errors from a log file. */
public record LogReadResult(List<Frame> frames, List<LineError> errors) {

  public LogReadResult {
    frames = List.copyOf(frames);
    errors = List.copyOf(errors);
  }
}
