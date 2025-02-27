package edu
package Experiments

import edu.DProvDB
import edu.DProvDB.{AccuracyState, ProvenanceState, State}
import edu.DProvDB.Mechanisms.{AdditiveGM, BaselineMechanism, BrownianMechanism, Chorus, ChorusWithProvenance, Mechanism, PrivateSQL}
import edu.DProvDB.Provenance.{ProvenanceTable, viewTuple, analystTuple}
import edu.DProvDB.Utils.{AnalystUtils, MetricUtils, DatasetUtils}
import edu.DProvDB.Utils.{ProvenanceUtils, ViewUtils}
import edu.DProvDB.Scheduler.{QueryQuerier}
import edu.DProvDB.Model._
import breeze.stats.distributions.{Gaussian, RandBasis, ThreadLocalRandomGenerator}
import org.apache.commons.math3.random.MersenneTwister
import scala.io.StdIn._

import java.io.FileWriter

import edu.DProvDB.Utils.BrownianMotionUtil
/**
 * SBT args list:
 * args(0): dataset to be used, adult or tpch
 * args(1): query tasks to execute, rrq or eqw
 *
 */

object CLI extends App {
    println("CLI")

    if (args.length == 1) {
        require(List("adult", "tpch").contains(args(0)))
    } else if (args.length == 2) {
        require(List("RRQ", "EQW").contains(args(1)))
    }

    var filename: String = _
    val db = if (args.length >= 1) args(0) else "adult"
    val table = if (args.length >= 3) args(2) else if (db.equals("adult")) "adult" else "orders"
    val state = new State()
    var avgAccuracy: Double = 0
    var queryCounter: Double = 0

    println(db, table)

    System.setProperty("dp.elastic_sensitivity.check_bins_for_release", "false")
    System.setProperty("db.use_dummy_database", "false")

    System.setProperty("db.driver", "org.postgresql.Driver")
    System.setProperty("db.url", "jdbc:postgresql://localhost:5432/" + db)
    System.setProperty("db.username", "link")
    System.setProperty("db.password", "12345")

    // Use the table schemas and metadata defined by the test classes
    System.setProperty("schema.config.path", "src/test/resources/schema.yaml")

    private val randomnessSeed = 42
    //val replacement = false
    //val scheduler = "round-robin"
    val mechanisms = "aGM"
    //aGM
    //val workloadSize = 4000
    val provenanceStates = ProvenanceState("dynamic", "fixed-aGM")
    //val accuracyState = AccuracyState("increasing", 3000, increasingStep = 1)
    var analystCase: List[Int] = List()
    var done = false

    println("Enter analyst privileges")
    //analystCase = readLine.split(" ").toList.map(_.toInt)
    analystCase = List(1,1)
    //analystCase = List(1,1)
    println("Enter system-wise budget")
    //val budget = readDouble()
    val budget = 10d
    //val task = "RRQ"

    filename = "data/" + db + "_CLI.csv"

    setupState(state, db, table, randomnessSeed, 1, budget, analystCase, provenanceStates, mechanisms, null, filename)

    var _provTable: ProvenanceTable = _
    var _state: State = state
    //var _mechanism: AdditiveGM = _

