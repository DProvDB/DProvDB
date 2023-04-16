package edu
package DProvDB.Mechanisms

import DProvDB.Model.{Analyst, Query, Synopsis, View}
import DProvDB.Provenance.ProvenanceTable
import DProvDB.Utils.ProvenanceUtils
import chorus.mechanisms.EpsilonDPCost
import chorus.sql.QueryParser
import chorus.util.DB
import edu.DProvDB.State

class ChorusWithProvenance (state: State, provTable: ProvenanceTable, compositionMethod: String = "basic",
                            delta: Double = 1e-5, alpha: Int = 2) extends Mechanism (provTable, compositionMethod, delta, alpha) {



  def run(query: Query, analyst: Analyst, view: View, epsilon: Double, status: String): Synopsis = {

    println(s"--ChorusP: run epsilon=$epsilon.--")

    // generate a noisy view
    val synopsis = new Synopsis(view._viewID, view)

    if (status.equals("Pass")) {
      privacyAccount(epsilon)

      // update the provenance table
      val consumedBudget = provTable.getEntry(analyst.id, view._viewID)

      provTable.updateEntry(analyst.id, view._viewID, epsilon + consumedBudget)

      val histogramSensitivity = state._DPFlag match {
        case "unbounded" => 1
        case "bounded" => 2
        case _ => throw new IllegalArgumentException()
      }

      val root = QueryParser.parseToRelTree(query.queryString, _state._database)

      val res = DB.execute(root, _state._database).head.vals.head.toDouble + chorus.mechanisms.BasicMechanisms.laplaceSample(histogramSensitivity / epsilon)

      synopsis._aggregationQueryAnswer = res
    }


    synopsis
  }

  /**
   * In ChorusP, we do not have cached views.
   * In order to use the DProvDB infra, we init the provenance table but not check the column constraints.
   */
  def checkConstraints(analystID: Int, viewID: Int, epsilon: Double): String = {

//    var status: String = "Unknown"

    val cost = immediateAccountant(_accountant.getTotalCost().asInstanceOf[EpsilonDPCost].epsilon, epsilon)

    // check table-level constraint
    if (cost > provTable._tableConstraint.getOrElse(throw new IllegalStateException("Table constraint does not exist!"))) {
      return "Fail"
    }

    // get current row cost
    val rowCost = immediateAccountant(privacyAccountOverList(provTable.getRow(analystID), "row"), epsilon)

    // check row-level constraint
    if (rowCost > provTable._analystConstraints.getOrElse(throw new IllegalStateException(s"Analyst constraint (for analyst $analystID) does not exist!"))(analystID)) {
      return "Fail"
    }

    "Pass"
  }

  def privacyTranslation(querySens: Int, accuracyReq: Double, provTable: ProvenanceTable, analyst: Analyst, view: View,
                         bins: Int = 1, precision: Double = 1e-7): Double = {
    val maxEpsilon = provTable._analystConstraints.getOrElse(throw new IllegalStateException(s"Analyst constraint (for analyst ${analyst.id}) does not exist!"))(analyst.id)

    val _accuracyReq = accuracyReq
    println(s"--ChorusP Privacy Translation: Target sigma ${_accuracyReq}.--")

    ProvenanceUtils.searchForEpsilonBinary(0.0, maxEpsilon, precision, _accuracyReq, _delta, querySens)
  }

}
