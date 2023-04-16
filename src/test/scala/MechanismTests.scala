//package edu
//
//import chorus.mechanisms.{EpsilonCompositionAccountant, RenyiCompositionAccountant}
//import edu.DProvDB.Mechanisms.{BaselineMechanism, Mechanism}
//import edu.DProvDB.Model.View
//import edu.DProvDB.Provenance.ProvenanceTable
////import edu.Experiments.Pool
//import junit.framework.TestCase
//
//class MechanismTests extends TestCase {
//
//  override def setUp(): Unit = super.setUp()
//
//  def testMechanismAbstract(): Unit = {
//
////    val pool: Pool.type = Pool
//
//    val testViewList = new View(0) :: new View(1) :: Nil
//
//    var provTable = null
//
//
//    val mechanism = new BaselineMechanism(provTable, compositionMethod = "renyi")
//
//    println(mechanism._accountant.isInstanceOf[RenyiCompositionAccountant])
//
//    println(mechanism.getClass.getName)
//
////    val mechanismTest = Class.forName(mechanism.getClass.getName).getDeclaredConstructor().newInstance()
//
////    println(mechanismTest.getClass.getName)
//
//  }
//}
