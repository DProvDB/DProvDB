package edu
package Experiments

import edu.DProvDB
import edu.DProvDB.{AccuracyState, ProvenanceState, State}
import edu.DProvDB.Utils.{AnalystUtils, MetricUtils}

import java.io.FileWriter

/**
 * SBT args list:
 * args(0): dataset to be used, adult or tpch
 * args(1): query tasks to execute, rrq or eqw
 *
 */

object Experiment extends App {

  println(s"Experiment: args.length =  ${args.length}")


  if (args.length == 1) {
    require(List("adult", "tpch").contains(args(0)))
  } else if (args.length == 2) {
    require(List("RRQ", "EQW").contains(args(1)))
  }


  val db = if (args.length >= 1) args(0) else "adult"
  val task = if (args.length >= 2) args(1) else "RRQ"
  val table = if (args.length >= 3) args(2) else if (db.equals("adult")) "adult" else "orders"
  val exps = if (args.length >= 4) args(3) else "TTTTTT"

  println(db, task, table)

  System.setProperty("dp.elastic_sensitivity.check_bins_for_release", "false")
  System.setProperty("db.use_dummy_database", "false")

  System.setProperty("db.driver", "org.postgresql.Driver")
  System.setProperty("db.url", "jdbc:postgresql://localhost:5432/" + db)
  System.setProperty("db.username", "link")
  System.setProperty("db.password", "12345")

  // Use the table schemas and metadata defined by the test classes
  System.setProperty("schema.config.path", "src/test/resources/schema.yaml")


  /**
   * Experimental report setup
   */
  var filename: String = _


  val e2e_exp = if (exps(0).==('T')) true else false
  val component_exp_1 = if (exps(1).==('T')) true else false
  val component_exp_2 = if (exps(2).==('T')) true else false
  val component_exp_3 = if (exps(3).==('T')) true else false
  val other_exp = if (exps(4).==('T')) true else false
  val delta_exp = if (exps(5).==('T')) true else false

  println(s"Selected experiments: End-to-end $e2e_exp, Additive GM vs Vanilla $component_exp_1, " +
    s"Cached Synopses $component_exp_2, Constraint Settings $component_exp_3, Delta Experiment $delta_exp, Other $other_exp")

  val runs: Int = 4 // each experiment is run 4 times
  val randomnessSeed = 42
  val replacement = false


  if (task == "RRQ") {
    RRQExperiments()
  } else if (task == "EQW") {
    EQWExperiments()
  }


