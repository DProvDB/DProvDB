package edu.DProvDB.Utils

import org.apache.commons.math3.special.Erf

object AnalyticGM {
  private[this] def Phi(t: Double): Double = 0.5 * (1.0 + Erf.erf(t/math.sqrt(2.0)))

  private[this] def caseA(epsilon: Double, s: Double): Double = Phi(math.sqrt(epsilon * s)) - math.exp(epsilon) * Phi(-math.sqrt(epsilon * (s + 2.0)))

  private[this] def caseB(epsilon: Double, s: Double): Double = Phi(-math.sqrt(epsilon * s)) - math.exp(epsilon) * Phi(-math.sqrt(epsilon * (s + 2.0)))

  private[this] def doubling_trick(predicate_stop: Double => Boolean, s_inf: Double, s_sup: Double): (Double, Double) = {
    var var_inf = s_inf
    var var_sup = s_sup
    while (! predicate_stop(var_sup)) {
      var_inf = var_sup
      var_sup = 2.0 * var_inf
    }

    (var_inf, var_sup)
  }

  private[this] def binary_search(predicate_stop: Double => Boolean, predicate_left: Double => Boolean, s_inf: Double, s_sup: Double): Double = {
    var var_inf = s_inf
    var var_sup = s_sup
    var var_mid = var_inf + (var_sup - var_inf) / 2.0
    while (!predicate_stop(var_mid)) {
      if (predicate_left(var_mid)) {
        var_sup = var_mid
      }
      else {
        var_inf = var_mid
      }
      var_mid = var_inf + (var_sup - var_inf) / 2.0
    }
    var_mid
  }

  def calibrateAnalyticGaussianMechanism(epsilon: Double, delta: Double, GS: Int, tol: Double = 1e-12): Double = {
    var delta_thr = caseA(epsilon, 0.0)
    var sigma: Double = 0.0
    var alpha: Double = 0.0

    var predicate_stop_DT: Double => Boolean = null
    var function_s_to_delta: Double => Double = null
    var predicate_left_BS: Double => Boolean = null
    var function_s_to_alpha: Double => Double = null
//    var predicate_stop_BS: Double => Boolean = null

    if (delta == delta_thr) {
      alpha = 1.0
    }
    else {
      if (delta > delta_thr) {
        predicate_stop_DT = (s: Double) => caseA(epsilon, s) >= delta
        function_s_to_delta = (s: Double) => caseA(epsilon, s)
        predicate_left_BS = (s: Double) => function_s_to_delta(s) > delta
        function_s_to_alpha = (s: Double) => math.sqrt(1.0 + s/2.0) - math.sqrt(s/2.0)
      }
      else {
        predicate_stop_DT = (s: Double) => caseB(epsilon, s) <= delta
        function_s_to_delta = (s: Double) => caseB(epsilon, s)
        predicate_left_BS = (s: Double) => function_s_to_delta(s) < delta
        function_s_to_alpha = (s: Double) => math.sqrt(1.0 + s/2.0) + math.sqrt(s/2.0)
      }

      var predicate_stop_BS = (s: Double) => math.abs(function_s_to_delta(s) - delta) <= tol

      var (s_inf, s_sup) = doubling_trick(predicate_stop_DT, 0.0, 1.0)
      var s_final = binary_search(predicate_stop_BS, predicate_left_BS, s_inf, s_sup)
      alpha = function_s_to_alpha(s_final)
    }

    sigma = alpha * GS / math.sqrt(2.0 * epsilon)
    sigma
  }
}
