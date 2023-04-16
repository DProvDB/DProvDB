package edu.DProvDB.Mechanisms

import edu.DProvDB.Model.{Analyst, NoisyView, Query, View}
import edu.DProvDB.Provenance.ProvenanceTable
import edu.DProvDB.Utils.ProvenanceUtils
import edu.DProvDB.ViewManager.ViewManager

import scala.collection.mutable.ListBuffer

class PrivateSQL(provTable: ProvenanceTable, compositionMethod: String = "basic", delta: Double = 1e-5,
                 alpha: Int = 2) extends Mechanism (provTable, compositionMethod, delta, alpha) {

  var noisyViewList: ListBuffer[NoisyView] = new ListBuffer[NoisyView]
  def run(query: Query, analyst: Analyst, view: View, epsilon: Double, status: String): NoisyView = {

    if (status.equals("Unknown") || status.equals("Fail"))
      throw new IllegalStateException()

    println(s"--Simulating PrivateSQL: run epsilon=$epsilon.--")

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
   * "Pass": if the query can be answered, but it requires view updates or generation
   * "Fail": if any constraint is violated and the query should be rejected
   */
  def checkConstraints(analystID: Int, viewID: Int, epsilon: Double): String ={

    println("--Simulating PrivateSQL: check constraints.--")

    // check column-level constraint
    if (epsilon > provTable._viewConstraints.getOrElse(throw new IllegalStateException(s"View constraint (for analyst $viewID) does not exist!"))(viewID)) {
      return "Fail"
    }

    "Cache"
  }

  def privacyTranslation(querySens: Int, accuracyReq: Double, provTable: ProvenanceTable, analyst: Analyst, view: View,
                         bins: Int = 1, precision: Double = 1e-7): Double = {
    val maxEpsilon = provTable._viewConstraints.getOrElse(throw new IllegalStateException(s"Analyst constraint (for analyst ${view._viewID}) does not exist!"))(view._viewID)

    val _accuracyReq = accuracyReq / bins.toDouble

    println(s"--Simulating PrivateSQL == Target sigma: ${_accuracyReq}.--")


    ProvenanceUtils.searchForEpsilonBinary(0.0, maxEpsilon, precision, _accuracyReq, _delta, view._viewSens)
  }

}