  def RRQExperiments(): Unit = {

    /**
     * End-to-end Experiments
     */
    if (e2e_exp) {
      filename = "data/" + task + "_" + db + "_end_to_end.csv"
      writeReportTittle(filename)

      val state: State = new State()


      val schedulers = List("round-robin", "random")

      val mechanisms = List("aGM", "baseline", "PrivateSQL", "Chorus", "ChorusP")
      val workloadSize = 4000
      val provenanceStates = List(ProvenanceState("dynamic", "fixed-aGM"), ProvenanceState("dynamic", "fixed-normalized"),
        ProvenanceState("static", "fixed-normalized"), ProvenanceState("static", "fixed-normalized"), ProvenanceState("static", "fixed-normalized"))
      val accuracyState = AccuracyState("increasing", 3000, increasingStep = 1)
      val analystCase = List(1, 9)

      val overallBudgets = List(0.4, 0.8, 1.6, 3.2, 6.4)

      for (curRun <- 1 to runs) {
        overallBudgets foreach {
          budget =>
            schedulers foreach {
              scheduler =>
                mechanisms zip provenanceStates foreach {
                  tuple =>
                      setupState(state, db, table, randomnessSeed, curRun, budget, analystCase, task,
                        workloadSize, accuracyState, tuple._2, scheduler, replacement, tuple._1, null, filename)
                      run(state)
                    }
                }
            }
      }

    }

    /**
     * Additive GM v.s. Vanilla: Comparisons between different analyst constraint specifications
     */

    if (component_exp_1) {
      filename = "data/" + task + "_" + db + "_analyst_constraints.csv"
      writeReportTittle(filename)

      val mechanisms = List("aGM", "aGM", "baseline")

      val state: State = new State()

      val workloadSize = 2500
      val provenanceStates = List(ProvenanceState("dynamic", "fixed-aGM"), ProvenanceState("dynamic", "fixed-normalized"),
        ProvenanceState("dynamic", "fixed-normalized"))
      val scheduler = "round-robin"
      val accuracyState = AccuracyState("increasing", 5000, increasingStep = 2)

      val privileges = List(List(5, 5), List(5, 5, 5), List(5, 5, 5, 5), List(5, 5, 5, 5, 5), List(5, 5, 5, 5, 5, 5)) // two data analysts: low high
      val budget = 3.2

      for (curRun <- 1 to runs) {
        provenanceStates zip mechanisms foreach {
          tuple =>
            privileges foreach {
              privilege =>
                setupState(state, db, table, randomnessSeed, curRun, budget, privilege, task,
                  workloadSize, accuracyState, tuple._1, scheduler, replacement, tuple._2, null, filename)
                run(state)
            }
        }
      }

      val budgets = List(0.4, 0.8, 1.6, 6.4)
      val privilege = List(5, 5)

      for (curRun <- 1 to runs) {
        provenanceStates zip mechanisms foreach {
          tuple =>
            budgets foreach {
              budget =>
                setupState(state, db, table, randomnessSeed, curRun, budget, privilege, task,
                  workloadSize, accuracyState, tuple._1, scheduler, replacement, tuple._2, null, filename)
                run(state)
            }
        }
      }

    }


    /**
     * Component Experiment 2: Cached Synopses
     */
    if (component_exp_2) {
      filename = "data/" + task + "_" + db + "_increasing_workload.csv"
      writeReportTittle(filename)

      val privileges = List(1, 4) // two data analysts: low high
      val schedulers = List("round-robin")
      val mechanisms = List("aGM", "baseline", "Chorus", "ChorusP")

      val state: State = new State()

      val workloadSizes = List(50, 400, 1000, 2000, 4000, 7000)
      val overallBudgets = List(0.4, 0.8, 1.6, 3.2, 6.4)
      val provenanceState = ProvenanceState("dynamic", "fixed-normalized")
      val accuracyState = AccuracyState("increasing", 15000, increasingStep = 2)


      /**
       * Run experiments
       */

      for (curRun <- 1 to runs) {
        overallBudgets foreach {
          budget =>
            workloadSizes foreach {
              workloadSize =>
                schedulers foreach {
                  scheduler =>
                    mechanisms foreach {
                      mechanism =>
                        setupState(state, db, table, randomnessSeed, curRun, budget, privileges, task,
                          workloadSize, accuracyState, provenanceState, scheduler, replacement, mechanism, null, filename)
                        run(state)
                    }
                }
            }
        }
      }

    }

    /**
     * Component Experiment 3: fairness vs utility
     */
    if (component_exp_3) {
      filename = "data/" + task + "_" + db + "_fairness.csv"
      writeReportTittle(filename)

      val provenanceStates = List(ProvenanceState("dynamic", "fixed-aGM"),
        ProvenanceState("dynamic", "fixed-expansion", 1.3), ProvenanceState("dynamic", "fixed-expansion", 1.6),
        ProvenanceState("dynamic", "fixed-expansion", 1.9))

      val schedulers = List("round-robin", "random")
      val mechanisms = List("aGM", "aGM", "aGM", "aGM")
      val accuracyState = AccuracyState("increasing", 15000, increasingStep = 2)


      val overallBudgets = List(0.4, 0.8, 1.6, 3.2, 6.4)
      val state: State = new State()
      val privileges = List(1, 4) // two data analysts: low high


      val workloadSize = 4000

      for (curRun <- 1 to runs) {
        overallBudgets foreach {
          budget =>
            provenanceStates foreach {
              provenanceState =>
                schedulers foreach {
                  scheduler =>
                      mechanisms foreach {
                        mechanism =>
                          setupState(state, db, table, randomnessSeed, curRun, budget, privileges, task,
                            workloadSize, accuracyState, provenanceState, scheduler, replacement, mechanism, null, filename)
                          run(state)
                      }
                    }
                }
            }
      }
    }

    /**
     * Other Evaluation: Comparisons between different composition methods
     */
    if (other_exp) {
      filename = "data/" + task + "_" + db + "_compositions.csv"
      writeReportTittle(filename)


      val compositions = List("basic", "advanced", "renyi")
      val state: State = new State()

      val mechanism = "aGM"
      val workloadSize = 15000
      val provenanceState = ProvenanceState("dynamic", "fixed-aGM")
      val scheduler = "round-robin"
      val accuracyState = AccuracyState("increasing", 15000, increasingStep = 1)
      val privileges = List(1, 4) // two data analysts: low high

      val budget = 6.4

      for (curRun <- 1 to runs) {
      compositions foreach {
        composition =>
            setupState(state, db, table, randomnessSeed, curRun, budget, privileges, task,
              workloadSize, accuracyState, provenanceState, scheduler, replacement, mechanism, null, filename, composition)
            run(state)
          }
      }

    }

    /**
     * Delta Experiment: Comparisons between different delta values
     */
    if (delta_exp) {
      filename = "data/" + task + "_" + db + "_end_to_end_delta.csv"
      writeReportTittle(filename)

      val state: State = new State()


      val schedulers = List("round-robin", "random")

      val mechanisms = List("aGM", "baseline", "PrivateSQL", "Chorus", "ChorusP")
      val workloadSize = 4000
      val provenanceStates = List(ProvenanceState("dynamic", "fixed-aGM"), ProvenanceState("dynamic", "fixed-normalized"),
        ProvenanceState("static", "fixed-normalized"), ProvenanceState("static", "fixed-normalized"), ProvenanceState("static", "fixed-normalized"))
      val accuracyState = AccuracyState("increasing", 3000, increasingStep = 1)
      val analystCase = List(1, 9)

      val overallBudgets = List(6.4)
//      val overallBudgets = List(0.4, 0.8, 1.6, 3.2, 6.4)
      val perQueryDeltas = List(1e-9, 1e-10, 1e-11, 1e-11, 1e-12, 1e-13)

      for (curRun <- 1 to runs) {
        overallBudgets foreach {
          budget =>
            perQueryDeltas foreach {
              delta =>
              schedulers foreach {
                scheduler =>
                mechanisms zip provenanceStates foreach {
                  tuple =>
                      setupState(state, db, table, randomnessSeed, curRun, budget, analystCase, task,
                        workloadSize, accuracyState, tuple._2, scheduler, replacement, tuple._1, null, filename, perQueryDelta=delta)
                      run(state)
                }
              }
            }
          }
        }
    }

  }

