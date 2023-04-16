package edu.DProvDB.Provenance

import edu.DProvDB.Model.Analyst


/**
 * The class to manage and optimize analyst constraints.
 *
 * 1) Privilege Normalization (initialized setup)
 *
 * 2) Proportional Fair (with an expansion parameter \tau)
 *
 * 3) Dynamic Adjustment
 */
object ConstraintOptimizer {

  def privilegeNormalization(overallBudget: Double, analysts: List[Analyst]): Unit = {

    val privilegeSum = analysts.map {analyst => analyst.privilege}.sum
    analysts.foreach
      {analyst => analyst.setPrivilegeConstraint( (overallBudget * analyst.privilege).toDouble / privilegeSum.toDouble)}
  }

  def privilegeNormalizationAGM(overallBudget: Double, analysts: List[Analyst]): Unit = {

    val privilegeMax = analysts.map {analyst => analyst.privilege}.max
    analysts.foreach
      {analyst => analyst.setPrivilegeConstraint((overallBudget * analyst.privilege).toDouble / privilegeMax.toDouble)}

  }

  def budgetExpansion(analysts: List[Analyst], tau: Double): Unit = {
    analysts foreach {
      analyst => analyst.setPrivilegeConstraint(analyst._privilegeConstraint * tau)
    }
  }

}
