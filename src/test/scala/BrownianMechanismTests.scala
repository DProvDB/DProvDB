package edu

import edu.DProvDB.Utils.{BrownianMotionUtil, BrownianMotion}
import junit.framework.TestCase

class BrownianMechanismTests extends TestCase {

  val B = new BrownianMotion()

  override def setUp(): Unit = super.setUp()
  def testMixtureBoundary: Unit = {
    println("unit test brownian motion boundaries")

    println(BrownianMotionUtil.mixtureValue(100,1,0.000001,1))
    println(BrownianMotionUtil.mixtureDerivative(100,1,0.000001,1))
    println(BrownianMotionUtil.linearValue(100,1,0.000001,1))
    println(BrownianMotionUtil.linearDerivative(100,1,0.000001,1))
  }

  def testMinimize: Unit = {
    println("unit test brownian motion minimums")

    println(BrownianMotionUtil.findMinValue(100,1,0.000001))
    println(BrownianMotionUtil.findMinValue_old(100,1,0.000001))
  }

  def testRetrieveAccuracy: Unit = {
    println("unit test brownian motion retrieve by accuracy")

    println(BrownianMotionUtil.retrieveAccuracy(364.5,1,1e-5))
    println(BrownianMotionUtil.retrieveAccuracy(323.8888888888889,1,1e-5))
  }

  def testRetrievePrivacy: Unit = {
    println("unit test brownian motion retrieve by privacy")

    val (i, epsilon) = BrownianMotionUtil.retrievePrivacy(2,1,1e-5)
    println(i, epsilon)
    println(B.get(i))
  }
}