  case class EQWParams(attrs: List[String], granularity: Double, threshold: Double, m: Int)
  def EQWExperiments(): Unit = {

    val eqw_attr = if(db.equals("adult")) "age" else "o_totalprice"


    /**
     * End-to-end Evaluation for BFS tasks.
     */
    filename = "data/" + task + "_" + db + "_end_to_end.csv"
    writeReportTittle(filename)

    val privileges = List(1, 4) // two data analysts: low high
    val runs: Int = 4 // each experiment is run 4 times

    val accuracyState = AccuracyState("increasing", 25000, increasingStep = 100)
    val provenanceStates = List(ProvenanceState("dynamic", "fixed-aGM"), ProvenanceState("dynamic", "fixed-normalized"),
      ProvenanceState("dynamic", "fixed-normalized"), ProvenanceState("dynamic", "fixed-normalized"),
      ProvenanceState("static", "fixed-normalized"))
    val mechanisms = List("aGM", "baseline", "Chorus", "ChorusP")
    val schedulers = List("BFS")
    val eqw_params = if(db.equals("adult")) List(EQWParams(List(eqw_attr), 1, 5, 5)) else List(EQWParams(List(eqw_attr), 4000, 6000, 6))


    /**
     * Run experiments
     */
    val overallBudget = 200

    val state: State = new State()

    for (curRun <- 1 to runs) {
      eqw_params foreach {
      eqw_param =>
      schedulers foreach {
        scheduler =>
          mechanisms zip provenanceStates foreach {
            tuple =>
                setupState(state, db, table, randomnessSeed, curRun, overallBudget, privileges, task, null.asInstanceOf[Int],
                  accuracyState, tuple._2, scheduler, replacement, tuple._1, eqw_param, filename)
                run(state)
                state._views = null // get run time for setting views
              }
          }
      }
    }
  }

