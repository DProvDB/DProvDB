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

class BaselineMechanism (provTable: ProvenanceTable, compositionMethod: String = "basic", delta: Double = 1e-5,
                         alpha: Int = 2) extends Mechanism (provTable, compositionMethod, delta, alpha) {

  var noisyViewList: ListBuffer[NoisyView] = new ListBuffer[NoisyView]
  def run(query: Query, analyst: Analyst, view: View, epsilon: Double, status: String): NoisyView = {

    if (status.equals("Unknown") || status.equals("Fail"))
      throw new IllegalStateException()

    println(s"--Baseline: run epsilon=$epsilon.--")

    val viewManager = new ViewManager()

    if (status.equals("Pass")) {
      val noisyView = new NoisyView(view._viewID)
      noisyView.setAnalyst(analyst.id)

      // generate a noisy view
      noisyView.setNoisyHistogramView(viewManager.noisyHistogramGen(view, epsilon))

      // update currently consumed budget
      privacyAccount(epsilon)

      // update the provenance table
      provTable.updateEntry(analyst.id, view._viewID, epsilon + provTable.getEntry(analyst.id, view._viewID))

      noisyViewList += noisyView

      noisyView
    }
    else {
      noisyViewList.find(noisyView => noisyView._analystID == analyst.id && noisyView._viewID == view._viewID).orNull
    }
  }

  /***
   * Return a status string for the results of checking constraints:
   * "Unknown": default status
   * "Cache": if the query can be answered by cached views
   * "Pass": if the query can be answered, but it requires view updates or generation
   * "Fail": if any constraint is violated and the query should be rejected
   */
  def checkConstraints(analystID: Int, viewID: Int, epsilon: Double, nonCachedQueries: Int): String ={
//    var status: String = "Unknown"
    val epsilonPrev = provTable.getEntry(analystID, viewID)

    println("--Baseline: check constraints.--")
    // accuracy not increase, query can be answered with cached views
    if (epsilonPrev >= epsilon) {
      return "Cache"
    }


    // get current budget cost
    val cost = immediateAccountant(_accountant.getTotalCost().asInstanceOf[EpsilonDPCost].epsilon, epsilon)

    // check table-level constraint
    if (cost > provTable._tableConstraint.getOrElse(throw new IllegalStateException("Table constraint does not exist!"))) {
      return "Fail"
    }

    // get current column cost
    val colCost = immediateAccountant(privacyAccountOverList(provTable.getCol(viewID), "col"), epsilon)

    // check column-level constraint
    if (colCost > provTable._viewConstraints.getOrElse(throw new IllegalStateException(s"View constraint (for analyst $viewID) does not exist!"))(viewID)) {
      return "Fail"
    }

    // get current row cost
    val rowCost = immediateAccountant(privacyAccountOverList(provTable.getRow(analystID), "row"), epsilon)

    // check row-level constraint
    if (rowCost > provTable._analystConstraints.getOrElse(throw new IllegalStateException(s"Analyst constraint (for analyst $analystID) does not exist!"))(analystID)) {
      return "Fail"
    }

    if (nonCachedQueries * delta > provTable._deltaConstraint)
      return "Fail"

    "Pass"
  }

  def privacyTranslation(querySens: Int, accuracyReq: Double, provTable: ProvenanceTable, analyst: Analyst, view: View,
                         bins: Int = 1, precision: Double = 1e-7): Double = {
    val maxEpsilon = provTable._analystConstraints.getOrElse(throw new IllegalStateException(s"Analyst constraint (for analyst ${analyst.id}) does not exist!"))(analyst.id)

    val _accuracyReq = accuracyReq / bins.toDouble

    println(s"--Baseline == Target sigma: ${_accuracyReq}.--")


    ProvenanceUtils.searchForEpsilonBinary(0.0, maxEpsilon, precision, _accuracyReq, _delta, view._viewSens)
  }

}
