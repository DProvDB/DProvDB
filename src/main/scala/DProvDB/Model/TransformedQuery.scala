package edu
package DProvDB.Model

import chorus.sql.QueryParser
import com.facebook.presto.sql.tree
import com.facebook.presto.sql.tree.{ComparisonExpression, Expression, LogicalBinaryExpression, QualifiedNameReference, QuerySpecification, Select}

import java.util.Optional
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Breaks._
import scala.util.control.ControlThrowable

object ComparisonOperators extends Enumeration {
  type ComparisonOperator = Value

  val NOT_EQUAL, EQUAL, GREATER_THAN, LESS_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL = Value
}

case class Predicate (attr: String, operator: ComparisonOperators.ComparisonOperator, value: String) {
  def evaluate(LHS: String): Boolean = {
    operator match {
      case ComparisonOperators.NOT_EQUAL => LHS != value
      case ComparisonOperators.EQUAL  => LHS == value
      case ComparisonOperators.GREATER_THAN_OR_EQUAL => LHS.toDouble >= value.toDouble
      case ComparisonOperators.GREATER_THAN =>  LHS.toDouble > value.toDouble
      case ComparisonOperators.LESS_THAN_OR_EQUAL => LHS.toDouble <= value.toDouble
      case ComparisonOperators.LESS_THAN =>  LHS.toDouble < value.toDouble
      case err => throw new Exception("Unknown predicate operators: " + err.toString)
    }

  }

  override def toString: String = {
    attr + ", " + operator.toString + ", " + value
  }
}

class TransformedQuery(query: String) {

  var _interval: Double = 1.0

  val queryTree: tree.Query = QueryParser.parseToPrestoTree(query)

  val queryExpr: Select = queryTree.getQueryBody.asInstanceOf[QuerySpecification].getSelect

  val queryWhere: Optional[Expression] = queryTree.getQueryBody.asInstanceOf[QuerySpecification].getWhere

  var queryLogicalExpressions: LogicalBinaryExpression = _

  def this(query: String, interval: Double) {
    this(query)
    _interval = interval
  }


  // assuming all AND predicates
  var predicates: ArrayBuffer[Predicate] = ArrayBuffer[Predicate]()

  if (!queryWhere.isEmpty) {

    // if single where condition
    queryWhere.get() match {
      case expression: ComparisonExpression =>

        predicates += Predicate(expression.getLeft.toString, ComparisonOperators.withName(expression.getType.toString), expression.getRight.toString.substring(1, expression.getRight.toString.length - 1))
      case _ =>
        queryLogicalExpressions = queryWhere.get().asInstanceOf[LogicalBinaryExpression]

        breakable {
          while (true) {

            queryLogicalExpressions.getRight match {
              case expression: ComparisonExpression =>
                // dealing with RHS
                val RHS: ComparisonExpression = expression
                predicates += Predicate(RHS.getLeft.toString, ComparisonOperators.withName(RHS.getType.toString), RHS.getRight.toString.substring(1, RHS.getRight.toString.length - 1))
              case _ =>
            }

            try {
              queryLogicalExpressions.getLeft match {
                case expression: ComparisonExpression =>
                  val LHS: ComparisonExpression = expression
                  predicates += Predicate(LHS.getLeft.toString, ComparisonOperators.withName(LHS.getType.toString), LHS.getRight.toString.substring(1, LHS.getRight.toString.length - 1))
                  break
                case _ =>
              }
            } catch {
              case c: ControlThrowable => throw c
              case t: Throwable => t.printStackTrace()
            }


            queryLogicalExpressions = queryLogicalExpressions.getLeft.asInstanceOf[LogicalBinaryExpression]

          }
        }
    }



  }
  else {

  }

  def setInterval(interval: Double): Unit = {
    _interval = interval
  }

  override def toString: String = {
    query + " " + predicates.toList.toString()
  }

  def clear: Unit = {
    _interval = 0
  }
}
