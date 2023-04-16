package edu
package DProvDB.Scheduler

import edu.DProvDB.Model.{Analyst, Node, Query, Workload, WorkloadList, WorkloadQueue}

import scala.collection.mutable
import scala.util.Random

trait ScheduleResults{
  def analyst: Analyst
  def query: Query
}

case class QueryQuerier(analyst: Analyst, query: Query) extends ScheduleResults

case class QueryQuerierThreshold(analyst: Analyst, query: Query, threshold: Double) extends ScheduleResults

case class ScheduleState(var ans: Boolean = false)

/**
 * The scheduler interface.
 *
 * This class maintains two lists:
 * The list of analysts participated in the system.
 * The query workloads each analyst submits.
 *
 * A workload is a list of queries that one analyst submitted to the system.
 *
 * The scheduler interface maintain a *replacement* flag, indicating if a query
 * would be dropped after being scheduled.
 *
 * The run method will return a query, analyst pair, defined by the QueryQuerier class.
 * When an analyst is scheduled, then we add 1 to this analyst's submitted query counter.
 * If there is no more queries in the workload, then return null.
 */
abstract class Scheduler {

  var _analysts: List[Analyst] = _
  var _workloads: List[Workload]
  var _replacement: Boolean = _

  def setup(analysts: List[Analyst], workloads: List[List[Query]], replacement: Boolean, roots: List[Node] = null): Unit

  def run (scheduleState: ScheduleState): ScheduleResults

  // if all analysts' workload queries are exhausted
  def isExhausted: Boolean = _workloads map {workload => workload.isExhausted} reduce(_&&_)
}

/**
 * Round-Robin scheduler:
 * Data analysts take turns to ask queries.
 * e.g., A1, A2, A3, A1, A2, A3....
 */
class RoundRobinScheduler extends Scheduler {

  private var curAnalyst: Int = 0
  override var _workloads: List[Workload] = _

  def setup(analysts: List[Analyst], workloads: List[List[Query]], replacement: Boolean, roots: List[Node] = null): Unit = {
    _analysts = analysts

    _workloads = workloads map {workload => WorkloadQueue(analysts(workloads.indexOf(workload)), workload)}

    _replacement = replacement
  }

  def run (scheduleState: ScheduleState): QueryQuerier = {

    var index = curAnalyst % _analysts.length
    curAnalyst += 1

    // handling exhausted workload
    var nextIndex = index
    while (_workloads(nextIndex).asInstanceOf[WorkloadQueue].queryQueue.isEmpty) {
      nextIndex = (nextIndex + 1) % _analysts.length
      if (nextIndex == index)
        return null
    }

    // if there exists at least one workload not being exhausted
    index = nextIndex
    val analyst = _analysts(index)
    analyst.querySubmittedCounter()

    if (_replacement) {
      val query = _workloads(index).asInstanceOf[WorkloadQueue].queryQueue.dequeue
      _workloads(index).asInstanceOf[WorkloadQueue].queryQueue += query
      QueryQuerier(analyst, query)
    } else {
      QueryQuerier(analyst, _workloads(index).asInstanceOf[WorkloadQueue].queryQueue.dequeue)
    }
  }

  override def isExhausted: Boolean = _workloads map {workload => workload.asInstanceOf[WorkloadQueue].isExhausted} reduce(_&&_)

}

/**
 * This scheduler randomly picks an analyst and a random query from his workload.
 *
 * Simulating the case that analysts randomly submitting queries to system.
 */
class RandomScheduler(seed: Long = 42) extends Scheduler {

  val rnd = new Random(seed)

  override var _workloads: List[Workload] = _
  override def setup(analysts: List[Analyst], workloads: List[List[Query]], replacement: Boolean, roots: List[Node] = null): Unit = {
    _analysts = analysts
    _workloads = workloads map {workload => WorkloadList(analysts(workloads.indexOf(workload)), workload)}
    _replacement = replacement
  }

