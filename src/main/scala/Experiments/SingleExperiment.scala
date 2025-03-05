package Experiments
import edu.Experiments.Experiment._
import edu.DProvDB.{AccuracyState, ProvenanceState, State}

object SingleExperiment extends App {
    private val randomnessSeed = 42
    private val replacement = false


    val db = if (args.length >= 1) args(0) else "adult"
    val task = if (args.length >= 2) args(1) else "RRQ"
    val table = if (args.length >= 3) args(2) else if (db.equals("adult")) "adult" else "orders"
    val exps = if (args.length >= 4) args(3) else "TFFFF"

    println(db, task, table)

    System.setProperty("dp.elastic_sensitivity.check_bins_for_release", "false")
    System.setProperty("db.use_dummy_database", "false")

    System.setProperty("db.driver", "org.postgresql.Driver")
    System.setProperty("db.url", "jdbc:postgresql://localhost:5432/" + db)
    System.setProperty("db.username", "link")
    System.setProperty("db.password", "12345")

    // Use the table schemas and metadata defined by the test classes
    System.setProperty("schema.config.path", "src/test/resources/schema.yaml")

    var filename = "data/" + task + "_" + db + "_e2e_single.csv"
    //writeReportTittle(filename)

    val state: State = new State()
    val scheduler = "random"
    // "round-robin" "random"

    val mechanism = "aGM"
    // "aGM", "baseline", "PrivateSQL", "Brownian"
    val workloadSize = 4000
    val provenanceState = ProvenanceState("dynamic", "fixed-aGM")
    val accuracyState = AccuracyState("increasing", 3000, increasingStep = 1)
    val analystCase = List(1, 9)

    val budget = 0.4

    setupState(state, db, table, randomnessSeed, 4, budget, analystCase, task,
        workloadSize, accuracyState, provenanceState, scheduler, replacement, mechanism, null, filename)
    run(state)
}
