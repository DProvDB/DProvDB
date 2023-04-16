package edu.DProvDB.Utils

import breeze.stats.distributions.{Gaussian, RandBasis, ThreadLocalRandomGenerator}
import edu.DProvDB.Model.{Analyst, Domain, Node, Query, QueryType, TransformedQuery, View}
import edu.DProvDB.{AccuracyState, State}
import org.apache.commons.math3.random.MersenneTwister

import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps


object WorkloadUtils {

  /**
   * Randomized Range Query generator.
   *
   * Return a workload of queries for a data analyst.
   */
  def RRQ(state: State, analyst: Analyst, num_query: Int, accuracyState: AccuracyState): List[Query] = {

    val querySet: ListBuffer[Query] = ListBuffer[Query]()

    val attrList = DatasetUtils.getDatasetColumnNames(state)
    val attrTypeList = DatasetUtils.getDatabaseColumnType(state)
    val prunedAttrList = attrList zip attrTypeList filter { tuple => tuple._2.equals("c") } map { tuple => tuple._1 }

    val biasedDist = genBiasedDist(prunedAttrList.length)

    for (counter <- 0 until  num_query) {
//      val attr: String = random[String](prunedAttrList.toSet, state._randomnessSeed + counter)

      val attr: String = randomBiased[String](prunedAttrList, biasedDist, state._randomnessSeed + counter * 1001)

      val domain = DatasetUtils.getDomainUBLB(state, attr)
      val lower_range_mean = (domain.ub + domain.lb).toInt / 3
      val lower_range_variance = (domain.ub - domain.lb).toInt / 15

      val offset_mean = (domain.ub - domain.lb).toInt / 8
      val offset_variance = (domain.ub - domain.lb).toInt / 40

      val lb = math.min(math.max(GuassianDraw(lower_range_mean, lower_range_variance, state._randomnessSeed + counter), domain.lb), domain.ub)
      val ub = math.max(math.min(lb + math.abs(GuassianDraw(offset_mean, offset_variance, state._randomnessSeed + counter)), domain.ub), domain.lb)


      val queryString = "SELECT count( " + attr + " ) FROM " + state._tableName + " WHERE " +
        attr + " >= " + lb + " AND " + attr + "<= " + ub

      val transformedQuery = new TransformedQuery(queryString)

      val domains = List(DatasetUtils.getDomainUBLB(state, attr))


      val query = new Query(counter, attrList.indexOf(attr), analyst)

      query.setQueryString(queryString)

      query.setQuerier(analyst.id)

      query.setDomains(domains)

      query.setUBLB(ub, lb)

      query.setTransformedQuery(transformedQuery)

      query.setAccuracy(processAccuracy(accuracyState, counter, state._randomnessSeed + counter))

      query.setType(QueryType.RangeQuery)

      querySet += query
    }

    querySet.toList
  }

  /**
   * DFS/BFS (Exploration) Query Workload Generator
   *
   * Return the root node and the size of this workload
   */

  def EQW (state: State, analyst: Analyst, granularity: Double, threshold: Double, m: Int, accuracyState: AccuracyState): (Node, Int) = {

    val attrList = DatasetUtils.getDatasetColumnNames(state)
    val attrTypeList = DatasetUtils.getDatabaseColumnType(state)

    val attrs = state._EQWView._attrs

    if (attrs map {attr => attrTypeList(attrList.indexOf(attr))} contains "x") {
      throw new IllegalArgumentException("Contains not acceptable attribute(s).")
    }

    val domains = attrs map {attr => DatasetUtils.getDomainUBLB(state, attr)}

    val rndRange = RndRange((domains map {domain => (domain.ub - domain.lb) toInt} product) / granularity * m toInt)


    val counter = new AtomicInteger()

    val root: Node = TreeBuild(state, rndRange, state._EQWView, domains, attrs, analyst, granularity, threshold, m, counter)

    setTreeAccuracy(state, root, accuracyState)

    (root, counter.get())
  }