    setupProvenance(state)
    //var _mechanism = new BrownianMechanism(_provTable)
    var _mechanism = new AdditiveGM(state, _provTable, state._analysts, state._views, compositionMethod = state._accountantMethod)


/*
    var _mechanism1 = new AdditiveGM(state, _provTable, state._analysts, state._views, compositionMethod = state._accountantMethod)
    var compare: List[(Int, Double, Double)]= List()
    for (i <- 1 to 20 by 1) {
        compare = compare :+ (i,_mechanism.privacyTranslation(1,i,_provTable,state._analysts(0),_state._views(0)),
            _mechanism1.privacyTranslation(1,i,_provTable,state._analysts(0),_state._views(0)))
        //println(_mechanism.privacyTranslation(1,i,_provTable,state._analysts(0),_state._views(0)))
        //println(_mechanism1.privacyTranslation(1,i,_provTable,state._analysts(0),_state._views(0)))
    }
    for (i <- 30 to 100 by 10) {
        compare = compare :+ (i,_mechanism.privacyTranslation(1,i,_provTable,state._analysts(0),_state._views(0)),
            _mechanism1.privacyTranslation(1,i,_provTable,state._analysts(0),_state._views(0)))
        //println(_mechanism.privacyTranslation(1,i,_provTable,state._analysts(0),_state._views(0)))
        //println(_mechanism1.privacyTranslation(1,i,_provTable,state._analysts(0),_state._views(0)))
    }
    for ((i, j, k) <- compare) {
        println(i, j, k)
    }

    sys.exit
*/
    var i: Int = 0
    while (true) {
        i = i + 1
        /*
        println("Attr")
        val attr: String = readLine()
        println("lb")
        val lb = readDouble()
        println("ub")
        val ub = readDouble()
        */
        println("analyst")
        val analyst = state._analysts(readInt())
        //val analyst = state._analysts(0)
        println("accuracy")
        val accuracy = readDouble()

        println("query")
        //val queryString: String = "SELECT count( " + attr + " ) FROM " + state._tableName + " WHERE " + attr + " >= " + lb + " AND " + attr + "<= " + ub + ""
        //val queryString = readLine()
        val queryString = "SELECT count(age) FROM adult WHERE age >= 0 AND age <= 20"

        val transformedQuery = new TransformedQuery(queryString)

        //println(transformedQuery.predicates)

        val attr = transformedQuery.predicates(0).attr.filter(_.isLetter)

        val lb = transformedQuery.predicates(0).value.toDouble

        val ub = transformedQuery.predicates(1).value.toDouble

        val domain = DatasetUtils.getDomainUBLB(state, attr)

        val domains = List(DatasetUtils.getDomainUBLB(state, attr))

        val query = new Query(i, 0, analyst)

        query.setAttrs(List(attr))

        query.setQueryString(queryString)

        query.setQuerier(analyst.id)

        query.setDomains(domains)

        query.setUBLB(ub, lb)

        query.setTransformedQuery(transformedQuery)

        query.setAccuracy(accuracy)

        query.setType(QueryType.RangeQuery)


        val queryQuerier = new QueryQuerier(analyst,query)
        val view = _state._views find (view => view._viewID == queryQuerier.query._viewID) orNull

        if (_state._logger.equals("debug"))
            println(s"--This query is matched to view: ID ${view._viewID}, Attr ${view._attrs}.--")


        /**
        * Get sensitivity and #bins to answer the query.
        */
        val sensitivity = ViewUtils.getSensitivity(view, queryQuerier.query, _state._DPFlag)

        var bins: Int = 1

        _state._DPFlag match {
            case "bounded" => bins = sensitivity / 2
            case "unbounded" => bins = sensitivity
            case _ => throw new IllegalArgumentException()
        }

        if (_state._logger.equals("debug"))
            println(s"--The sensitivity of the query over the selected view: Sens=${sensitivity}, bins=$bins.--")

        val epsilon = _mechanism.privacyTranslation(sensitivity, queryQuerier.query._accuracyRequirement.getOrElse(Double.NaN),
            _provTable, queryQuerier.analyst, view, bins)

        if (_state._logger.equals("debug"))
            println(s"--Translated privacy budget: eps=$epsilon.--")


        var synopsis: NoisyView = null

        if (epsilon != Double.PositiveInfinity) {
            val status = _mechanism.checkConstraints(queryQuerier.analyst.id, view._viewID, epsilon)
            if (status.equals("Pass")) {

                if (_state._logger.equals("full") || _state._logger.equals("debug"))
                    println("--Query has been executed.--")

                synopsis = _mechanism.run(queryQuerier.query, queryQuerier.analyst, view, epsilon, status)

                queryQuerier.analyst.queryAnsweredCounter()

                avgAccuracy += queryQuerier.query._accuracyRequirement.getOrElse(0.0)
                queryCounter += 1
            }
            else if (status.equals("Cache")) {

                if (_state._logger.equals("full") || _state._logger.equals("debug"))
                    println("--Query has been executed via Cache.--")

                synopsis = _mechanism.run(queryQuerier.query, queryQuerier.analyst, view, epsilon, status)
                queryQuerier.analyst.queryAnsweredCounter()

                avgAccuracy += queryQuerier.query._accuracyRequirement.getOrElse(0.0)
                queryCounter += 1
            }
            else {
            // reject
            if (_state._logger.equals("full") || _state._logger.equals("debug"))
                println("--Query has been rejected.--")
            }

        }

        // answer query
        var ans: Double = null.asInstanceOf[Double]

        _state._mechanism match {
            case "aGM" => {
                ans = synopsis.asInstanceOf[LocalSynopsis].queryAnswering(queryQuerier.query, _state.viewManager)
            }
            case "baseline" | "PrivateSQL" | "Brownian"=> {
                ans = synopsis.queryAnswering(queryQuerier.query, _state.viewManager)
            }
            case "Chorus" | "ChorusP" =>
                ans = synopsis.asInstanceOf[Synopsis]._aggregationQueryAnswer
        }


        println(s"--Query Answer: ${ans}--")

        val analystDisplayID = analyst.id
        val viewDisplayID = view._viewID

        var x = 1
        while(x != 0){
            //println("Additional Info: 1 - analyst prov 2 - view prov 3 - provenace 4 - global synop 5 - local synop 6 - synopsis 0 - exit")
            x = readInt()
            x match {
                case 7 => printHistogram(view.histogramView)
                case 6 => printHistogram(synopsis.noisyHistogramView)
                case 4 => println(_provTable._analystCapacityList)
                case 5 => println(_provTable._viewControlList)
                case 1 => println(_provTable.getRow(analyst.id)(viewDisplayID).asInstanceOf[viewTuple].consumedBudget,
                _provTable._analystConstraints)
                case 2 => println(_provTable.getCol(view._viewID)(analystDisplayID).asInstanceOf[analystTuple].consumedBudget,
                _provTable._viewConstraints)
                case 3 => println(_provTable.columnMax()(viewDisplayID),_provTable._tableConstraint.getOrElse(0))
                case _ => x = 0
            }
        }
    }

