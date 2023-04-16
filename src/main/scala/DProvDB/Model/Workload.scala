package edu.DProvDB.Model

import scala.collection.mutable

trait Workload {

  val analyst: Analyst
  var queries: List[Query]

  def remove(query: Query): List[Query] = queries diff List(query)

  def isExhausted: Boolean = queries.isEmpty
}

case class WorkloadList(override val analyst: Analyst, override var queries: List[Query]) extends Workload {
  override def isExhausted: Boolean = super.isExhausted

  override def remove(query: Query): List[Query] = {
    queries = super.remove(query)
    queries
  }
}

case class WorkloadQueue(override val analyst: Analyst, override var queries: List[Query])
  extends Workload {
  val queryQueue: mutable.Queue[Query] = mutable.Queue(queries: _*)

  override def isExhausted: Boolean = queryQueue.isEmpty
}