  def TreeBuild(state: State, rndRange: RndRange, view: View, domains: List[Domain], attrs: List[String], analyst: Analyst,
                granularity: Double, threshold: Double, m: Int, counter: AtomicInteger): Node = {

    if ((domains map {domain => math.abs(domain.ub - domain.lb)} product) <= granularity){
      return null
    }

    val id = rndRange.draw()

    // construct a query based on the domain
    val query = new Query(id, view._viewID, analyst)

    // TODO: extend to multi-attrs
    val queryString = "SELECT count( " + attrs.head + " ) FROM " + state._tableName + " WHERE (" +
      attrs.head + " < " + domains.head.ub + ") and (" + attrs.head + " > " + domains.head.lb + ")"


    query.setQueryString(queryString)

    query.setQuerier(analyst.id)

    query.setDomains(domains)

    query.setAttrs(attrs)

    query.setType(QueryType.RangeQuery)

    counter.incrementAndGet()

    // construct a new node
    val newNode = new Node(query, threshold)

    val nodeList: ListBuffer[Node] = new ListBuffer[Node]

    // recursively call self
    for (i <- 0 until m) {

      for (j <- attrs.indices) {
        val newDomains: ListBuffer[Domain] = new ListBuffer[Domain]

        for (k <- domains.indices) {
          if (j == k) {
            val ub = math.min(((domains(k).ub - domains(k).lb) / m) * (i + 1) + domains(k).lb, domains(k).ub)
            val lb = math.max(((domains(k).ub - domains(k).lb) / m) * i + domains(k).lb, domains(k).lb)
            newDomains += new Domain(ub, lb)
          }
          else {
            newDomains += new Domain(domains(k).ub, domains(k).lb)
          }
        }

        nodeList += TreeBuild(state, rndRange, view, newDomains.toList, attrs, analyst, granularity, threshold, m, counter)
      }
    }

    newNode.setNexts(nodeList.toList)

    newNode
  }

  def setTreeAccuracy (state: State, root: Node, accuracyState: AccuracyState): Unit = {

    var counter = 1

    val nodeStack = new java.util.Stack[Node]()

    nodeStack.push(root)

    while (nodeStack.size() > 0) {
      val top = nodeStack.pop()

      if (top.nextNodes.nonEmpty) {
        for (i <- top.nextNodes.indices) {
          if (top.nextNodes(i) != null)
            nodeStack.push(top.nextNodes(i))
        }
      }

      top.query.setAccuracy(processAccuracy(accuracyState, counter, state._randomnessSeed + counter))
      counter += 1
    }
  }


  //  randomly select an item from a set
  //  https://stackoverflow.com/questions/25053724/how-to-get-a-random-element-from-a-set-in-scala
  private def random[T](s: Set[T], seed: Long = 42): T = {
    val rnd = util.Random
    rnd.setSeed(seed)
    val n = rnd.nextInt(s.size)
    s.iterator.drop(n).next
  }

  private def randomBiased[T](s: List[T], weights: List[Double], seed: Long = 42): T = {
    val rnd = util.Random
    rnd.setSeed(seed)
    var prob: Double = rnd.nextDouble()
    var n = 0
    var weight = weights.head
    while (prob > weight) {
      n += 1
      prob = prob - weight
      weight = weights(n)
    }
    s.iterator.drop(n).next
  }

  private def genBiasedDist(length: Int): List[Double] = {
    val seed = List(1, 10, 1, 1, 1, 1)
    val weights: ListBuffer[Int] = new ListBuffer[Int]

    for (i <- 0 until length) {
      val ind = i % seed.length
      weights += seed(ind)
    }

    val sum = weights.sum
    weights map { weight => weight.toDouble / sum.toDouble } toList
  }

  case class RndRange(ub: Int){
    var rndRange = Range(0, ub).toBuffer

    def draw(): Int = {
      val id = random(rndRange.toSet)
      rndRange -= id
      id
    }
  }

  private def GuassianDraw(mean: Int, variance: Int, seed: Long = 42): Double = {
    Gaussian(mean, variance)(new RandBasis(new ThreadLocalRandomGenerator(new MersenneTwister(seed)))).draw()
  }

  private def processAccuracy(accuracyState: AccuracyState, count: Int, randomSeed: Long): Double = {
    accuracyState.accuracyMode match {
      case "increasing" =>
        val step = if (accuracyState.increasingStep == 0) 1 else accuracyState.increasingStep
        Math.max(accuracyState.startingVar - count * step, 1)

      case "random" =>
        val randomness = if (accuracyState.randomness == 0) accuracyState.startingVar / 5 else accuracyState.randomness
        Math.max(GuassianDraw(accuracyState.startingVar, randomness, randomSeed), 1)
    }
  }

  def workloadToListOfQueries(): Unit = {

  }

  def listOfQueriesToWorkload(): Unit = {

  }
}
