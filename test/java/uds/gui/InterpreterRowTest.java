package uds.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uds.interpreter.DecodeKind;
import uds.interpreter.DecodeResult;
import uds.io.LineError;

class InterpreterRowTest {

  @Test
  void decodeColumns_mapTimeDirSidSubDid() {
    DecodeResult r =
        new DecodeResult(
            "1.200/REQ",
            DecodeKind.REQUEST,
            "0x22",
            "ReadDataByIdentifier",
            null,
            null,
            null,
            Map.of("did", "0x1001", "dataHex", "01 F4"),
            null);
    InterpreterRow row = InterpreterRow.ofDecode(r);
    assertEquals("1.200", row.time());
    assertEquals("REQ", row.dir());
    assertEquals("0x22", row.sid());
    assertEquals("ReadDataByIdentifier", row.service());
    assertEquals("0x1001", row.subDidNrc());
    assertTrue(row.data().contains("did=0x1001"));
    assertFalse(row.isError());
  }

  @Test
  void negativeUsesNrcAndRedactedFlag() {
    DecodeResult nrc =
        new DecodeResult(
            "1.310/RES",
            DecodeKind.NEGATIVE,
            "0x7F",
            "NegativeResponse",
            null,
            "0x31",
            "requestOutOfRange",
            Map.of("nrc", "0x31"),
            null);
    InterpreterRow row = InterpreterRow.ofDecode(nrc);
    assertEquals("NEG", row.dir());
    assertEquals("requestOutOfRange", row.subDidNrc());
    assertEquals(DecodeKind.NEGATIVE, row.kind());
    assertTrue(row.isProblem());
    assertFalse(row.isError());

    DecodeResult sec =
        new DecodeResult(
            "1.610/RES",
            DecodeKind.POSITIVE,
            "0x67",
            "SecurityAccess",
            "requestSeed",
            null,
            null,
            Map.of("seed", "<REDACTED>", "securityPayload", "<REDACTED>"),
            false);
    assertTrue(InterpreterRow.ofDecode(sec).isRedacted());
  }

  @Test
  void errorRow_mapsLineAndReason() {
    InterpreterRow row = InterpreterRow.ofError(new LineError(12, "bad", "hex parse"));
    assertTrue(row.isError());
    assertTrue(row.isProblem());
    assertEquals("L12", row.time());
    assertEquals("ERR", row.dir());
    assertEquals("hex parse", row.subDidNrc());
    assertTrue(row.displayLine().startsWith("ERR "));
  }

  @Test
  void detailAndCopy_includeFieldsAndRedactSecurityHex() {
    DecodeResult r =
        new DecodeResult(
            "1.200/REQ",
            DecodeKind.REQUEST,
            "0x22",
            "ReadDataByIdentifier",
            null,
            null,
            null,
            Map.of("did", "0x1001", "dataHex", "01 F4"),
            null);
    InterpreterRow row = InterpreterRow.ofDecode(r, "22 10 01");
    assertTrue(row.detailText().contains("Raw Hex: 22 10 01"));
    assertTrue(row.detailText().contains("did=0x1001"));
    assertTrue(row.copyTsv().contains("0x1001"));
    assertTrue(row.copyTsv().contains("22 10 01"));

    DecodeResult sec =
        new DecodeResult(
            "1.610/RES",
            DecodeKind.POSITIVE,
            "0x67",
            "SecurityAccess",
            "requestSeed",
            null,
            null,
            Map.of("seed", "<REDACTED>"),
            false);
    InterpreterRow secRow = InterpreterRow.ofDecode(sec, "67 01 AA BB");
    assertTrue(secRow.detailText().contains("Raw Hex: <REDACTED>"));
    assertFalse(secRow.copyTsv().contains("AA BB"));
  }
}
