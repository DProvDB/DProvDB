package edu

import chorus.mechanisms.LaplaceMechClipping
import chorus.schema.Schema
import chorus.sql.QueryParser
import edu.DProvDB.Model.{Analyst, Query, View}
import edu.DProvDB.Utils.{DatasetUtils, ProvenanceUtils, ViewUtils, WorkloadUtils}
import edu.DProvDB.ViewManager.ViewManager
import edu.DProvDB.{AccuracyState, State}
import junit.framework.TestCase

class DPTests extends TestCase{

  val state = new State()
  state.setupDB("adult", "adult")

  var views: List[View] = _

  System.setProperty("dp.elastic_sensitivity.check_bins_for_release", "false")
  System.setProperty("db.use_dummy_database", "false")

  System.setProperty("db.driver", "org.postgresql.Driver")
  System.setProperty("db.url", "jdbc:postgresql://localhost:5432/adult")
  System.setProperty("db.username", "link")
  System.setProperty("db.password", "12345")

  // Use the table schemas and metadata defined by the test classes
  System.setProperty("schema.config.path", "src/test/resources/schema.yaml")


  override def setUp(): Unit = super.setUp()

  def testTrueViewGen(): Unit = {

//    ViewUtils.queryMetaData(state)
//    val viewManager = new ViewManager()

//    println(state._dbConfig.database.tables.head.columns.toStream.map(col => col.name).toList)
//
//    println(DatasetUtils.getDatabaseColumnType(state))

//    views = viewManager.setupPlainViews(state)
//
//    views foreach {
//      view => println(view)
//    }
  }

  def testSensitivity: Unit = {

    val analyst = Analyst(0, "kirby", 4)
//    val workloadSize = 1
//    val accuracyState = AccuracyState("increasing", 12000, increasingStep = 1)
//
//    val queries = WorkloadUtils.RRQ(state, analyst, workloadSize, accuracyState)
//
//    queries foreach {
//      query => println(query)
//    }

    val viewManager = new ViewManager()
    viewManager.setState(state)

    views = viewManager.setupPlainViews()

    println(views)


    val attrList = DatasetUtils.getDatasetColumnNames(state)

    val query = new Query(1, attrList.indexOf("age"), analyst)

    query.setUBLB(100, 60)

    val view = views.filter(view => view._viewID == query._viewID).head

    println(view._ub, view._lb, view._binSize, view._noOfBins)

    val sens = ViewUtils.getSensitivity(view, query)

    println(sens)

  }

  def testVariance(): Unit = {
     val eps = ProvenanceUtils.searchForEpsilonBinary(0.0, 0.2, 1e-7, 1999.0, 1e-5, 6)

    println(eps)
  }

  def testChorus(): Unit = {
    val analyst = Analyst(0, "kirby", 4)

    val view = new View(1)

    val attrs = List("age")

    view.setAttr(attrs)

    state._EQWView = view

    val accuracyState = AccuracyState("increasing", 5, increasingStep = 1)


    val rootNode = WorkloadUtils.EQW(state, analyst, 1.0, 5, 3, accuracyState)._1

    print(rootNode.query, rootNode.query._domains)

//    val query = rootNode.query

    val query = "SELECT count( age ) FROM adult WHERE age IN (1.0, 140.0)"

    println(state._database)
    println(query)
//    println(query.queryString)

    val root = QueryParser.parseToRelTree(query, state._database)

    val res = new LaplaceMechClipping(1.0, 0, 10, root, state._dbConfig).run()

    println(res)
  }
}
