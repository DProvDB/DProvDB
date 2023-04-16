package edu.DProvDB.Model

object QueryType extends Enumeration {

  type QueryType = Value

  val RangeQuery, AggregationQuery, CountingQuery = Value

}
