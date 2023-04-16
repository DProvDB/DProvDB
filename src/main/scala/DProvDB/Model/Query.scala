package edu
package DProvDB.Model

import edu.DProvDB.Model.QueryType.QueryType

class Node(var query: Query, var threshold: Double) {
  var nextNodes: List[Node] = _

  def setNexts(next: List[Node]): Unit = {
    nextNodes = next
  }
}

class Domain(var ub: Double, var lb: Double) {
  override def toString = s"Domain($ub, $lb)"
}

// TODO: re-construct the current poor design on query
// TODO: make query parent-class and build sub-classes for specific types of queries
class Query(id: Int, viewID: Int, analyst: Analyst) {
  var _accuracyRequirement: Option[Double] = None
  var _budgetSpecified: Option[Float] = None
  var _transformedQuery: TransformedQuery = null

  var _queryType: QueryType = _

  var _attrs: List[String] = _

  var _querier: Option[Int] = None // querier id

  var _rlb: Double = _
  var _rub: Double = _

  var queryString: String = _

  var _domains: List[Domain] = _

  val _viewID: Int = viewID

  def setQuerier (querierID: Int): Unit = {
    _querier = Some(querierID)
  }

  def setUBLB(ub: Double, lb: Double): Unit = {
    _rlb = lb
    _rub = ub
  }

  def setAccuracy (accReq: Double): Unit ={
    _accuracyRequirement = Some(accReq)
  }

  def setQueryString(qString: String): Unit = {
    queryString = qString
  }

  def setTransformedQuery (transformedQuery: TransformedQuery): Unit = {
    _transformedQuery = transformedQuery
  }

  def setType(queryType: QueryType): Unit = {
    _queryType = queryType
  }

  def setDomains (domains: List[Domain]): Unit = {
    _domains = domains
  }

  def setAttrs (attrs: List[String]): Unit = {
    _attrs = attrs
  }

  def clear: Unit = {
    _accuracyRequirement = None
    _budgetSpecified = None
    _transformedQuery.clear
  }

  override def toString: String = s"Query($id, $queryString, viewID=$viewID " + "_accuracyRequirement=" + _accuracyRequirement + ")"
}


