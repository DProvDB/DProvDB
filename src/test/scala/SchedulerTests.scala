package edu

import edu.DProvDB.{AccuracyState, State}
import edu.DProvDB.Model.{Analyst, View, WorkloadList}
import edu.DProvDB.Scheduler.{BFSRRScheduler, RandomScheduler, RoundRobinScheduler, ScheduleState}
import edu.DProvDB.Utils.WorkloadUtils
import junit.framework.TestCase

class SchedulerTests extends TestCase{


  val state = new State()
  state.setupDB("adult", "adult")

  var views: List[View] = _

  System.setProperty("dp.elastic_sensitivity.check_bins_for_release", "false")
  System.setProperty("db.use_dummy_database", "false")

  System.setProperty("db.driver", "org.postgresql.Driver")
  System.setProperty("db.url", "jdbc:postgresql://localhost:5432/adult")
  System.setProperty("db.username", "link")
  System.setProperty("db.password", "12345")

  // Use the table schemas and metadata defined by the test classes
  System.setProperty("schema.config.path", "src/test/resources/schema.yaml")

  override def setUp(): Unit = super.setUp()

  def testRRScheduler(): Unit = {

    val analysts = List(Analyst(0, "kirby", 4), Analyst(1, "Link", 6))
    val workloadSize = 10
    val accuracyState = AccuracyState("increasing", 12000, increasingStep = 1)

    val queries = List(WorkloadUtils.RRQ(state, analysts(0), workloadSize, accuracyState), WorkloadUtils.RRQ(state, analysts(1), workloadSize, accuracyState))


    val scheduler = "round-robin"
    val replacement = false

    val _scheduler = new RoundRobinScheduler()
    _scheduler.setup(analysts, queries, replacement)

    val scheduleState = ScheduleState()


    while (!_scheduler.isExhausted) {
      val queryQuerier = _scheduler.run(scheduleState)
      println(s"--Scheduling (${scheduler}) == Analyst: ${queryQuerier.analyst} == Query: ${queryQuerier.query}.--")
    }
  }

  def testRandomScheduler(): Unit = {

    val analysts = List(Analyst(0, "kirby", 4), Analyst(1, "Link", 6))
    val workloadSize = 10
    val accuracyState = AccuracyState("increasing", 12000, increasingStep = 1)

    val queries = List(WorkloadUtils.RRQ(state, analysts(0), workloadSize, accuracyState), WorkloadUtils.RRQ(state, analysts(1), workloadSize, accuracyState))

    queries foreach {workload =>
      println(workload)
      println(queries.indexOf(workload))
      println(analysts(queries.indexOf(workload)))
      WorkloadList(analysts(queries.indexOf(workload)), workload)}

    val scheduler = "random"
    val replacement = false

    val _scheduler = new RandomScheduler(42)
    _scheduler.setup(analysts, queries, replacement)
    val scheduleState = ScheduleState()


    while (!_scheduler.isExhausted) {
      val queryQuerier = _scheduler.run(scheduleState)
      println(s"--Scheduling (${scheduler}) == Analyst: ${queryQuerier.analyst} == Query: ${queryQuerier.query}.--")
    }

  }

  def testBFSRR(): Unit = {
    val analysts =  List(Analyst(0, "kirby", 4), Analyst(1, "Link", 6))

    val view = new View(1)

    val attrs = List("age")

    view.setAttr(attrs)

    state._EQWView = view

    val accuracyState = AccuracyState("increasing", 5, increasingStep = 1)

    val roots = List(WorkloadUtils.EQW(state, analysts(0), 1.0, 5, 3, accuracyState)._1, WorkloadUtils.EQW(state, analysts(1), 1.0, 5, 3, accuracyState)._1)

    val _scheduler = new BFSRRScheduler
    _scheduler.setup(analysts, roots=roots)
    val scheduleState = ScheduleState()

    while (!_scheduler.isExhausted) {
      val queryQuerier = _scheduler.run(scheduleState)

      if (queryQuerier != null)
        println(s"--Scheduling (${_scheduler}) == Analyst: ${queryQuerier.analyst} == Query: ${queryQuerier.query._domains}.--")

      scheduleState.ans = true
    }
  }

}
