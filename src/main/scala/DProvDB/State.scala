package edu
package DProvDB

import DProvDB.Provenance.ConstraintOptimizer
import chorus.rewriting.RewriterConfig
import chorus.schema.{Database, Schema}
import edu.DProvDB.Model.{Analyst, Node, Query, View, Workload}
import edu.DProvDB.Utils.{AnalystUtils, WorkloadUtils}
import edu.DProvDB.ViewManager.ViewManager

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

/**
 * A case class to specify the accuracy requirement.
 *
 * Accuracy mode:
 * "increasing" accuracy never decrease in the same workload;
 * "random" random accuracy requirement for queries in the workload.
 *
 * Starting variance: the accuracy requirement for the first query in the workload.
 *
 * Randomness: for mode "random", specify the variance of drawing the accuracy requirement in the workload.
 *
 * Increasing step: for mode "increasing", specify the step of increasing accuracy among queries in the workload.
 */
case class AccuracyState (accuracyMode: String, startingVar: Int, randomness: Int = 0, increasingStep: Int = 0) {
  require(List("increasing", "random").contains(accuracyMode))
  require((increasingStep!=0 || randomness!=0))
}


/**
 *
 * A case class to specify the parameters for setting provenance table.
 *
 * View constraint budget allocation:
 * "static": fair allocation over all views => PrivateSQL;
 * "dynamic": no prior allocation => DProvDB.
 *
 * Analyst constraint budget allocation:
 * "fixed-normalized": the constraints are fixed and split the overall budget;
 * "fixed-aGM": use aGM budget split;
 * "fixed-expansion": the constraints are fixed but the sum can exceed the overall budget by a multiplier tau;
 * "adaptive": the constraints can be adjusted adaptively.
 *
 * Expansion index:
 * Tau (>=1), = 1 means no expansion.
 */
case class ProvenanceState (viewConstraintAllocationMode: String, analystConstraintAllocationMode: String,
                            expansionIndex: Double = 1) {
  require(List("static", "dynamic").contains(viewConstraintAllocationMode))
  require(List("fixed-normalized", "fixed-aGM", "fixed-expansion", "adaptive").contains(analystConstraintAllocationMode))
  require(expansionIndex >= 1)

  override def toString: String = viewConstraintAllocationMode + " " + analystConstraintAllocationMode + ": " + expansionIndex
}

/**
 * The State class:
 * This class is to manage system settings and configuration.
 * This class is separated from the provenance class.
 *
 * The function of this class provides a wrapper of analysts and queries.
 * Based on different system/experiment setting, it select query and analyst and feed them
 * to the system class.
 *
 * This class is instantiated, based on the experimental inputs, at the beginning of the main class.
 * Other classes, e.g., the query pool, are instantiated based on this class.
 */
class State {

  var report_filename: String = _

  var _curRun: Int = 0

  var _randomnessSeed: Long = 42

  var _dataset: String = _
  var _database: Database = _
  var _tableName: String = _

  var _mechanism: String = _
  var _accountantMethod: String = _

  var _overallBudget: Double = _

  // analysts and queries are finalized once instantiated
  var _analysts: List[Analyst] = _
  var _queries: List[List[Query]] = _
  var _views: List[View] = _

  // for DFS/BFS tasks
  var _roots: List[Node] = _
  var _EQWView: View = _

  // inherited from Chorus; contains the information about the db, read from schema.yaml
  var _dbConfig: RewriterConfig = _

  // the way data analyst asks queries: e.g., round-robin, random
  var _scheduler: String = _
  var _replacement: Boolean = _

  var _provenanceState: ProvenanceState = _

  val viewManager = new ViewManager()

  /**
   * Parameters for workload of queries
   *
   * Workload size and workload type (e.g., RRQ, BFS, ...)
   * Accuracy requirements
   */
  var _workloadSize: Int = _
  var _workloadType: String = _
  var _accuracyState: AccuracyState = _

  /**
   * System performance (running time)
   */
  var _viewSetupTime: Double = _


  /**
   * Bounded or unbounded DP
   */
  var _DPFlag: String = "unbounded"


  /**
   * Logger modes:
   * - "log": only display necessary log;
   * - "debug": display info for debugging;
   * - "full": display all log information.
   */
  var _logger: String = "log"