  override def run (scheduleState: ScheduleState): QueryQuerier = {

    var index = rnd.nextInt(_analysts.length)

    // handling exhausted workload
    var nextIndex = index
    while (_workloads(nextIndex).queries.isEmpty) {
      nextIndex = (nextIndex + 1) % _analysts.length
      if (nextIndex == index)
        return null
    }
    index = nextIndex
    
    // draw query
    val analyst = _analysts(index)
    analyst.querySubmittedCounter()

    val queryDraw = rnd.nextInt(_workloads(index).queries.length)
    val query = _workloads(index).queries(queryDraw)
    
    if (_replacement) {
      QueryQuerier(analyst, query)
    } else {
      _workloads(index).remove(query)
      QueryQuerier(analyst, query)
    }

  }

  override def isExhausted: Boolean = super.isExhausted
}

case class Flag(var flag: Boolean)

class BFSRRScheduler extends Scheduler {

  val nodeQueue = new mutable.Queue[Node]()
//  val analystStack = new java.util.Stack[Node]()
  val childrenQueue = new mutable.Queue[Node]()

  override var _workloads: List[Workload] = _

  var firstCallFlags: List[Flag] = _
  var emptyFlags: List[Flag] = _

  private var curAnalyst: Int = 0

  def setup(analysts: List[Analyst], workloads: List[List[Query]] = null, replacement: Boolean = false, roots: List[Node]): Unit = {
    _analysts = analysts

    firstCallFlags = analysts map {_ => Flag(true)}
    emptyFlags = analysts map {_ => Flag(false)}

    for (i <- roots.indices)
      nodeQueue.enqueue(roots(i))
  }

  def run(scheduleState: ScheduleState): QueryQuerierThreshold = {

    var index = curAnalyst % _analysts.length
    curAnalyst += 1

    // handling exhausted workload
    var nextIndex = index
    while (emptyFlags(nextIndex).flag) {
      nextIndex = (nextIndex + 1) % _analysts.length
      if (nextIndex == index)
        return null
    }
    index = nextIndex


    // push children to node queue if over threshold
    if (scheduleState.ans) {
      while (childrenQueue.nonEmpty) {
        val temp = childrenQueue.dequeue()
        nodeQueue.enqueue(temp)
      }
    } else {
      childrenQueue.clear()
    }

    if (isExhausted)
      return null

    // construct query querier
    var node = nodeQueue.dequeue()
    if (firstCallFlags(index).flag) {

      pushChildren(node, nodeQueue)
      firstCallFlags(index).flag = false
      checkEmpty()

      return QueryQuerierThreshold(_analysts(index), node.query, node.threshold)
    }

    while (node.query._querier.getOrElse(throw new IllegalStateException()) != _analysts(index).id) {
      nodeQueue.enqueue(node)
      node = nodeQueue.dequeue()
    }

    val query = node.query

    pushChildren(node, childrenQueue)
    checkEmpty()

    QueryQuerierThreshold(_analysts(index), query, node.threshold)

  }

  // if all analysts' workload queries are exhausted
  override def isExhausted: Boolean = {
    (emptyFlags forall { flag => flag.flag }) || (childrenQueue.isEmpty && nodeQueue.isEmpty)
  }

  def pushChildren (node: Node, queue: mutable.Queue[Node]): Unit = {
    if (node.nextNodes.nonEmpty) {
      for (i <- node.nextNodes.indices) {
        if (node.nextNodes(i) != null)
          queue.enqueue(node.nextNodes(i))
      }
    }
  }

  def checkEmpty (): Unit = {
    for (i <- _analysts.indices) {
      val analystID = _analysts(i).id
      if (checkStackExhausted(analystID, nodeQueue) && checkStackExhausted(analystID, childrenQueue))
        emptyFlags(i).flag = true
    }
  }

  def checkStackExhausted (analystID: Int, queue: mutable.Queue[Node]) : Boolean = {

    var flag = true

    if (queue.nonEmpty) {
      queue.foreach(node => {
        if (node.query._querier.getOrElse(throw new IllegalStateException()) == analystID)
          flag = false
      })
    }

    flag
  }

}