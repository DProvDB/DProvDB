package edu

import edu.DProvDB.Utils.ProvenanceUtils
import junit.framework.TestCase

class BudgetSearchTests extends TestCase {

  val _precision: Double = 1e-7
  val _delta: Double = 1e-5

  override def setUp(): Unit = super.setUp()

  def testBinary: Unit = {
    println("unit test binary search")
    val eps = ProvenanceUtils.searchForEpsilonBinary(0.0, 1.0, _precision, 200, _delta, 3)

    println(eps)
  }

  def testLine: Unit = {
    println("unit test line search")
    val eps = ProvenanceUtils.searchForEpsilonLine(0.0, 1.0, _precision, 200, _delta, 3)

    println(eps)
  }

  def testScientific: Unit = {
    println(1e-5)
    println(1E-5)
    println(10E-5)
  }
}
