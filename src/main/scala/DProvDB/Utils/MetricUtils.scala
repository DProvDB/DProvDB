package edu
package DProvDB.Utils

import DProvDB.Model.Analyst

object MetricUtils {
  /**
   * Utility measurement: the number of queries being answered.
   * @param analysts
   * @return
   */
  def utility (analysts: List[Analyst]): Int = {
    analysts.map(analyst => analyst.queryAnsweredCount).sum
  }

  def getUtilityBreakDown(analysts: List[Analyst]): List[Int] = {
    analysts.map(analyst => analyst.queryAnsweredCount)
  }

  /**
   * Fairness scoring (DCFG)
   * @param analysts
   * @return
   */
  def DCFG (analysts: List[Analyst]): Double = {
    val log2 = (x: Double) => math.log10(x)/math.log10(2.0)
    analysts.map(analyst => analyst.queryAnsweredCount / log2(1.toFloat/analyst.privilege + 1)).sum
  }


  /**
   * Testing time consumption
   * http://biercoff.com/easily-measuring-code-execution-time-in-scala/
   * @param block
   * @tparam R
   * @return
   */
  def time[R](block: => R): R = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()

    val time_elapsed = (t1 - t0) / 1e+6
    println("Elapsed time: " + (time_elapsed) + "ms")
    result
  }
}
