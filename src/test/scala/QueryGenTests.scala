package edu

import edu.DProvDB.{AccuracyState, State}
import edu.DProvDB.Model.{Analyst, View}
import edu.DProvDB.Utils.{DatasetUtils, ViewUtils, WorkloadUtils}
import junit.framework.TestCase


class QueryGenTests extends TestCase{

  val state = new State()
  state.setupDB("adult", "adult")

  System.setProperty("dp.elastic_sensitivity.check_bins_for_release", "false")
  System.setProperty("db.use_dummy_database", "false")

  System.setProperty("db.driver", "org.postgresql.Driver")
  System.setProperty("db.url", "jdbc:postgresql://localhost:5432/adult")
  System.setProperty("db.username", "link")
  System.setProperty("db.password", "12345")

  // Use the table schemas and metadata defined by the test classes
  System.setProperty("schema.config.path", "src/test/resources/schema.yaml")

  override def setUp(): Unit = super.setUp()


  def testMetaData(): Unit = {

    ViewUtils.queryMetaData(state)
  }

  def testAttrList(): Unit = {
    println(DatasetUtils.getDatasetColumnNames(state))
  }
  def testRRQIncreasing(): Unit = {

    val analyst = Analyst(0, "kirby", 4)
    val workloadSize = 10
    val accuracyState = AccuracyState("increasing", 12000, increasingStep = 1)

    val queries = WorkloadUtils.RRQ(state, analyst, workloadSize, accuracyState)

    queries foreach {
      query => println(query)
    }
  }

  def testRRQIncreasing2(): Unit = {

    val analyst = Analyst(0, "kirby", 4)
    val workloadSize = 10
    val accuracyState = AccuracyState("increasing", 5, increasingStep = 1)

    val queries = WorkloadUtils.RRQ(state, analyst, workloadSize, accuracyState)

    queries foreach {
      query => println(query)
    }
  }

  def testRRQRandom(): Unit = {

    val analyst = Analyst(0, "kirby", 4)
    val workloadSize = 10
    val accuracyState = AccuracyState("random", 1000, randomness = 5)

    val queries = WorkloadUtils.RRQ(state, analyst, workloadSize, accuracyState)

    queries foreach {
      query => println(query)
    }
  }

  def testBFS(): Unit = {

    val analyst = Analyst(0, "kirby", 4)

    val view = new View(1)

    val attrs = List("age")

    view.setAttr(attrs)

    state._EQWView = view

    val accuracyState = AccuracyState("increasing", 5, increasingStep = 1)


    val root = WorkloadUtils.EQW(state, analyst, 20, 5, 7, accuracyState)

    println(root._1.query)

    println(root._2)
  }

}
