package org.folio.codex;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.folio.rest.jaxrs.model.Diagnostic;
import org.folio.rest.jaxrs.model.ResultInfo;

public class ResultInformation {

  private static Logger logger = LogManager.getLogger("codex.mux");

  private ResultInformation() {
  }

  public static <T> ResultInfo createResultInfo(Map<String, Multiplexer.MuxCollection<T>> colons) {
    int totalRecords = 0;
    List<Diagnostic> diagnostics = new LinkedList<>();
    for (Multiplexer.MuxCollection<T> muxCollection : colons.values()) {
      if (muxCollection.colExt != null && muxCollection.colExt.getResultInfo() != null) {
        totalRecords += muxCollection.colExt.getResultInfo().getTotalRecords();
        diagnostics.addAll(muxCollection.colExt.getResultInfo().getDiagnostics());
      }
    }

    ResultInfo resultInfo = new ResultInfo().withTotalRecords(totalRecords);
    resultInfo.setDiagnostics(diagnostics);
    return resultInfo;
  }

  public static <T> void analyzeResult(Map<String, Multiplexer.MuxCollection<T>> cols, Multiplexer.CollectionExtension<T> result) {

    List<Diagnostic> diagnosticList = new LinkedList<>();
    for (Map.Entry<String, Multiplexer.MuxCollection<T>> ent : cols.entrySet()) {
      Multiplexer.MuxCollection<T> muxCollection = ent.getValue();
      Diagnostic diagnostic = new Diagnostic();
      diagnostic.setSource(ent.getKey());
      diagnostic.setCode(Integer.toString(muxCollection.statusCode));
      if (muxCollection.colExt != null) {
        diagnostic.setRecordCount(muxCollection.colExt.getResultInfo().getTotalRecords());
      }
      diagnostic.setQuery(muxCollection.query);
      if (muxCollection.statusCode != 200) {
        diagnostic.setMessage(muxCollection.message.toString());
        logger.warn("Module {} returned status {}", ent.getKey(), muxCollection.statusCode);
        logger.warn(muxCollection.message.toString());
      }
      diagnosticList.add(diagnostic);
    }
    ResultInfo resultInfo = result.getResultInfo();
    resultInfo.setDiagnostics(diagnosticList);
    result.setResultInfo(resultInfo);
  }
}
