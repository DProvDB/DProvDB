package edu
package DProvDB.Mechanisms

import DProvDB.Model.{Analyst, NoisyView, Query, Synopsis, View}
import DProvDB.Provenance.ProvenanceTable
import DProvDB.Utils.{AnalyticGM, ProvenanceUtils}
import chorus.sql.QueryParser
import chorus.util.DB

class Chorus (provTable: ProvenanceTable, compositionMethod: String = "basic",
              delta: Double = 1e-5, alpha: Int = 2) extends Mechanism (provTable, compositionMethod, delta, alpha) {


  def run(query: Query, analyst: Analyst, view: View, epsilon: Double, status: String): Synopsis = {

    val synopsis = new Synopsis(view._viewID, view)

    if (status.equals("Pass")) {
      privacyAccount(epsilon)


      // for debug only
      val consumedBudget = provTable.getEntry(analyst.id, view._viewID)
      provTable.updateEntry(analyst.id, view._viewID, epsilon + consumedBudget)

      val histogramSensitivity = _state._DPFlag match {
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


  def checkConstraints(analystID: Int, viewID: Int, epsilon: Double, nonCachedQueries: Int): String = {
    if (immediateAccountant(getConsumedBudget, epsilon) > _overallBudget || nonCachedQueries * delta > provTable._deltaConstraint)
      "Fail"
    else
      "Pass"

  }

  def privacyTranslation(querySens: Int, accuracyReq: Double, provTable: ProvenanceTable, analyst: Analyst, view: View,
                         bins: Int = 1, precision: Double = 1e-7): Double = {
    val maxEpsilon = _overallBudget

    val _accuracyReq = accuracyReq
    println(s"--Chorus: Target sigma  ${_accuracyReq}.--")

    ProvenanceUtils.searchForEpsilonBinary(0.0, maxEpsilon, precision, _accuracyReq, _delta, querySens)
  }

}
