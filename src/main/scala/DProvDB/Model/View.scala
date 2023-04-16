package edu
package DProvDB.Model

import edu.DProvDB.ViewManager.ViewManager

class View(viewID: Int){

  // plain view
  var histogramView: Map[String, Double] = _

  var _flatTable: List[Double] = _
  var _domain: List[List[String]] = _
  var _attrs: List[String] = _
  var _viewBudget: Double = _
  val _viewID: Int = viewID

  var _viewSens: Int = 1 // histogram view

  var _noOfBins: Int = _
  var _binSize: Double = _
  var _lb: Double = _
  var _ub: Double = _

  var _crossedDomain: List[String] = _

  def setHistogramView(view: Map[String, Double]): Unit = {
    histogramView = view
    _flatTable = view.values.toList
  }

  def setViewSensitivity(sens: Int): Unit = {
    _viewSens = sens
  }

  def refresh: Unit ={
    _flatTable = histogramView.values.toList
  }

  def setDomain(domain: List[List[String]]): Unit = {
    _domain = domain
  }

  def setAttr(attrs: List[String]): Unit = {
    _attrs = attrs
  }

  def setCrossedDomain (crossedDomain: List[String]): Unit ={
    _crossedDomain = crossedDomain
  }

  def setFlatTable(flatTable: List[Double]): Unit = {
    _flatTable = flatTable
  }

  def setViewBudget(budget: Double): Unit = {
    _viewBudget = budget
  }

  def setBins(binSize: Double, noOfBins: Int): Unit ={
    _binSize = binSize
    _noOfBins = noOfBins
  }

  def setBins(binSize: Double): Unit ={
    _binSize = binSize
  }

  def setUBLB(ub: Double, lb: Double): Unit = {
    _lb = lb
    _ub = ub
  }

  /**
   * Given a range query's range [lower bound, upper bound], identify the sensitivity of the query.
   *
   * We denote lb ub as the lowerbound and upperbound of the view/domain*;
   * r_lb r_ub as the range query lowerbound and upperbound.
   * Apparently, we have lb <= r_lb <= r_ub <= ub; otherwise illegal query.
   *
   * By (r_lb - lb) % bucket_size, we get #bucket_l on the left side of r_lb;
   * similarly (ub - r_ub) % bucket_size gives #bucket_r on right side of r_ub.
   * By #bucket - #bucket_l - #bucket_r we get the number of bucket that the range falls in.
   *
   * This gives the sensitivity, since histogram query is of sensitivity 1 in the case of unbounded DP.
   * If we use bounded DP, then histogram query gives sensitivity 2.
   *
   */
  def getSensitivity(query: Query, DP_flag: String = "unbounded"): Int = {

    val range_ub: Double = query._rub
    val range_lb: Double = query._rlb

    val bucketL = (range_lb - _lb) / _binSize
    val bucketR = (_ub - range_ub) / _binSize

    DP_flag match {
      case "unbounded" => _noOfBins - bucketL.floor.toInt - bucketR.floor.toInt
      case "bounded" => (_noOfBins - bucketL.floor.toInt - bucketR.floor.toInt) * 2
      case _ => throw new IllegalArgumentException()
    }

  }

  override def toString: String = {
    "View ID: " + viewID + "; attrs: " + _attrs + "; histogram: " + histogramView
  }
}

class NoisyView(viewID: Int) extends View(viewID: Int) {
  var noisyHistogramView: Map[String, Double] = _
  var _analystID: Int = _
//  var _noisyFlatTable: List[Double] = _

  def setAnalyst(analystID:Int): Unit = {
    _analystID = analystID
  }

  def setNoisyHistogramView(view: Map[String, Double]): Unit = {
    noisyHistogramView = view
    _flatTable = view.values.toList
  }

  /**
   * Answer linear query based on the noisy view.
   * Input: query
   *
   * Get the lower and upper of the query range,
   * adding up the corresponding entries in the noisy synopsis.
   *
   * For aggregation query:
   * multiplying each entry with the key value, then adding them up.
   *
   * TODO: currently only support single attr
   */
  def queryAnswering(query: Query, viewManager: ViewManager): Double = {

    val lb = query._domains.head.lb
    val ub = query._domains.head.ub

    val attr = query._attrs.head

    val indexList = viewManager.mapToBinIndex(attr, lb, ub)

    var res: Double = 0

    query._queryType match {
      case QueryType.RangeQuery => {
        indexList.foreach { index => res += _flatTable(index) }
      }
      case _ =>
        throw new NotImplementedError()
    }

    res
  }
}
