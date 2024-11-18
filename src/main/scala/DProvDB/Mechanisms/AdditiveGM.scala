package edu
package DProvDB.Mechanisms

import DProvDB.Model.{Analyst, GlobalSynopsis, LocalSynopsis, Query, View}
import breeze.stats.distributions.{Gaussian, RandBasis, ThreadLocalRandomGenerator}
import edu.DProvDB.Provenance.{ProvenanceTable}
import edu.DProvDB.State
import edu.DProvDB.Utils.{AnalyticGM, ProvenanceUtils, ViewUtils}
import edu.DProvDB.ViewManager.ViewManager
import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.{BrentOptimizer, SearchInterval, UnivariateObjectiveFunction}
import org.apache.commons.math3.random.MersenneTwister

import scala.collection.mutable

class AdditiveGM (state: State, provTable: ProvenanceTable, analysts: List[Analyst], views: List[View], compositionMethod: String = "basic", delta: Double = 1e-5,
                  alpha: Int = 2) extends Mechanism (provTable, compositionMethod, delta, alpha) {

  private val _globalSynopses: mutable.MutableList[GlobalSynopsis] = mutable.MutableList()
  private val _localSynopses: mutable.MutableList[LocalSynopsis] = mutable.MutableList()

  // init Global and Local synopses
  for (view <- views) {
    _globalSynopses += new GlobalSynopsis(view._viewID, view)
    for (analyst <- analysts) {
      _localSynopses += new LocalSynopsis(analyst.id, view._viewID, view)
    }
  }

  def run (query: Query, analyst: Analyst, view: View, epsilon: Double, status: String): LocalSynopsis = {

    println("additiveGM: run epsilon=", epsilon)
    val viewManager = new ViewManager()

    val globalSynopsis = ViewUtils.getGlobalSynopsis(_globalSynopses.toList, view._viewID)
    val localSynopsis = ViewUtils.getLocalSynopsis(_localSynopses.toList, view._viewID, analyst.id)

    var globalAcc: Double = 0

    val histogramSensitivity = state._DPFlag match {
      case "unbounded" => 1
      case "bounded" => 2
      case _ => throw new IllegalArgumentException()
    }

    val sigma = AnalyticGM.calibrateAnalyticGaussianMechanism(epsilon, delta, histogramSensitivity)
    val newHistogram = view._flatTable map {
      cell => cell + Gaussian(0, sigma)(new RandBasis(new ThreadLocalRandomGenerator(new MersenneTwister()))).draw()
    }

    // first time asking this query
    if (globalSynopsis._epsilon == Double.PositiveInfinity) {
      globalSynopsis.updateEpsilon(epsilon)
      globalSynopsis.updateHistogram(newHistogram)
      globalSynopsis._variance = sigma
      globalAcc = epsilon
    }
    else {
      if (epsilon > globalSynopsis._epsilon) {
        globalAcc = epsilon - globalSynopsis._epsilon
        val oldSigma = math.max(AnalyticGM.calibrateAnalyticGaussianMechanism(globalSynopsis._epsilon, delta, histogramSensitivity), math.sqrt(globalSynopsis._variance))
        val tuple = viewManager.updateNoisyHistogram(globalSynopsis._flatTable, newHistogram, oldSigma, sigma)
        globalSynopsis.updateEpsilon(epsilon)
        globalSynopsis.updateHistogram(tuple._1)
        globalSynopsis._variance = tuple._2
      }
    }

    // first time this analyst asking this query
    if (localSynopsis._epsilon == Double.PositiveInfinity) {

      // aGM to update local synopsis
      localSynopsis.updateEpsilon(epsilon)
      localSynopsis.updateHistogram(aGM(globalSynopsis._flatTable, histogramSensitivity, globalSynopsis._epsilon, epsilon))

    }
    else {
      if (epsilon > localSynopsis._epsilon) {
        // first use agm to generate a new synopsis
        val freshNoisyHistogram = aGM(view._flatTable, histogramSensitivity, globalSynopsis._epsilon, epsilon)

        // then combine with the previous one for boosting accuracy
        localSynopsis.updateEpsilon(epsilon)
        val oldSigma = AnalyticGM.calibrateAnalyticGaussianMechanism(localSynopsis._epsilon, delta, histogramSensitivity)
        val tuple = viewManager.updateNoisyHistogram(localSynopsis._flatTable, freshNoisyHistogram, oldSigma, sigma)
        localSynopsis.updateHistogram(tuple._1)
      }

    }

    if (globalAcc > 0)
      privacyAccount(globalAcc)


    if (status.equals("Pass")) {
      val epsilonPrev = provTable.getEntry(analyst.id, view._viewID)

      // update provenance table
      val newEps = math.min(epsilon + epsilonPrev, globalSynopsis._epsilon)
      provTable.updateEntry(analyst.id, view._viewID, newEps)


    }

    localSynopsis
  }

  def aGM (noisyAnswer: List[Double], querySens: Int, previousEpsilon: Double, targetEpsilon: Double): List[Double] = {

    if (targetEpsilon <= 0) {
      return noisyAnswer
    }
    val previousSigma: Double = AnalyticGM.calibrateAnalyticGaussianMechanism(previousEpsilon, delta, querySens)
    val targetSigma: Double = AnalyticGM.calibrateAnalyticGaussianMechanism(targetEpsilon, delta, querySens)

    noisyAnswer map {
      cell => cell + Gaussian(0, math.sqrt(math.pow(targetSigma, 2) - math.pow(previousSigma, 2)))(new RandBasis(new ThreadLocalRandomGenerator(new MersenneTwister()))).draw()
    }

  }

  def checkConstraints (analystID: Int, viewID: Int, epsilon: Double, nonCachedQueries: Int): String = {

    val globalSynopsis = ViewUtils.getGlobalSynopsis(_globalSynopses.toList, viewID)

    val epsilonPrev = provTable.getEntry(analystID, viewID)

    val diff = math.min(globalSynopsis._epsilon, epsilon + epsilonPrev) - epsilonPrev

    println(s"--AdditiveGM: check constraints.--")

    // accuracy not increase
    if (globalSynopsis._epsilon != Double.PositiveInfinity && globalSynopsis._epsilon >= epsilon) {
      return "Cache"
    }

    // check table-level constraint
    val cost = immediateAccountant(privacyAccountOverList(provTable.columnMax(), "colMax"), diff)

    if (cost > provTable._tableConstraint.getOrElse(throw new IllegalStateException("Table constraint does not exist!"))) {
      return "Fail"
    }

    // get current column cost
    val colCost = immediateAccountant(provTable.columnMax(viewID), diff)

    // check column-level constraint
    if (colCost > provTable._viewConstraints.getOrElse(throw new IllegalStateException(s"View constraint (for analyst $viewID) does not exist!"))(viewID)) {
      return "Fail"
    }


    // get current row cost
    val rowCost = immediateAccountant(privacyAccountOverList(provTable.getRow(analystID), "row"), diff)

    // check row-level constraint
    if (rowCost > provTable._analystConstraints.getOrElse(throw new IllegalStateException(s"Analyst constraint (for analyst $analystID) does not exist!"))(analystID)) {
      return "Fail"
    }

//    provTable.updateEntry(analystID, viewID, diff + epsilonPrev)

    if (nonCachedQueries * delta > provTable._deltaConstraint)
      return "Fail"

    "Pass"
  }

  def privacyTranslation (querySens: Int, accuracyReq: Double, provTable: ProvenanceTable, analyst: Analyst, view: View,
                          bins: Int = 1, precision: Double = 1e-7): Double = {
    val maxEpsilon = provTable._analystConstraints.getOrElse(List(Double.NaN))(analyst.id)

    // get previous sigma
    val epsilonPrev = provTable.getEntry(analyst.id, view._viewID)

    println(s"--AdditiveGM privacy translation == Accuracy requirement: $accuracyReq, Previous epsilon: $epsilonPrev.--")

    var sigmaTarget: Double = 0.0

    val globalSynopsis = ViewUtils.getGlobalSynopsis(_globalSynopses.toList, view._viewID)

    if (globalSynopsis._epsilon != Double.PositiveInfinity) {
      val variancePrev = globalSynopsis._variance

      println(s"--AdditiveGM privacy translation == Previous variance: $variancePrev.--")

      val accTarget = accuracyReq / bins.toDouble
      if (variancePrev <= accTarget) {
        return ProvenanceUtils.searchForEpsilonBinary(0.0, maxEpsilon, precision, accTarget, _delta, view._viewSens)
      }

      // run minimizer
      val goal = GoalType.MINIMIZE
      val interval = new SearchInterval(0,1)
      val funcToMinimize: UnivariateFunction = (x: Double) =>  - (accuracyReq - math.pow(x, 2) * variancePrev) / math.pow(1-x, 2)

      val optimizer = new BrentOptimizer(0.01, 0.01)
      val objective = new UnivariateObjectiveFunction(funcToMinimize)

      val result = optimizer.optimize(objective, goal, interval, MaxEval.unlimited)

      val root = result.getPoint

      // target error bound
      val varianceTarget: Double = (accuracyReq - math.pow(root, 2) * variancePrev) / math.pow(1-root, 2)
      sigmaTarget = math.sqrt(varianceTarget)

      println(s"--AdditiveGM privacy translation == varianceTarget: $varianceTarget, optimization root: ${result.getValue}.--")
    }
    else {
      // first time translation
      sigmaTarget = math.sqrt(accuracyReq)
    }

    val accTarget = math.pow(sigmaTarget, 2) / bins.toDouble
    println(s"--AdditiveGM: Target accuracy: $accTarget.--")


//    ProvenanceUtils.searchForEpsilonLine(0.0, maxEpsilon, _precision, accTarget, _delta, sens)
    ProvenanceUtils.searchForEpsilonBinary(0.0, maxEpsilon, precision, accTarget, _delta, view._viewSens)
  }
}
