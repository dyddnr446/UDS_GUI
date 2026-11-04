package uds.interpreter.decode;

import java.util.ArrayList;
import java.util.List;
import uds.catalog.load.UdsCatalog;
import uds.io.Frame;
import uds.io.LogReadResult;
import uds.interpreter.model.DecodeResult;

/** Orchestrates deterministic frame decoding against a catalog snapshot. */
public final class UdsInterpreter {

  private final UdsCatalog catalog;

  public UdsInterpreter(UdsCatalog catalog) {
    this.catalog = catalog;
  }

  public DecodeResult parse(Frame frame) {
    return UdsParser.parse(frame, catalog);
  }

  public List<DecodeResult> parseAll(List<Frame> frames) {
    List<DecodeResult> out = new ArrayList<>(frames.size());
    for (Frame frame : frames) {
      out.add(parse(frame));
    }
    return List.copyOf(out);
  }

  public List<DecodeResult> parse(LogReadResult log) {
    return parseAll(log.frames());
  }

  public UdsCatalog catalog() {
    return catalog;
  }
}
