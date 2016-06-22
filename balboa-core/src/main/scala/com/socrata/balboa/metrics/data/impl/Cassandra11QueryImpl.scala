package com.socrata.balboa.metrics.data.impl

import java.io.IOException
import java.nio.charset.Charset
import java.{util => ju}

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.{BatchStatement, ConsistencyLevel, Row}
import com.socrata.balboa.metrics.Metric.RecordType
import com.socrata.balboa.metrics.data.impl.Cassandra11Util.DatastaxContext
import com.socrata.balboa.metrics.data.{BalboaFastFailCheck, Period}
import com.socrata.balboa.metrics.{Metric, Metrics}
import com.typesafe.scalalogging.slf4j.StrictLogging

import scala.{collection => sc}
import scala.collection.JavaConversions.asScalaIterator

/**
 * Query Implementation
 *
 */
class Cassandra11QueryImpl(context: DatastaxContext) extends Cassandra11Query with StrictLogging {

  def fetch(entityId: String, period: Period, bucket:ju.Date): Metrics = {
    val entityKey: String = Cassandra11Util.createEntityKey(entityId, bucket.getTime)
    val ret: Metrics = new Metrics()

    for (recordType <- List(RecordType.ABSOLUTE, RecordType.AGGREGATE)) {
      fetch_cf(recordType, entityKey, period).foreach(row => {
        ret.put(row.getString("column1"), new Metric(recordType, row.getLong("value")))})
    }

    ret
  }

  def removeTimestamp(key: String): String = key.replaceFirst("-[0-9]+$", "")

  /**
   * Returns all the row keys in a tier as an iterator with many, many duplicate strings. This is very slow. Do
   * not use this outside the admin tool.
   */
  def getAllEntityIds(recordType: RecordType, period: Period): Iterator[String] = {
    fastfail.proceedOrThrow()
    try {

      val qb = QueryBuilder.select("key").distinct()
        .from(context.keyspace, Cassandra11Util.getColumnFamily(period, recordType))
        .limit(100)
        .setConsistencyLevel(ConsistencyLevel.ONE)

      val rows = context.newSession.execute(qb).all()
      val retVal = asScalaIterator(rows.iterator()).map(_.getString("key")).map(removeTimestamp)

      fastfail.markSuccess()
      retVal
    } catch {
      case e: Exception =>
        val wrapped = new IOException("Error reading entityIds Query:" + recordType + ":" + period + "Cassandra", e)
        fastfail.markFailure(wrapped)
        throw wrapped
    }
  }

  def fetch_cf(recordType: RecordType, entityKey: String, period: Period): Iterator[Row] = {
    fastfail.proceedOrThrow()
    try {

      val qb = QueryBuilder.select().all()
        .from(context.keyspace, Cassandra11Util.getColumnFamily(period, recordType))
        .where(QueryBuilder.eq("key", entityKey))
        .setConsistencyLevel(ConsistencyLevel.ONE)

      val rows = context.newSession.execute(qb).all()

      val retVal = asScalaIterator(rows.iterator())

      fastfail.markSuccess()
      retVal
    } catch {
      case e: Exception =>
        val wrapped = new IOException("Error reading row " + entityKey + " from " + recordType + ":" + period, e)
        fastfail.markFailure(wrapped)
        throw wrapped
    }
  }

  def persist(entityId: String,
              bucket: ju.Date,
              period: Period,
              aggregates: sc.Map[String, Metric],
              absolutes: sc.Map[String, Metric]): Unit = {
    val entityKey = Cassandra11Util.createEntityKey(entityId, bucket.getTime)
    logger debug s"Using entity/row key $entityKey at period $period"
    fastfail.proceedOrThrow()

    val batchStatement = new BatchStatement(BatchStatement.Type.LOGGED)

    val entityKeyWhere = QueryBuilder.eq("key", entityKey)

    for (sameTypeRecords <- List(aggregates, absolutes)) {
      if (sameTypeRecords.nonEmpty) {
        // Initialize the query to work with either AGGREGATE or ABSOLUTE type values
        val table =  Cassandra11Util.getColumnFamily(period, sameTypeRecords.iterator.next()._2.getType)

        for {(k, v) <- sameTypeRecords} {
          if (k != "") {
            v.getType match {
              case RecordType.ABSOLUTE =>
                batchStatement.add(
                   QueryBuilder.insertInto(table)
                     .value("key", entityKey).value("column1", k).value("value", v.getValue))
              case RecordType.AGGREGATE =>
                batchStatement.add(
                  QueryBuilder.update(table)
                    .`with`(QueryBuilder.incr("value", v.getValue.longValue))
                    .where(entityKeyWhere).and(QueryBuilder.eq("column1", k)))
            }
          } else {
            logger warn "dropping metric with empty string as column"
          }
        }
      }
    }

    try {
      val retVal = context.newSession.execute(batchStatement)
      retVal.all()
      fastfail.markSuccess()
    } catch {
      case e: Exception =>
        val wrapped = new IOException("Error writing metrics " + entityKey + " from " + period, e)
        fastfail.markFailure(wrapped)
        logger.error(s"Error writing metrics $entityKey from $period", e)
        throw wrapped
    }
  }

  val fastfail: BalboaFastFailCheck = BalboaFastFailCheck.getInstance
}
