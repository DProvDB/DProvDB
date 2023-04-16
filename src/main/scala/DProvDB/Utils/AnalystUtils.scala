package edu.DProvDB.Utils

import edu.DProvDB.Model.Analyst
import edu.DProvDB.Provenance.ConstraintOptimizer
import edu.DProvDB.ProvenanceState

import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.util.Random

object AnalystUtils {


  /**
   * Initialize data analysts:
   *
   * Generate a list of data analysts.
   *
   * Then doing two things:
   * 1) set up privilege in each data analyst;
   * 2) set up constraints w.r.t the privilege level for each data analyst.
   */
  def initAnalysts(overallBudget: Double, privileges: List[Int], provenanceState: ProvenanceState): List[Analyst] = {
    val analysts: ListBuffer[Analyst] = new ListBuffer[Analyst]()

    for (i <- privileges.indices)
      analysts += Analyst(i,  Random.alphanumeric take 5 mkString, privileges(i))

    provenanceState.analystConstraintAllocationMode match {
      case "fixed-aGM" =>
        ConstraintOptimizer.privilegeNormalizationAGM(overallBudget, analysts.toList)
      case _ =>
        ConstraintOptimizer.privilegeNormalization(overallBudget, analysts.toList)
    }

    analysts.toList
  }

  def getAnalystPrivilegeConstraints(analysts: List[Analyst]): List[Double] = analysts map {analyst => analyst._privilegeConstraint}

  def getAnalystPrivilege(analysts: List[Analyst]): List[Int] = analysts map {analyst => analyst.privilege}

  def resetAnalystCounts(analysts: List[Analyst]): Unit = analysts foreach {analyst => analyst.clear()}
}