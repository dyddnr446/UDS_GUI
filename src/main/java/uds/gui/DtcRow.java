package uds.gui;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import uds.read.DtcItem;

/** TableView row for the DTC Result card. */
public final class DtcRow {

  private final StringProperty code = new SimpleStringProperty();
  private final StringProperty name = new SimpleStringProperty();
  private final StringProperty statusByte = new SimpleStringProperty();
  private final StringProperty flags = new SimpleStringProperty();

  public DtcRow(DtcItem item) {
    code.set(item.codeHex());
    name.set(item.name() == null || item.name().isBlank() ? "—" : item.name());
    statusByte.set(item.statusByteHex());
    flags.set(item.flagsText());
  }

  public StringProperty codeProperty() {
    return code;
  }

  public StringProperty nameProperty() {
    return name;
  }

  public StringProperty statusByteProperty() {
    return statusByte;
  }

  public StringProperty flagsProperty() {
    return flags;
  }
}
