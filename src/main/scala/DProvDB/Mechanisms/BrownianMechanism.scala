package edu
package DProvDB.Mechanisms

import edu.DProvDB.Mechanisms.Mechanism
import DProvDB.Provenance.{ProvenanceTable, analystTuple, viewTuple}
import chorus.mechanisms.{EpsilonCompositionAccountant, EpsilonDPCost}
import edu.DProvDB.Model.{Analyst, NoisyView, Query, View}
import edu.DProvDB.Utils.{AnalyticGM, ProvenanceUtils, ViewUtils}
import edu.DProvDB.ViewManager.ViewManager

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import edu.DProvDB.Utils.{BrownianMotionUtil, BrownianMotion}

class BrownianNoise (view: View){
  val histogramView: View = view
  var highestEpsilon: Double = 0
  val brownianNoise: List[BrownianMotion] = List.fill(view.histogramView.size)(new BrownianMotion())

  def getNoisyView (epsilon: Double, delta: Double, sensitivity: Int, analystID: Int): (NoisyView, Double) = {
    val noisyView = new NoisyView(view._viewID)
    noisyView.setAnalyst(analystID)
    //println(s"--BrownianMechanism: run epsilon=$epsilon.--")
    val (time, epsilon_consumed) = BrownianMotionUtil.retrievePrivacy(epsilon, sensitivity, delta)
    highestEpsilon = Math.max(highestEpsilon, epsilon_consumed)

    val noisyHistogram = view.histogramView.zip(brownianNoise).map {
      case ((key: String, e: Double), b: BrownianMotion) =>
        (key, e + b.get(time))
    }
    //println(epsilon, sensitivity, delta)
    //println(time, epsilon_consumed)
    //println(view.histogramView)
    //println(noisyHistogram)
    // generate a noisy view
    noisyView.setNoisyHistogramView(noisyHistogram)

    (noisyView, epsilon_consumed)
  }
}

class BrownianMechanism (provTable: ProvenanceTable, compositionMethod: String = "basic", delta: Double = 1e-5) extends Mechanism (provTable, compositionMethod, delta) {

  var noisyViewList: ListBuffer[BrownianNoise] = new ListBuffer[BrownianNoise]

  def run(query: Query, analyst: Analyst, view: View, epsilon: Double, status: String): NoisyView = {

    if (status.equals("Unknown") || status.equals("Fail"))
      throw new IllegalStateException()

    println(s"--BrownianMechanism: run epsilon=$epsilon.--")

    val sensitivity = view._viewSens
    val lastBrownianNoise: Option[BrownianNoise] = noisyViewList.find(_.histogramView._viewID == view._viewID)
    lastBrownianNoise match {
      case Some(v) =>
        val (noisyView, epsilon_consumed) = v.getNoisyView(epsilon, delta, sensitivity, analyst.id)
        val epsilonPrev = provTable.getEntry(analyst.id, view._viewID)
        val newEps = math.max(epsilon_consumed, epsilonPrev)
        privacyAccount(if (newEps > epsilonPrev) newEps - epsilonPrev else 0)
        provTable.updateEntry(analyst.id, view._viewID, newEps)
        return noisyView
      case _ =>
        val v = new BrownianNoise(view)
        noisyViewList.append(v)
        val (noisyView, epsilon_consumed) = v.getNoisyView(epsilon, delta, sensitivity, analyst.id)
        privacyAccount(epsilon_consumed)
        provTable.updateEntry(analyst.id, view._viewID, epsilon_consumed)
        return noisyView
    }
    }


  /***
   * Return a status string for the results of checking constraints:
   * "Unknown": default status
   * "Cache": if the query can be answered by cached views
   * "Pass": if the query can be answered, but it requires view updates or generation
   * "Fail": if any constraint is violated and the query should be rejected
   */
  def checkConstraints(analystID: Int, viewID: Int, epsilon: Double): String ={
//    var status: String = "Unknown"
    println(s"--BrownianMechanism: check constraints.--")

    val lastBrownianNoise: Option[BrownianNoise] = noisyViewList.find(_.histogramView._viewID == viewID)
    var diff: Double = 0
    // accuracy not increase
    lastBrownianNoise match {
      case Some(v) =>
        if (v.highestEpsilon >= epsilon) {
          return "Cache"
        } else {
          diff = epsilon - v.highestEpsilon
        }
      case None =>
        diff = epsilon
    }

    // check table-level constraint
    val cost = immediateAccountant(privacyAccountOverList(provTable.columnMax(), "colMax"), diff)
    //println(privacyAccountOverList(provTable.columnMax(), "colMax"), diff, cost)
    if (cost > provTable._tableConstraint.getOrElse(throw new IllegalStateException("Table constraint does not exist!"))) {
      println("Table", privacyAccountOverList(provTable.columnMax(), "colMax"), diff, cost, provTable._tableConstraint.get)
      return "Fail"
    }

    // get current column cost
    val colCost = immediateAccountant(provTable.columnMax(viewID), diff)

    // check column-level constraint
    if (colCost > provTable._viewConstraints.getOrElse(throw new IllegalStateException(s"View constraint (for analyst $viewID) does not exist!"))(viewID)) {
      println("Column", colCost, provTable._viewConstraints.get(viewID))
      return "Fail"
    }


    // get current row cost
    val rowCost = immediateAccountant(privacyAccountOverList(provTable.getRow(analystID), "row"), diff)

    // check row-level constraint
    if (rowCost > provTable._analystConstraints.getOrElse(throw new IllegalStateException(s"Analyst constraint (for analyst $analystID) does not exist!"))(analystID)) {
      println("Row", rowCost,  provTable._analystConstraints.get(analystID))
      return "Fail"
    }

//    provTable.updateEntry(analystID, viewID, diff + epsilonPrev)
    "Pass"
  }

  def privacyTranslation(querySens: Int, accuracyReq: Double, provTable: ProvenanceTable, analyst: Analyst, view: View,
                         bins: Int = 1, precision: Double = 1e-7): Double = {
    //val maxEpsilon = provTable._analystConstraints.getOrElse(throw new IllegalStateException(s"Analyst constraint (for analyst ${analyst.id}) does not exist!"))(analyst.id)

    val _accuracyReq = accuracyReq / bins.toDouble

    println(s"--BrownianMechanism == Target sigma: ${_accuracyReq}.--")

    val (time, epsilon) = BrownianMotionUtil.retrieveAccuracy(_accuracyReq, view._viewSens, delta)

    if (epsilon > provTable._analystConstraints.getOrElse(List(Double.NaN))(analyst.id)) {
      Double.PositiveInfinity
    } else {
      epsilon
    }
  }
}
