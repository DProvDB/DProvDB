package edu.DProvDB.Utils

import chorus.schema.{Column, Schema, Table}
import edu.DProvDB.Model.Domain
import edu.DProvDB.State

import scala.collection.breakOut
import scala.language.postfixOps

object DatasetUtils {

  def getDatasetColumnNames(state: State): List[String] = {

    getTable(state).columns.toStream.map(col => col.name).toList

  }

  def getDatabaseColumnType(state: State): List[String] = {

    getTable(state).columns.toStream.map {
      col => Schema.getSchemaMapForTable(Schema.getDatabase(state._dataset), state._tableName)(col.name).properties
        .getOrElse("type", throw new IllegalArgumentException(s"Unknown field 'type' in '${col.name}'."))
    } toList

  }

  /**
   * Read the domain information from the yaml meta data.
   * Return the domain upperbound and lowerbound for a given column in a database table.
   *
   * Require: the column should be continuous attribute.
   */
  def getDomainUBLB(state: State, columnName: String): Domain = {

    val ub = getColumn(state, columnName).properties
      .getOrElse("ub", throw new IllegalArgumentException(s"Unknown field 'ub' in '$columnName'.")).toDouble
    val lb = getColumn(state, columnName).properties
      .getOrElse("lb", throw new IllegalArgumentException(s"Unknown field 'lb' in '$columnName'.")).toDouble

    new Domain(ub, lb)
  }

  def getDiscreteDomain(state: State, columnName: String): List[String] = {

    trimmedList(getColumn(state, columnName).properties
      .getOrElse("domain", throw new IllegalArgumentException(s"Unknown field 'domain' in '$columnName'.")))
  }

  def getTable(state: State): Table =
    state._dbConfig.database.tableMap.getOrElse(state._tableName, throw new IllegalArgumentException(s"Cannot find '${state._tableName}'."))

  def getColumn(state: State, columnName: String): Column =
    getTable(state).columnMap.getOrElse(columnName, throw new IllegalArgumentException(s"Cannot find '${columnName}'."))


  def trimmedList(str: String): List[String] = str.split(",").map(_.trim)(breakOut)

}
