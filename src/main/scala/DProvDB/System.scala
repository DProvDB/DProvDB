package edu
package DProvDB

import DProvDB.Model._
import edu.DProvDB.Mechanisms.{AdditiveGM, BaselineMechanism, Chorus, ChorusWithProvenance, Mechanism, PrivateSQL}
import edu.DProvDB.Provenance.ProvenanceTable
import edu.DProvDB.Scheduler.{BFSRRScheduler, QueryQuerierThreshold, RandomScheduler, RoundRobinScheduler, ScheduleState, Scheduler}
import edu.DProvDB.Utils.{ProvenanceUtils, ViewUtils}

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.util.control.Breaks._

class System {

  var _provTable: ProvenanceTable = _
  var _state: State = _

  var _scheduler: Scheduler = _

  var _mechanism: Mechanism = _


  // see if this will be kept
  var accountant: Double = 0
  var analystsAccountant: List[Double] = _
  var avgAccuracy: Double = 0
  var queryCounter: Double = 0

  var accountantLedger: ListBuffer[Double] = new ListBuffer[Double]
  var workloadIndex: Double = 0


  def setup(state: State): Unit = {

    _state = state

    println(state)

    setupProvenance(_state)

    state._mechanism match {
      case "aGM" => _mechanism = new AdditiveGM(state, _provTable, state._analysts, state._views, compositionMethod = state._accountantMethod)
      case "baseline" => _mechanism = new BaselineMechanism(_provTable, compositionMethod = state._accountantMethod)
      case "Chorus" => _mechanism = new Chorus(_provTable, compositionMethod = state._accountantMethod)
      case "ChorusP" => _mechanism = new ChorusWithProvenance(state, _provTable, compositionMethod = state._accountantMethod)
      case "PrivateSQL" => _mechanism = new PrivateSQL(_provTable, compositionMethod = state._accountantMethod)
      case _ => throw new IllegalArgumentException()
    }

    _mechanism.setState(state)

    state._scheduler match {
      case "random" =>
        _scheduler = new RandomScheduler(state._randomnessSeed)
        _scheduler.setup(_state._analysts, _state._queries, _state._replacement)
      case "round-robin" =>
        _scheduler = new RoundRobinScheduler()
        _scheduler.setup(_state._analysts, _state._queries, _state._replacement)
      case "BFS" =>
        _scheduler = new BFSRRScheduler()
        _scheduler.setup(_state._analysts, null, _state._replacement, _state._roots)
      case _ => throw new IllegalArgumentException()
    }

  }

  private def setupProvenance(state: State): Unit = {
    _provTable = new ProvenanceTable(state._views, state._analysts)

    // set up constraints
    state._provenanceState.viewConstraintAllocationMode match {
      case "static" =>
        ProvenanceUtils.setConstraintStatic(_provTable, state._analysts, state._views, state._overallBudget, state._views.length * 2)
      case "dynamic" =>
        ProvenanceUtils.setConstraintDynamic(_provTable, state._analysts, state._views, state._overallBudget)
    }
  }

  def execute(): Unit = {

    println(s"======Start to execute the ${_state._mechanism} mechanism.============")
    val scheduleState = ScheduleState()

    while (!_scheduler.isExhausted) {
      val queryQuerier = _scheduler.run(scheduleState)

      workloadIndex += 1

      breakable {
      if (queryQuerier == null)
        break



      if (_state._logger.equals("debug")) {
        println(s"--Scheduling (${_state._scheduler}) == Analyst: ${queryQuerier.analyst} == Query: ${queryQuerier.query}, on range ${queryQuerier.query._rlb} (${queryQuerier.query._domains.head.lb}) to ${queryQuerier.query._rub} (${queryQuerier.query._domains.head.ub}).--")
      }

      val view = _state._views find (view => view._viewID == queryQuerier.query._viewID) orNull

      if (_state._logger.equals("debug"))
        println(s"--This query is matched to view: ID ${view._viewID}, Attr ${view._attrs}.--")


      /**
       * Get sensitivity and #bins to answer the query.
       */
      val sensitivity = ViewUtils.getSensitivity(view, queryQuerier.query, _state._DPFlag)

      var bins: Int = 1

      _state._DPFlag match {
        case "bounded" => bins = sensitivity / 2
        case "unbounded" => bins = sensitivity
        case _ => throw new IllegalArgumentException()
      }

      if (_state._logger.equals("debug"))
        println(s"--The sensitivity of the query over the selected view: Sens=${sensitivity}, bins=$bins.--")

      val epsilon = _mechanism.privacyTranslation(sensitivity, queryQuerier.query._accuracyRequirement.getOrElse(Double.NaN),
        _provTable, queryQuerier.analyst, view, bins)

      if (_state._logger.equals("debug"))
        println(s"--Translated privacy budget: eps=$epsilon.--")


      var synopsis: NoisyView = null

      if (epsilon != Double.PositiveInfinity) {
        val status = _mechanism.checkConstraints(queryQuerier.analyst.id, view._viewID, epsilon)
        if (status.equals("Pass")) {

          if (_state._logger.equals("full") || _state._logger.equals("debug"))
            println("--Query has been executed.--")

          synopsis = _mechanism.run(queryQuerier.query, queryQuerier.analyst, view, epsilon, status)

          queryQuerier.analyst.queryAnsweredCounter()

          avgAccuracy += queryQuerier.query._accuracyRequirement.getOrElse(0.0)
          queryCounter += 1
        }
        else if (status.equals("Cache")) {

          if (_state._logger.equals("full") || _state._logger.equals("debug"))
            println("--Query has been executed via Cache.--")

          synopsis = _mechanism.run(queryQuerier.query, queryQuerier.analyst, view, epsilon, status)
          queryQuerier.analyst.queryAnsweredCounter()

          avgAccuracy += queryQuerier.query._accuracyRequirement.getOrElse(0.0)
          queryCounter += 1
        }
        else {
          // reject
          if (_state._logger.equals("full") || _state._logger.equals("debug"))
            println("--Query has been rejected.--")
        }

      }

      // answer query
      var ans: Double = null.asInstanceOf[Double]

      if (_state._workloadType.equals("EQW")) {
      _state._mechanism match {
        case "aGM" => {
          ans = synopsis.asInstanceOf[LocalSynopsis].queryAnswering(queryQuerier.query, _state.viewManager)
        }
        case "baseline" => {
          ans = synopsis.queryAnswering(queryQuerier.query, _state.viewManager)
        }
        case "Chorus" | "ChorusP" =>
          ans = synopsis.asInstanceOf[Synopsis]._aggregationQueryAnswer
      }

      println(s"--Query Answer: ${ans}--")


        if (ans == Double.NaN)
          scheduleState.ans = false
        else {
          if (ans > queryQuerier.asInstanceOf[QueryQuerierThreshold].threshold)
            scheduleState.ans = true
          else
            scheduleState.ans = false
        }

        // partially record the accountant
        if (workloadIndex > _state._workloadSize * accountantLedger.size / 100)
          accountantLedger += _mechanism.getConsumedBudget
      }

    }
  }

    accountant = _mechanism.getConsumedBudget
    avgAccuracy /= queryCounter

    accountForAnalysts()
  }

  def accountForAnalysts(): Unit = {
    analystsAccountant = _state._analysts map {
      analyst =>
        _mechanism.privacyAccountOverList(_provTable.getRow(analyst.id), "row")
    }
  }
}
