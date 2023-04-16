package edu
package DProvDB.Model

import scala.collection.breakOut

class Synopsis(viewID: Int, view: View) extends NoisyView(viewID: Int) {

  var _aggregationQueryAnswer: Double = Double.NaN

  def setAnswer(answer: Double): Unit = {
    _aggregationQueryAnswer = answer
  }

}

class GlobalSynopsis(viewID: Int, view: View) extends Synopsis(viewID: Int, view: View) {
  val _synopsisID: Int = viewID
  var _epsilon: Double = Double.PositiveInfinity

  var _variance: Double = 0.0

  val _attrsSyn: List[String] = view._attrs

  val _domainSyn: List[List[String]] = view._domain
  val _viewBudgetSyn: Double = view._viewBudget
  val _crossedDomainSyn: List[String] = view._crossedDomain

  def updateEpsilon(epsilon: Double): Unit = {
    _epsilon = epsilon
  }

  def updateHistogram(histogram: List[Double]): Unit ={
    _flatTable = histogram
  }
}

class LocalSynopsis(analystID: Int, viewID: Int, view: View) extends Synopsis(viewID: Int, view: View) {
  val _synopsisID: Int = viewID
  var _epsilon: Double = Double.PositiveInfinity

  val _attrsSyn: List[String] = view._attrs

  val _domainSyn: List[List[String]] = view._domain
  val _viewBudgetSyn: Double = view._viewBudget
  val _crossedDomainSyn: List[String] = view._crossedDomain

  setAnalyst(analystID)

  def updateEpsilon(epsilon: Double): Unit = {
    _epsilon = epsilon
  }

  def updateHistogram(histogram: List[Double]): Unit ={
    _flatTable = histogram
    histogramView = (_crossedDomainSyn zip histogram)(breakOut)
  }



  //  // only answers linear queries
//  // TODO: Extend to aggregation queries
//  def queryAnswering (query: TransformedQuery): Double = {
//    val histogramKeys = histogramView.keySet
//
//    val histogramKeyList = histogramKeys.map {
//      key =>
//        key.split("&").toList
//    }
//
//    var filteredList = histogramKeyList.toList
//
//    if (query.predicates.nonEmpty) {
//      for (pred <- query.predicates) {
//        // TODO: debug, why the pred attr has double quote
//        val attrIndex = _attrsSyn.indexOf(pred.attr.replaceAll("^\"|\"$", ""))
//
//        filteredList = filteredList.filter(list => pred.evaluate(list(attrIndex)))
//      }
//    }
//
//
//    val filteredStringList = filteredList.map {
//      key => key.mkString("&")
//    }
//
//    val filteredHistogram: Map[String, Double] = histogramView filterKeys filteredStringList.toSet
//
//    filteredHistogram.foldLeft(0.0)(_+_._2)
//  }

}
