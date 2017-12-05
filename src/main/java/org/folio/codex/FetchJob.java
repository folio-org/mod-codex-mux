package org.folio.codex;

import java.util.List;

public class FetchJob {

  int totalRecords;
  int offset;
  int limit;

  public FetchJob(int total) {
    totalRecords = total;
    offset = limit = -1;
  }

  public static void roundRobin(List<FetchJob> jobs, int offset, int limit) {
    for (FetchJob job : jobs) {
      job.limit = 0;
    }
    int gPos = 0;
    int jPos = 0;
    boolean more = true;
    while (more) {
      more = false;
      for (FetchJob job : jobs) {
        if (gPos >= offset + limit) {
          break;
        }
        if (jPos < job.totalRecords) {
          if (gPos >= offset) {
            if (job.limit == 0) {
              job.offset = jPos;
            }
            job.limit++;
          }
          more = true;
          gPos++;
        }
      }
      jPos++;
    }
  }
}
