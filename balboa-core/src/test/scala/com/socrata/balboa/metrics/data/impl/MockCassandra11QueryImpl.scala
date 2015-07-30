package com.socrata.balboa.metrics.data.impl

import java.util.Date

import com.socrata.balboa.common.Metric.RecordType
import com.socrata.balboa.common.{Metric, Metrics}
import com.socrata.balboa.metrics.data.Period

import scala.{collection => sc}

/**
 * Mock Cassandra Query implementation.  Persist and Fetch don't actually correlate to any real Mock Data Store behaviour.
 */
class MockCassandra11QueryImpl extends Cassandra11Query {

  // TODO This type of Mock Object can easily be replaced with Mockito or another Mock library

  var persists = List[APersist]()
  var fetches = List[AFetch]()
  var entities = List[AEntitySearch]()

  var metricsToReturn:Metrics = _
  val uniqEntityNames = 3 // should match the stubbed fn getAllEntityIds

  def getAllEntityIds(recordType:RecordType, period:Period):Iterator[String] = {
    entities = entities ::: List[AEntitySearch](new AEntitySearch(recordType, period))
    List("one", "two", "one", "two", "three").iterator
  }

  def fetch(entityId:String, period:Period, bucket:Date):Metrics = {
    val entityKey:String = Cassandra11Util.createEntityKey(entityId,bucket.getTime)
    //println("Fetching " + entityKey + " in period " + period + " DATE: " + bucket.toGMTString)
    fetches = fetches ::: List[AFetch](new AFetch(entityKey, period))
    metricsToReturn
  }

  def persist(entityId:String, bucket:Date, period:Period, aggregates:sc.Map[String, Metric], absolutes:sc.Map[String, Metric]) {
    val entityKey:String = Cassandra11Util.createEntityKey(entityId,bucket.getTime)
    //println("Persisting " + entityKey + " in period " + period + " DATE: " + bucket.toGMTString)
    persists = persists ::: List[APersist](new APersist(entityKey, period, aggregates, absolutes))
  }
}

class APersist(val entityKey:String, val period:Period, val agg:sc.Map[String, Metric], val abs:sc.Map[String, Metric]) {
  override def equals(obj:Any) = {
    if (!obj.isInstanceOf[APersist]) false
    else {
      val them:APersist = obj.asInstanceOf[APersist]
      them.entityKey == this.entityKey && them.period == this.period && them.agg.equals(agg) && them.abs.equals(abs)
    }
  }

  override def toString():String = {
    return "PERSIST: entityKey: " + entityKey + " period:" + period + " agg:" + agg + " abs:" + abs
  }
}

class AFetch(val entityKey:String, val period:Period) {
  override def equals(obj:Any) = {
    if (!obj.isInstanceOf[AFetch]) false
    else {
      val them:AFetch = obj.asInstanceOf[AFetch]
      them.entityKey == this.entityKey && them.period == this.period
    }
  }

  override def toString():String = {
    return "FETCH: entityKey: " + entityKey + " period:" + period
  }
}

class AEntitySearch(val recordType:RecordType, val period:Period) extends Ordered[AEntitySearch]{
  def compare(that:AEntitySearch) =  if (that.period.compareTo(this.period) == 0) (that.recordType.compareTo(this.recordType)) else that.period.compareTo(this.period)
}