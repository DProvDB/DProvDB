package edu
package DProvDB.Utils

import chorus.rewriting.RewriterConfig
import chorus.sql.QueryParser
import chorus.util.DB
import chorus.util.DB.Row
import edu.DProvDB.Model.{GlobalSynopsis, LocalSynopsis, Query, View}
import edu.DProvDB.State


import scala.collection.mutable.ArrayBuffer
import scala.collection.breakOut



object ViewUtils {

  def genHistogramContinuous(viewID: Int, attrs: List[String], min: Double, max: Double, no_of_bucket: Int,
                             config: RewriterConfig, relation: String): View = {
    val view = new View(viewID)
    view.setAttr(attrs)

    // create histogram
    val histogram: ArrayBuffer[Double] = ArrayBuffer[Double]()
    val indexList = (0 until no_of_bucket).toList.map(_.toString)

    for (index <- 0 until  no_of_bucket) {

      val step = (max - min) / no_of_bucket
      val low = min + index * step
      val high = min + (index + 1) * step

      var condition = ""

      if (index == 0) {
        condition = attrs.head + " >= " + low + " AND " + attrs.head + " <= " + high
      }
      else
        condition = attrs.head + " > " + low + " AND " + attrs.head + " <= " + high

//      println(condition)

      val query = s"SELECT COUNT(*) FROM $relation WHERE $condition"

      val root = QueryParser.parseToRelTree(query, config.database)
      val result = DB.execute(root, config.database)

      histogram += result.head.vals.head.toDouble
    }

    val histogramMap = (indexList zip histogram.toList)(breakOut): Map[String, Double]
    view.setHistogramView(histogramMap)
    view.setCrossedDomain(indexList)

    view
  }

  /**
   * Generate a histogram view, assuming single relation database
   * @param viewID
   * @param domain A map from attribute name to the list of its domain
   * @param attrs  A list of attributes in the database
   * @param config Database configuration
   * @param relation The table being queried
   * @return
   */
  def genHistogramDiscrete(viewID: Int, domain: List[List[String]], attrs: List[String], config: RewriterConfig, relation: String): View ={
    val view = new View(viewID)
    view.setAttr(attrs)
    view.setDomain(domain)

    // create histogram
    val histogram: ArrayBuffer[Double] = ArrayBuffer[Double]()
    val domainCrossProduct = combine[String](_ + "&" + _)(domain)

    view.setCrossedDomain(domainCrossProduct.toList)

    for(combination <- domainCrossProduct) {

      var condition = ""

      val combinationList = combination split '&'

      for (attr <- attrs) {
        if (attrs.length == 1 || attrs.last == attr)
          condition += attr ++ " = \'" ++ combinationList(attrs.indexOf(attr)) ++ "\'"
        else
          condition += attr ++ " = \'" ++ combinationList(attrs.indexOf(attr)) ++ "\' AND "
      }

      // example: SELECT COUNT(*) FROM orders WHERE (o_orderkey, o_orderstatus) = ('1', 'O');
      // or: SELECT COUNT(*) FROM orders WHERE o_orderkey = 1 AND orders.o_orderstatus = 'F';
      val query = s"SELECT COUNT(*) FROM $relation WHERE $condition"
      

      val root = QueryParser.parseToRelTree(query, config.database)
      val result = DB.execute(root, config.database)

      histogram += result.head.vals.head.toDouble

    }

    val histogramMap = (domainCrossProduct.toList zip histogram.toList)(breakOut): Map[String, Double]

    view.setHistogramView(histogramMap)

    view
  }

  def queryMetaData(state: State): Unit = {

    val columns = DatasetUtils.getDatasetColumnNames(state)
    val attrTypeList = DatasetUtils.getDatabaseColumnType(state)

    val relation = state._tableName

    for (index <- columns.indices) {
      if (attrTypeList(index).equals("d")) {
        val query_domain = s"SELECT DISTINCT " + columns(index) + s" FROM $relation"

        //        println(query_domain)
        val root = QueryParser.parseToRelTree(query_domain, state._dbConfig.database)

        val results: ArrayBuffer[List[Row]] = ArrayBuffer[List[Row]]()
        results += DB.execute(root, state._dbConfig.database)

        val attrsDomain: ArrayBuffer[List[String]] = ArrayBuffer[List[String]]()

        for (result <- results) {
          val attrDomain: ArrayBuffer[String] = ArrayBuffer[String]()
          for (row <- result) {
            attrDomain += row.vals.head
          }
          attrsDomain += attrDomain.toList
        }

        println(columns(index), attrsDomain)
      }
      else if (attrTypeList(index).equals("c")) {

        val query_max = "SELECT MAX( " + columns(index) + s" ) FROM $relation"
        val query_min = "SELECT MIN( " + columns(index) + s" ) FROM $relation"

        val root_max = QueryParser.parseToRelTree(query_max, state._dbConfig.database)
        val root_min = QueryParser.parseToRelTree(query_min, state._dbConfig.database)

        val result_max: Double = DB.execute(root_max, state._dbConfig.database).head.vals.head.toDouble
        val result_min: Double = DB.execute(root_min, state._dbConfig.database).head.vals.head.toDouble

        println(columns(index), result_max, result_min)

      }

    }
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
  def getSensitivity(view: View, query: Query, DP_flag: String = "unbounded"): Int = {

    val range_ub: Double = query._rub
    val range_lb: Double = query._rlb

    if ((range_ub == 0) || (range_lb == 0) || (range_lb == range_ub)) {
      DP_flag match {
        case "unbounded" => return 1
        case "bounded" => return 2
        case _ => throw new IllegalArgumentException()
      }
    }

    val lb = view._lb
    val ub = view._ub
    val binSize = view._binSize
    val noOfBins = view._noOfBins

    if (range_ub <= lb || range_lb >= ub || range_lb > range_ub)
      throw new IllegalStateException("Query cannot be answered by this view.")

    val bucketL = (range_lb - lb) / binSize
    val bucketR = (ub - range_ub) / binSize

    DP_flag match {
      case "unbounded" => noOfBins - bucketL.floor.toInt - bucketR.floor.toInt
      case "bounded" => (noOfBins - bucketL.floor.toInt - bucketR.floor.toInt) * 2
      case _ => throw new IllegalArgumentException()
    }
  }


  /**
   * https://stackoverflow.com/questions/2892358/expand-a-setsetstring-into-cartesian-product-in-scala/4515071#4515071
   */
  def combine[A](f: (A, A) => A)(xs:Iterable[Iterable[A]]) =
    xs reduceLeft { (x, y) => for (a <- x.view; b <- y) yield f(a, b) }


  def getGlobalSynopsis(globalSynopses: List[GlobalSynopsis], synopsisID: Int): GlobalSynopsis = {
    globalSynopses.filter( synopsis => synopsis._synopsisID == synopsisID).head
  }

  def getLocalSynopsis(localSynopses: List[LocalSynopsis], synopsisID: Int, analystID: Int): LocalSynopsis = {
    localSynopses.filter( synopsis => synopsis._synopsisID == synopsisID && synopsis._analystID == analystID).head
  }
}