  /**
   * Constructor-like function to set up flags.
   */
  def setupDB(dataset: String, tableName: String): Unit = {
    _dataset = dataset

    _database = Schema.getDatabase(dataset)
    _dbConfig = new RewriterConfig(_database)

    _tableName = tableName
  }

  def setReportFile(filename: String): Unit = {
    report_filename = filename
  }

  def setLogger(logger: String): Unit = {
    _logger = logger
  }

  /**
   * For simplicity, we assume every data analyst asks the same type and size of workload queries.
   */
  def setupAnalystWorkload(workloadType: String, workloadSize: Int, accuracyState: AccuracyState): Unit = {
    _workloadSize = workloadSize
    _workloadType = workloadType
    _accuracyState = accuracyState

    val queriesBuilder = new ListBuffer[List[Query]]

    _analysts foreach {
      analyst =>
        workloadType match {
          case "RRQ" =>
            queriesBuilder += WorkloadUtils.RRQ(this, analyst, workloadSize, accuracyState)
          case _ => throw new IllegalArgumentException()
        }
    }

    _queries = queriesBuilder.toList
  }

  /**
   * For BFS/DFS tasks, set up a list of workloads for queriers.
   *
   * Parameters:
   * attrs: build the traverse tree over the cross-domain of these attributes;
   * granularity: the size of the sub-domain should be larger than this number;
   * threshold: threshold for expanding the tree, based on the query answer;
   * m: m-ary tree.
   */
  def setupEQW(workloadType: String, attrs: List[String], granularity: Double, threshold: Double, m: Int, accuracyState: AccuracyState): Unit = {

    _workloadType = workloadType
    _accuracyState = accuracyState

    findEQWView(attrs)

    val rootsBuilder = new ListBuffer[Node]

    var tuple: (Node, Int) = null

    _analysts foreach {
      analyst =>
        tuple = WorkloadUtils.EQW(this, analyst, granularity, threshold, m, accuracyState)
        rootsBuilder += tuple._1
    }

    _roots = rootsBuilder.toList

    _workloadSize = tuple._2 * _analysts.size

  }

  def findEQWView(attrs: List[String]): Unit = {
    _EQWView = _views find( view => view._attrs.equals(attrs)) orNull
  }

  /**
   * Set up a list of views based on the dataset.
   */
  def setupViews(): List[View] = {

    viewManager.setState(this)

    if (_views == null) {
      _dataset match {
        case "adult" | "tpch" =>
          val ret = time {
            viewManager.setupPlainViews()
          }
          _viewSetupTime = ret._2
          _views = ret._1
          ret._1
      }
    }
    else {
      _views foreach {view => view.refresh}
      _views
    }

  }

  def setOverallBudget(overallBudget: Double): Unit = {
    _overallBudget = overallBudget
  }

  /**
   * Set up a list of data analyst based on their privileges.
   */
  def setupAnalysts(privileges: List[Int]): Unit = {
    _analysts = AnalystUtils.initAnalysts(_overallBudget, privileges, _provenanceState)

    _provenanceState.analystConstraintAllocationMode match {
      case "fixed-normalized" | "fixed-expansion" | "fixed-aGM" =>
        ConstraintOptimizer.budgetExpansion(_analysts, _provenanceState.expansionIndex)
      case "adaptive" =>
    }
  }

  def setProvenanceState(provenanceState: ProvenanceState): Unit = {
    _provenanceState = provenanceState
  }

  def setMechanism(mechanism: String, accountant: String): Unit = {
    _mechanism = mechanism
    _accountantMethod = accountant
  }

  def setScheduler(scheduler: String, replacement: Boolean = false): Unit = {
    _scheduler = scheduler
    _replacement = replacement
  }

  def setRandomSeed(seed: Long): Unit = {
    _randomnessSeed = seed
  }

  def setCurRun(curRun: Int): Unit = {
    _curRun = curRun
    _randomnessSeed = curRun * 1025
  }

  def setDPFlag(dpFlag: String = "unbounded"): Unit = {
    _DPFlag = dpFlag
  }

  def time[A](f: => A): (A, Double) = {
    val s = System.nanoTime()
    val res = f
    (res, (System.nanoTime() - s) / 1e+6)
  }


  override def toString: String = super.toString
}
