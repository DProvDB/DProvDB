package edu
package DProvDB.Model


/**
 * Analyst class:
 *
 * Privilege: represents the privilege level of this analyst, on the scale of [1, 10]; external analyst has lower
 * privilege level than internal analyst.
 *
 * Privilege Constraint: the row constraint according to this analyst in the provenance table, i.e., the maximum
 * privacy budget this analyst can use.
 *
 * Query Answered Count: the #queries from this analyst being answered.
 * Query Submitted Count: the #queries from this analyst being submitted to the system.
 *
 */
case class Analyst(id: Int, userName: String, privilege: Int) {

  var queryAnsweredCount: Int = 0

  var querySubmittedCount: Int = 0

  var _privilegeConstraint: Double = _

  def setPrivilegeConstraint(constraint: Double): Unit = {
    _privilegeConstraint = constraint
  }

  def queryAnsweredCounter(): Unit = {
    queryAnsweredCount += 1
  }

  def querySubmittedCounter(): Unit = {
    querySubmittedCount += 1
  }

  override def toString: String = {
    id + ", privilege " + privilege + ", queries " + queryAnsweredCount
  }

  def clear(): Unit = {
    queryAnsweredCount = 0
  }
}