package edu
package DProvDB.Provenance

import DProvDB.Model.{Analyst, View}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

case class viewTuple(viewID: Int, var consumedBudget: Double) {

  def update(epsilon: Double): (Double, Double) = {

    val originalBudget = consumedBudget

    consumedBudget = epsilon

    (originalBudget, consumedBudget)
  }

}

case class analystTuple(analystID: Int, var consumedBudget: Double) {

  def update(epsilon: Double): (Double, Double) = {

    val originalBudget = consumedBudget

    consumedBudget = epsilon

    (originalBudget, consumedBudget)
  }
}

class ProvenanceTable (views: List[View], analysts: List[Analyst]) {

  var _tableConstraint: Option[Double] = None
  var _viewConstraints: Option[List[Double]] = None
  var _analystConstraints: Option[List[Double]] = None

  var _consumedBudget = 0


  val viewCount: Int = views.length
  val analystCount: Int = analysts.length

  println("set up provT: no of views, no of analysts", viewCount, analystCount)

  // index by viewID
  var _viewControlList: Map[Int, ArrayBuffer[analystTuple]] = Map[Int, ArrayBuffer[analystTuple]]()

  // index by analystID
  var _analystCapacityList: Map[Int, ArrayBuffer[viewTuple]] = Map[Int, ArrayBuffer[viewTuple]]()

  initProvTable ()

  // TODO: index by ID, not by the numbering
  private def initProvTable(): Unit = {
    // index starting from 0
    for (i <- 0 until viewCount) {
      var initCol = ArrayBuffer[analystTuple]()
      for (j <- 0 until analystCount) {
        initCol.+=(analystTuple(j, 0.0))
      }
      _viewControlList += (i -> initCol)
    }

    for (j <- 0 until analystCount) {
      var initRow = ArrayBuffer[viewTuple]()
      for (i <- 0 until viewCount) {
        initRow.+=(viewTuple(i, 0.0))
      }
      _analystCapacityList += (j -> initRow)
    }
  }

  def setConstraints(tableConstraint: Double, viewConstraints: List[Double], analystConstraints: List[Double]): Unit = {
    _tableConstraint = Some(tableConstraint)
    _viewConstraints = Some(viewConstraints)
    _analystConstraints = Some(analystConstraints)
  }

  def setTableConstraint(tableConstraint: Double): Unit = {
    _tableConstraint = Some(tableConstraint)
  }

  def setViewConstraints(viewConstraints: List[Double]): Unit = {
    _viewConstraints = Some(viewConstraints)
  }

  def setAnalystConstraints(analystConstraints: List[Double]): Unit = {
    _analystConstraints = Some(analystConstraints)
  }


  def updateEntry (analystID: Int, viewID: Int, updateTo: Double): Unit = {

    _analystCapacityList(analystID).find(viewTuple => viewTuple.viewID == viewID).toList.head.update(updateTo)
    _viewControlList(viewID).find(analystTuple => analystTuple.analystID == analystID).toList.head.update(updateTo)
  }

  def getRow (analystID: Int): List[Any] = {
    _analystCapacityList(analystID).toList
  }

  def getCol (viewID: Int): List[Any] = {
    _viewControlList(viewID).toList
  }

  def getEntry (analystID: Int, viewID: Int): Double = {

    if (_analystCapacityList(analystID).find(viewTuple => viewTuple.viewID == viewID).toList.head.consumedBudget !=
      _viewControlList(viewID).find(analystTuple => analystTuple.analystID == analystID).toList.head.consumedBudget) {
      throw new Exception("Provenance Table not consistent.")
    }
    _analystCapacityList(analystID).find(viewTuple => viewTuple.viewID == viewID).toList.head.consumedBudget

  }

  def loopOverTable (): List[Double] = {
    val budgetTable: mutable.MutableList[Double] = mutable.MutableList()

    // index starting from 0
    for (i <- 0 until viewCount) {
      val col = _viewControlList(i)
      for (j <- 0 until analystCount) {
        budgetTable += col(j).consumedBudget
      }
    }

    budgetTable.toList

  }

  /**
   * Find the maximum privacy budget consumption for each column in the provenance table.
   */
  def columnMax (): List[Double] = {
    val budgetOnColumnMax: mutable.MutableList[Double] = mutable.MutableList()

    for (i <- 0 until viewCount) {
      val col = _viewControlList(i)
      val colBudgetList =  col map {
        analystTuple => analystTuple.consumedBudget
      }
      budgetOnColumnMax += colBudgetList.max
    }

    budgetOnColumnMax.toList
  }

  /**
   * Given a column ID (i.e., view ID), find the maximum privacy budget consumption in that column.
   */
  def columnMax (viewID: Int): Double = {
    val col = _viewControlList(viewID)
    val colBudgetList =  col map {
      analystTuple => analystTuple.consumedBudget
    }
    colBudgetList.max
  }

  def columnSum (): List[Double] = {

    val budgetOnColumnSum: mutable.MutableList[Double] = mutable.MutableList()

    for (i <- 0 until viewCount) {
      val col = _viewControlList(i)
      val colBudgetList = col map {
        analystTuple => analystTuple.consumedBudget
      }
      budgetOnColumnSum += colBudgetList.sum
    }

    budgetOnColumnSum.toList

  }
}