    def printHistogram(map: Map[String,Double]): Unit = {
        map.foreach{case (key, value) => println(key, value)}
    }

    def setupProvenance(state: State): Unit = {
        _provTable = new ProvenanceTable(state._views, state._analysts)

        // set up constraints
        state._provenanceState.viewConstraintAllocationMode match {
            case "static" =>
                ProvenanceUtils.setConstraintStatic(_provTable, state._analysts, state._views, state._overallBudget, state._views.length * 2)
            case "dynamic" =>
                ProvenanceUtils.setConstraintDynamic(_provTable, state._analysts, state._views, state._overallBudget)
        }
    }

    case class EQWParams(attrs: List[String], granularity: Double, threshold: Double, m: Int)

    def setupState(state: State, dataset: String, tableName: String, randomnessSeed: Long, curRun: Int, budget: Double,
                privileges: List[Int], provenanceState: ProvenanceState, mechanism: String,
                EQWParams: EQWParams, filename:String, accountant: String = "basic"): State = {

    state.setReportFile(filename)
    state.setupDB(dataset, tableName)
    if (state._randomnessSeed == 0)
        state.setRandomSeed(randomnessSeed)
    if (state._curRun == 0 || state._curRun != curRun)
        state.setCurRun(curRun)
    state.setupViews()
    state.setOverallBudget(budget)
    state.setProvenanceState(provenanceState)
    state.setupAnalysts(privileges)
    state.setMechanism(mechanism, accountant)
    state.setLogger("debug")

    state
    }
}
