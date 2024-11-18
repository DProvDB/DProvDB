package edu
package DProvDB.Mechanisms

import chorus.mechanisms.{AdvancedCompositionAccountant, EpsilonCompositionAccountant, EpsilonDPCost, EpsilonDeltaDPCost, PrivacyAccountant, PrivacyCost, RenyiCompositionAccountant, RenyiDPCost}
import edu.DProvDB.Model.{Analyst, NoisyView, Query, View}
import edu.DProvDB.Provenance.{ProvenanceTable, analystTuple, viewTuple}
import edu.DProvDB.State


abstract class Mechanism (provTable: ProvenanceTable, compositionMethod: String = "basic", delta: Double = 1e-5,
                          alpha: Int = 2) {

  val _overallDelta: Double = delta

  val _delta: Double = _overallDelta

  val _alpha: Int = alpha

  var _consumedBudget: Double = 0

  val _overallBudget: Double = provTable._tableConstraint.getOrElse(Double.NaN)

  var _accountant: PrivacyAccountant = _

  var _logger: String = "debug"

  var _state: State = _

  compositionMethod match {
    case "basic" => _accountant = new EpsilonCompositionAccountant()
    case "advanced" => _accountant = new AdvancedCompositionAccountant(_delta)
    case "renyi" => _accountant = new RenyiCompositionAccountant()
    case _ => println("Not implemented accountant: " + _)
  }

  /**
   * Func: run mechanism
   * Return a noisy view to answer the query;
   * if status is "Pass", then account the privacy budget usage.
   *
   * This function is only called if status is "Pass" or "Cache".
   */
  def run (query: Query, analyst: Analyst, view: View, epsilon: Double, status: String): NoisyView

  /**
   * Func: Check Provenance Constraints
   * Return a status string for the results of checking constraints:
   *
   * "Unknown": default status: remain for handling unknown system issues;
   * "Cache": if the query can be answered by cached views;
   * "Pass": if the query can be answered, but it requires view updates or generation;
   * "Fail": if any constraint is violated and the query should be rejected.
   */
  def checkConstraints(analystID: Int, viewID: Int, epsilon: Double, nonCachedQueries: Int): String

  /**
   * Func: Privacy Translation
   * Takes in the query accuracy requirement and the translation precision;
   * Returns the minimal and closest (within the precision) privacy budget to answer the query.
   *
   * Built-in methods:
   * Line-search
   * Binary-search
   */
  def privacyTranslation(querySens: Int, accuracyReq: Double, provTable: ProvenanceTable, analyst: Analyst, view: View,
                         bins: Int = 1, precision: Double = 1e-7): Double

  /**
   * Account privacy according to different composition methods.
   * This function is to add a privacy cost to the overall consumed budget.
   */
  def privacyAccount (epsilon: Double, delta: Double = _delta, alpha: Int = _alpha): Unit = {
    compositionMethod match {
      case "basic" => _accountant.addCost(EpsilonDPCost(epsilon))
      case "advanced" => _accountant.addCost(EpsilonDPCost(epsilon))
      case "renyi" => _accountant.addCost(RenyiDPCost(alpha, epsilon))
      case _ => println("Not implemented accountant: " + _)
    }
  }

  /**
   * Privacy accountant over a given list of certain types.
   *
   * We provide three types of lists to be accounted:
   * Row over the provenance table, which is a list of view tuples, i.e., (viewID, budget[viewID, _]);
   * Column over the provenance table, which is a list of analyst tuples, i.e., (analystID, budget[_, analystID]);
   * Column maximums, which is a list of privacy budget, each of them being the maximum over a column.
   */
  def privacyAccountOverList[T] (list: List[T], rowOrCol: String, delta: Double = _delta, alpha: Int = _alpha): Double = {

    val listOfBudgetsToAccount = rowOrCol match {
      case "row" =>
        list map { tuple => tuple.asInstanceOf[viewTuple].consumedBudget }
      case "col" =>
        list map { tuple => tuple.asInstanceOf[analystTuple].consumedBudget}
      case "colMax" =>
        list map { eps => eps.asInstanceOf[Double] }
    }

    var newAccountant: PrivacyAccountant = null

    compositionMethod match {
      case "basic" =>
        newAccountant = new EpsilonCompositionAccountant()
        for (budget <- listOfBudgetsToAccount)
          newAccountant.addCost(EpsilonDPCost(budget))
        newAccountant.getTotalCost().asInstanceOf[EpsilonDPCost].epsilon
      case "advanced" =>
        newAccountant = new AdvancedCompositionAccountant(_delta)
        for (budget <- listOfBudgetsToAccount)
          newAccountant.addCost(EpsilonDPCost(budget))
        newAccountant.getTotalCost().asInstanceOf[EpsilonDeltaDPCost].epsilon
      case "renyi" =>
        newAccountant = new RenyiCompositionAccountant()
        for (budget <- listOfBudgetsToAccount)
          newAccountant.addCost(RenyiDPCost(alpha, budget))
        newAccountant.getTotalCost().asInstanceOf[RenyiDPCost].epsilon
      case _ => throw new IllegalArgumentException
    }

  }

  /**
   * Composition function on privacy accountant.
   * Return the composition of two privacy budgets.
   */
  def immediateAccountant (epsilon1: Double, epsilon2: Double, delta: Double = _delta, alpha: Int = _alpha): Double = {
    var newAccountant: PrivacyAccountant = null

    compositionMethod match {
      case "basic" =>
        newAccountant = new EpsilonCompositionAccountant()
        newAccountant.addCost(EpsilonDPCost(epsilon1))
        newAccountant.addCost(EpsilonDPCost(epsilon2))
        newAccountant.getTotalCost().asInstanceOf[EpsilonDPCost].epsilon
      case "advanced" =>
        newAccountant = new AdvancedCompositionAccountant(_delta)
        newAccountant.addCost(EpsilonDPCost(epsilon1))
        newAccountant.addCost(EpsilonDPCost(epsilon2))
        newAccountant.getTotalCost().asInstanceOf[EpsilonDeltaDPCost].epsilon
      case "renyi" =>
        newAccountant = new RenyiCompositionAccountant()
        newAccountant.addCost(RenyiDPCost(alpha, epsilon1))
        newAccountant.addCost(RenyiDPCost(alpha, epsilon2))
        newAccountant.getTotalCost().asInstanceOf[RenyiDPCost].epsilon
      case _ => throw new IllegalArgumentException
    }
  }

  /**
   * This function is to get the overall privacy budget consumption till the time the method is executed.
   */
  def getConsumedBudget: Double = {
    compositionMethod match {
      case "basic" => _accountant.getTotalCost().asInstanceOf[EpsilonDPCost].epsilon
      case "advanced" => _accountant.getTotalCost().asInstanceOf[EpsilonDeltaDPCost].epsilon
      case "renyi" => _accountant.getTotalCost().asInstanceOf[RenyiDPCost].epsilon
      case _ => throw new IllegalArgumentException
    }
  }

  def setState(state: State): Unit = {
    _state = state
  }


}