  def run(state: State): Unit = {

    val sys = new DProvDB.System()

    sys.setup(state)
    println(s"=======The ${state._curRun}-th run: Finished system set up.=========")

    val t0 = System.nanoTime()
    sys.execute()
    val t1 = System.nanoTime()

    val time_sys_execute = (t1 - t0) / 1e+6

    val utility = MetricUtils.utility(state._analysts)

    val utility_breakdown = MetricUtils.getUtilityBreakDown(state._analysts)

    val fairness = MetricUtils.DCFG(state._analysts)

    val accountant_breakdown = sys.analystsAccountant

    var view_breakdown: String = null

    if (state._mechanism.equals("aGM")) {
      view_breakdown = sys._provTable.columnMax().toString()
    }
    else
      view_breakdown = sys._provTable.columnSum().toString()

    if (state._workloadType.equals("RRQ"))
      writeReportData(state.report_filename, state, view_breakdown, time_sys_execute, utility, utility_breakdown, fairness, sys.accountant.toString, sys.avgAccuracy, accountant_breakdown)
    else if (state._workloadType.equals("EQW"))
      writeReportData(state.report_filename, state, view_breakdown, time_sys_execute, utility, utility_breakdown, fairness, sys.accountantLedger.toList.toString(), sys.avgAccuracy, accountant_breakdown)
  }


  def setupState(state: State, dataset: String, tableName: String, randomnessSeed: Long, curRun: Int, budget: Double,
                 privileges: List[Int], workloadType: String, workloadSize: Int, accuracyState: AccuracyState,
                 provenanceState: ProvenanceState, scheduler: String, replacement: Boolean, mechanism: String,
                 EQWParams: EQWParams, filename:String, accountant: String = "basic", perQueryDelta: Double = 1e-9,
                 deltaConstraint: Double = 1e-5): State = {

    state.setReportFile(filename)

    state.setupDB(dataset, tableName)
    if (state._randomnessSeed == 0)
      state.setRandomSeed(randomnessSeed)
    if (state._curRun == 0 || state._curRun != curRun)
      state.setCurRun(curRun)
    state.setupViews()
    state.setOverallBudget(budget)
    state.setProvenanceState(provenanceState)    
    state.setDelta(perQueryDelta,deltaConstraint)
    state.setupAnalysts(privileges)
    if (task.equals("EQW"))
      state.setupEQW(workloadType, EQWParams.attrs, EQWParams.granularity, EQWParams.threshold, EQWParams.m, accuracyState)
    else
      state.setupAnalystWorkload(workloadType, workloadSize, accuracyState)
    state.setMechanism(mechanism, accountant)
    state.setScheduler(scheduler, replacement)
    state.setLogger("debug")

    state
  }

 def writeReportTittle(fileName: String): Unit = {

    val fw = new FileWriter(fileName, true)

    try {
      fw.write("dataset; mechanism; task; viewConstraintFlag; budget; utility; totalNoOfQueries; analystConstraints; setup_time; execution_time; " +
        "utility_breakdown; DCFG; accountant; accountant_breakdown; view_breakdown; avgAccuracy; runIndex; perQueryDelta; randomness \n")
    }
    finally fw.close()
  }

  private def writeReportData(fileName: String, state: State, view_breakdown: String,
                              time_sys_execute: Double, utility: Int, utility_breakdown: List[Int], DCFG: Double,
                              accountant: String, avgAccuracy:Double, accountant_breakdown: List[Double]): Unit = {

    val fw = new FileWriter(fileName, true)

    try {
      fw.write(state._dataset + "; ")
      fw.write(state._mechanism + "; ")
      fw.write(state._workloadType + "_" + state._scheduler + "; ")
      fw.write(state._provenanceState + "; ")
      fw.write(state._overallBudget + "; ")
      fw.write(utility + "; ")
      fw.write(state._analysts.size * state._workloadSize + "; ")
      fw.write(AnalystUtils.getAnalystPrivilegeConstraints(state._analysts) + "; ")
      fw.write(state._viewSetupTime + "; ")
      fw.write(time_sys_execute + "; ")
      fw.write(utility_breakdown + "; ")
      fw.write(DCFG + "; ")
      fw.write(accountant + "; ")
      fw.write(accountant_breakdown + "; ")
      fw.write(view_breakdown + "; ")
      fw.write(avgAccuracy + "; ")
      fw.write(state._curRun + "; ")
      fw.write(state._per_query_delta + "; ")
      fw.write(state._randomnessSeed + " \n")
    }
    finally fw.close()
  }

}
