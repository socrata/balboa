package com.socrata.balboa.service.kafka.test.util

import java.util.Calendar

import com.socrata.balboa.metrics.Metric.RecordType
import com.socrata.balboa.metrics.impl.JsonMessage
import com.socrata.balboa.metrics.{Metric, Metrics}
import com.socrata.balboa.common.kafka.codec.KafkaCodec
import kafka.message.{Message, MessageAndMetadata}
import kafka.serializer.DefaultDecoder

/**
 * Utility class for Consumers.
 */
object ConsumerTestUtil {

  // Helper methods

  def message(key: String, message: String):MessageAndMetadata[Array[Byte], Array[Byte]] = {
    val k: Array[Byte] = if (key == null) null else key.toCharArray.map(_.toByte)
    val m: Array[Byte] = if (message == null) null else message.toCharArray.map(_.toByte)
    new MessageAndMetadata[Array[Byte], Array[Byte]]("test_topic", 1, new Message(m, k),
      0, new DefaultDecoder(), new DefaultDecoder())
  }

  def message[K,M](key: K, message: M, keyCodec: KafkaCodec[K], messageCodec: KafkaCodec[M]) = {
    val k: Array[Byte] = if (key == null) null else keyCodec.toBytes(key)
    val m: Array[Byte] = if (message == null) null else messageCodec.toBytes(message)
    new MessageAndMetadata[K, M]("test_topic", 1, new Message(m, k),
      0, keyCodec, messageCodec)
  }

  def testJSONMetric(name: String, num: Number): JsonMessage = {
    val metric = new Metric()
    metric.setType(RecordType.ABSOLUTE)
    metric.setValue(num)
    val metrics = new Metrics()
    metrics.put(name, metric)
    testJSONMetric(metrics)
  }

  def testJSONMetric(metrics: Metrics = new Metrics()): JsonMessage = {
    val json = new JsonMessage()
    json.setEntityId("test_id")
    json.setTimestamp(Calendar.getInstance().getTime.getTime)
    json.setMetrics(metrics)
    json
  }

  def testStringMessage(message: Array[Byte]): String = message match {
    case m: Array[Byte] => new String(m.map(_.toChar))
    case _=>null
  }

  def combine(k: String, m: String): String = s"$k:$m"

}