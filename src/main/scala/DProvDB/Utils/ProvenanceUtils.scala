package edu
package DProvDB.Utils

import DProvDB.Provenance.ProvenanceTable

import edu.DProvDB.Model.{Analyst, View}

import scala.collection.mutable

object ProvenanceUtils {
  def setConstraintStatic(provT: ProvenanceTable, analysts: List[Analyst], views: List[View], overallBudget: Double = Double.PositiveInfinity, noOfViews:Int): Unit = {

    val tableConstraint = overallBudget
    provT.setTableConstraint(tableConstraint)

    val sensNormalization = views.map(view => view._viewSens).sum
    val viewConstraints = views.map {view => tableConstraint / noOfViews * 1}
    provT.setViewConstraints(viewConstraints)

    val analystConstraints = analysts.map {analyst => analyst._privilegeConstraint}
    provT.setAnalystConstraints(analystConstraints)

  }

  def setConstraintDynamic(provT: ProvenanceTable, analysts: List[Analyst], views: List[View], overallBudget: Double): Unit = {
    provT.setTableConstraint(overallBudget)

    val analystConstraints = analysts map {analyst => analyst._privilegeConstraint}
    provT.setAnalystConstraints(analystConstraints)

    provT.setViewConstraints(views map { _ => overallBudget })

  }

  def searchForEpsilonBinary(lower: Double, upper: Double, precision: Double, accuracyReq: Double, delta: Double,
                             querySens: Int): Double = {

    if (upper == Double.NaN)
      return Double.NaN

    // If epsilon upper bound cannot satisfy the accuracy requirement, then we reject the query
    if (math.pow(AnalyticGM.calibrateAnalyticGaussianMechanism(upper, delta, querySens), 2) > accuracyReq)
      return Double.PositiveInfinity

    var eps_max = upper
    var eps_min = lower

    while ( (eps_max - eps_min) > precision ) {
      val epsilon = (eps_max + eps_min) / 2

      val sigma = AnalyticGM.calibrateAnalyticGaussianMechanism(epsilon, delta, querySens)

      if (math.pow(sigma, 2) <= accuracyReq)
        eps_max = epsilon
      else
        eps_min = epsilon
    }

    eps_max
  }

  def searchForEpsilonLine(lower: Double, upper: Double, precision: Double, accuracyReq: Double, delta: Double,
                           querySens: Int): Double = {

    if (upper == Double.NaN)
      return Double.NaN

    val candidateEpsilons: mutable.MutableList[Double] = mutable.MutableList()

    val steps = math.abs(math.log10(precision).floor.toInt)

    val searchForEpsilons = {
      for (i <- 0 to steps) yield (upper-lower) / math.pow(2, i) + lower
    }

    for (eps <- searchForEpsilons) {
      val sigma = AnalyticGM.calibrateAnalyticGaussianMechanism(eps, delta, querySens)
      if (math.pow(sigma, 2) <= accuracyReq)
        candidateEpsilons += eps
    }

    if (candidateEpsilons.isEmpty)
      Double.PositiveInfinity
    else
      candidateEpsilons.min

  }
}
