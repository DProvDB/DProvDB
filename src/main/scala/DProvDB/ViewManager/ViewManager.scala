package edu.DProvDB.ViewManager

import breeze.numerics.pow
import breeze.stats.distributions.{Gaussian, RandBasis, ThreadLocalRandomGenerator}
import edu.DProvDB.Model.View
import edu.DProvDB.State
import edu.DProvDB.Utils.AnalyticGM.calibrateAnalyticGaussianMechanism
import edu.DProvDB.Utils.{DatasetUtils, ViewUtils}
import org.apache.commons.math3.random.MersenneTwister

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps


/**
 * View management class:
 * Provides the following functions:
 * 1) view set up: generating a plain view (data are unprotected);
 * 2) noisy view generation: adding noise to the plain view;
 * 3) update noisy view: using additive Gaussian mechanism to update views;
 * 4) given the query range (i.e, lb and ub), find the mapped view entry index.
 */
class ViewManager {

  var _state: State = _
  var no_of_buckets = 50 // by default

  var relation: String = _
  var columns: List[String] = _
  var attrTypeList: List[String] = _

  def setState(state: State): Unit = {
    _state = state

    relation = _state._tableName
    columns = DatasetUtils.getDatasetColumnNames(_state)
    attrTypeList = DatasetUtils.getDatabaseColumnType(_state)
  }

  def setBucketNum(bins: Int): Unit = {
    no_of_buckets = bins
  }

  /**
   * Histogram view.
   */
  def setupPlainViews(): List[View] = {

    if (_state == null)
      throw new IllegalStateException("State does not init.")

    val views: ArrayBuffer[View] = ArrayBuffer[View]()

    for (index <- columns.indices) {
      if (attrTypeList(index).equals("d")) {

        println(s"Setting up histogram for attribute(s) ${columns(index)}")


        val attrsDomain: ArrayBuffer[List[String]] = ArrayBuffer[List[String]]()

        attrsDomain += DatasetUtils.getDiscreteDomain(_state, columns(index))

        val view = ViewUtils.genHistogramDiscrete(index, attrsDomain.toList, List(columns(index)), _state._dbConfig, relation)
        view.setBins(1, attrsDomain map {domain => domain.size} product )

        _state._DPFlag match {
          case "bounded" => view.setViewSensitivity(2)
          case "unbounded" => view.setViewSensitivity(1)
          case _ => throw new IllegalArgumentException()
        }

        views += view
      }
      else if (attrTypeList(index).equals("c")) {

        println(s"Setting up histogram for attribute(s) ${columns(index)}")

        val tuple = DatasetUtils.getDomainUBLB(_state, columns(index))

        val domain_max = tuple.ub
        val domain_min = tuple.lb

        val view = ViewUtils.genHistogramContinuous(index, List(columns(index)), domain_min, domain_max, no_of_buckets, _state._dbConfig, relation)
        view.setBins((domain_max - domain_min) / no_of_buckets, no_of_buckets)
        view.setUBLB(domain_max, domain_min)

        _state._DPFlag match {
          case "bounded" => view.setViewSensitivity(2)
          case "unbounded" => view.setViewSensitivity(1)
          case _ => throw new IllegalArgumentException()
        }

        views += view

      }

    }

    views.toList
  }

  def noisyHistogramGen(view: View, epsilon: Double, DP_flag: String = "unbounded", delta: Double = 1e-6): Map[String, Double] = {

    var sens: Int = 0

    DP_flag match {
      case "unbounded" => sens = 1
      case "bounded" => sens = 2
      case _ => throw new IllegalArgumentException()
    }

    val sigma = calibrateAnalyticGaussianMechanism(epsilon, delta, sens)

    view.histogramView map {
      case (key, e) =>
        (key, e + Gaussian(0, sigma)(new RandBasis(new ThreadLocalRandomGenerator(new MersenneTwister()))).draw())
    }
  }

  def updateNoisyHistogram(noisyView1: List[Double], noisyView2: List[Double], sigma1: Double, sigmaAggregated: Double): (List[Double], Double) = {
    val v_t: Double = pow(sigma1, 2)
    val v_Delta: Double = pow(sigmaAggregated, 2)
    val w_t = v_t / (v_t + v_Delta)
    val w = 1 - w_t
    ((noisyView1 zip noisyView2).map {
      case (e1, e2) => e1 * w + e2 * w_t
    }, v_t * v_Delta / (v_t + v_Delta))
  }

  def mapToBinIndex(attr: String, query_lb: Double, query_ub: Double): List[Int] = {

    val attrIndex = columns.indexOf(attr)

    if (attrTypeList(attrIndex).equals("c")) {
      val tuple = DatasetUtils.getDomainUBLB(_state, columns(attrIndex))

      val domain_max = tuple.ub
      val domain_min = tuple.lb

      if (domain_max < domain_min)
        throw new IllegalStateException("The attr has wrong specification on domain.")

      if (query_ub < query_lb)
        throw new IllegalArgumentException("The query is not legal.")

      val bin_size = (domain_max - domain_min) / no_of_buckets

      val starting_index = math.floor(math.max((query_lb - domain_min), 0) / bin_size) toInt
      val ending_index = math.floor(math.max((query_ub - domain_min), 0) / bin_size) toInt

      (starting_index to math.min(ending_index, no_of_buckets - 1)) toList
    }
    else {
      throw new IllegalArgumentException("not implemented for discrete domain index mapping")
    }


  }

}
