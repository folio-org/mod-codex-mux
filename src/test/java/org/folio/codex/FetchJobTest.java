package org.folio.codex;

import java.util.LinkedList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class FetchJobTest {

  public FetchJobTest() {
  }

  @Test
  public void test1() {
    FetchJob fj1 = new FetchJob(5);
    List<FetchJob> jobs = new LinkedList<>();
    jobs.add(fj1);

    FetchJob.roundRobin(jobs, 0, 3);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(3, jobs.get(0).limit);

    FetchJob.roundRobin(jobs, 0, 4);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(4, jobs.get(0).limit);

    FetchJob.roundRobin(jobs, 0, 5);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(5, jobs.get(0).limit);

    FetchJob.roundRobin(jobs, 0, 6);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(5, jobs.get(0).limit);

    FetchJob.roundRobin(jobs, 2, 2);
    assertEquals(2, jobs.get(0).offset);
    assertEquals(2, jobs.get(0).limit);

    FetchJob.roundRobin(jobs, 2, 3);
    assertEquals(2, jobs.get(0).offset);
    assertEquals(3, jobs.get(0).limit);

    FetchJob.roundRobin(jobs, 2, 4);
    assertEquals(2, jobs.get(0).offset);
    assertEquals(3, jobs.get(0).limit);

    FetchJob.roundRobin(jobs, 2, 5);
    assertEquals(2, jobs.get(0).offset);
    assertEquals(3, jobs.get(0).limit);
  }

  @Test
  public void test2() {
    FetchJob fj1 = new FetchJob(5);
    FetchJob fj2 = new FetchJob(7);
    List<FetchJob> jobs = new LinkedList<>();
    jobs.add(fj1);
    jobs.add(fj2);

    FetchJob.roundRobin(jobs, 0, 3);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(2, jobs.get(0).limit);
    assertEquals(0, jobs.get(1).offset);
    assertEquals(1, jobs.get(1).limit);

    FetchJob.roundRobin(jobs, 0, 4);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(2, jobs.get(0).limit);
    assertEquals(0, jobs.get(1).offset);
    assertEquals(2, jobs.get(1).limit);

    FetchJob.roundRobin(jobs, 0, 5);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(3, jobs.get(0).limit);
    assertEquals(0, jobs.get(1).offset);
    assertEquals(2, jobs.get(1).limit);

    FetchJob.roundRobin(jobs, 0, 10);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(5, jobs.get(0).limit);
    assertEquals(0, jobs.get(1).offset);
    assertEquals(5, jobs.get(1).limit);

    FetchJob.roundRobin(jobs, 0, 11);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(5, jobs.get(0).limit);
    assertEquals(0, jobs.get(1).offset);
    assertEquals(6, jobs.get(1).limit);

    FetchJob.roundRobin(jobs, 0, 12);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(5, jobs.get(0).limit);
    assertEquals(0, jobs.get(1).offset);
    assertEquals(7, jobs.get(1).limit);

    FetchJob.roundRobin(jobs, 0, 13);
    assertEquals(0, jobs.get(0).offset);
    assertEquals(5, jobs.get(0).limit);
    assertEquals(0, jobs.get(1).offset);
    assertEquals(7, jobs.get(1).limit);

    FetchJob.roundRobin(jobs, 2, 3);
    assertEquals(1, jobs.get(0).offset);
    assertEquals(2, jobs.get(0).limit);
    assertEquals(1, jobs.get(1).offset);
    assertEquals(1, jobs.get(1).limit);

    FetchJob.roundRobin(jobs, 3, 3);
    assertEquals(2, jobs.get(0).offset);
    assertEquals(1, jobs.get(0).limit);
    assertEquals(1, jobs.get(1).offset);
    assertEquals(2, jobs.get(1).limit);
  }

}
