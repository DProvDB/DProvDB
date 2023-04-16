package edu

import junit.framework.TestCase

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

class RandomTests extends TestCase{

  private def genBiasedDist(length: Int): List[Double] = {
    val seed = List(1, 10, 8, 7)
    val weights: ListBuffer[Int] = new ListBuffer[Int]

    for (i <- 0 until length) {
      val ind = i % seed.length
      weights += seed(ind)
    }

    val sum = weights.sum
    weights map { weight => weight.toDouble / sum.toDouble } toList
  }

  private def randomBiased[T](s: List[T], weights: List[Double], seed: Long = 42): T = {
    val rnd = util.Random
    rnd.setSeed(seed)
    var prob: Double = rnd.nextDouble()
    println(prob)
    var n = 0
    var weight = weights.head
    while (prob > weight) {
      n += 1
      prob = prob - weight
      weight = weights(n)
    }
    s.iterator.drop(n).next
  }

  def testGen(): Unit = {

    println(genBiasedDist(1))
    println(genBiasedDist(3))
    println(genBiasedDist(4))
    println(genBiasedDist(5))
    println(genBiasedDist(10))

  }

  def testSample(): Unit = {
    val dist = genBiasedDist(5)
    val l = List(3, 4, 5, 6, 7)

    println(dist)

    for (i <- 1 to 100)
      println(randomBiased(l,dist, i*1000))
  }
}
