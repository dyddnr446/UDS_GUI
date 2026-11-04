package uds.gui.analysis;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import uds.access.state.ActiveDiagnosticContext;
import uds.access.state.DiagnosticAccessState;
import uds.analysis.report.AnalysisReportWriter;
import uds.analysis.report.AnalysisReportWriter.RestoreResult;
import uds.analysis.session.AnalysisSessionStore;
import uds.analysis.session.PersistenceError;
import uds.catalog.load.UdsCatalog;
import uds.gui.app.AppSession;

/** Analysis Session tab — save/restore; never restores unlock privilege (NFR-21). UDS-UX-05. */
public final class AnalysisSessionViewModel {

  public static final String SECURITY_BANNER =
      "보안 정책 안내: 과거 세션 데이터만 복원되었으며, Security Unlock 권한은 복원되지 않습니다 (현재 상태: DEFAULT / LOCKED).";

  private final AppSession session;
  private final AnalysisReportWriter writer = new AnalysisReportWriter();
  private AnalysisSessionStore store;
  private final List<String> messages = new ArrayList<>();
  private final StringProperty restoreSummary = new SimpleStringProperty("");
  private final BooleanProperty securityBannerVisible = new SimpleBooleanProperty(false);
  private final StringProperty securityBannerText = new SimpleStringProperty("");

  public AnalysisSessionViewModel(AppSession session) {
    this.session = Objects.requireNonNull(session, "session");
    this.store =
        new AnalysisSessionStore(session.analysisSessionId(), session.catalog().catalogVersion());
  }

  public AnalysisSessionStore store() {
    return store;
  }

  public List<String> messages() {
    return List.copyOf(messages);
  }

  public StringProperty restoreSummaryProperty() {
    return restoreSummary;
  }

  public BooleanProperty securityBannerVisibleProperty() {
    return securityBannerVisible;
  }

  public StringProperty securityBannerTextProperty() {
    return securityBannerText;
  }

  public String restoreSummary() {
    return restoreSummary.get();
  }

  public boolean securityBannerVisible() {
    return securityBannerVisible.get();
  }

  public void bindProfile(String profileId) {
    store.setProfileId(profileId);
  }

  public void save() {
    messages.clear();
    try {
      writer.save(store, session.sessionDir());
      messages.add("saved " + session.sessionDir());
    } catch (PersistenceError e) {
      messages.add("PersistenceError: " + e.getMessage());
    }
  }

  public RestoreResult restoreFrom(Path sessionDir) {
    messages.clear();
    securityBannerVisible.set(false);
    securityBannerText.set("");
    ActiveDiagnosticContext context = session.context();
    UdsCatalog catalog = session.catalog();
    RestoreResult result =
        writer.restore(sessionDir, catalog.catalogVersion(), context);
    if (result.store() != null) {
      this.store = result.store();
      AnalysisSessionStore s = this.store;
      restoreSummary.set(
          "Requests="
              + (s.readResults().size() + s.writeResults().size() + s.decodeResults().size())
              + " Responses="
              + (s.readResults().size() + s.writeResults().size())
              + " Findings="
              + s.findings().size()
              + " AccessEvidence="
              + s.accessEvidences().size()
              + " catalogOK="
              + result.warnings().stream().noneMatch(w -> w.toLowerCase().contains("catalog")));
      securityBannerVisible.set(true);
      securityBannerText.set(SECURITY_BANNER);
    } else {
      restoreSummary.set("restore failed");
    }
    messages.addAll(result.warnings());
    messages.add("accessState=" + context.snapshot().accessState());
    return result;
  }

  public DiagnosticAccessState accessState() {
    return session.context().snapshot().accessState();
  }

  public Path sessionDir() {
    return session.sessionDir();
  }
}
