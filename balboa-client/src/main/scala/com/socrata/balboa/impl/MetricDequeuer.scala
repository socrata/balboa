package com.socrata.balboa.impl

import java.util.concurrent.{Executors, TimeUnit}

import com.socrata.balboa.common.logging.BalboaLogging
import com.socrata.balboa.common.{Metric, Metrics}
import com.socrata.metrics.collection.PreBufferQueue
import com.socrata.metrics.components.{BufferComponent, BufferItem, MetricEntry}

trait MetricDequeuerService extends BalboaLogging {
  self: BufferComponent with PreBufferQueue =>

  val timeout = 500L

  class MetricDequeuer {
    var keepRunning = true
    val dequeueExecutor = Executors.newFixedThreadPool(1)
    val flushExecutor = Executors.newScheduledThreadPool(1)
    val actualBuffer = Buffer()

    class PreBufferDequeuer(val buffer:Buffer) extends Runnable {
      def run() {
        while(keepRunning) take()
        while(!queue.isEmpty) take()
      }

      def take() {
        // if need be, we can add a cushion to prevent context switching, e.g. only take if (queue.size > 10)
        queue.take() match {
          case Some(m) => buffer.synchronized { buffer.add(asBufferItem(m)) }
          case None =>
        }
      }
    }

    class BufferFlusher(val buffer:Buffer) extends Runnable {
      override def run() {
        val numFlushed = buffer.synchronized { buffer.flush() }
        val queueSize = queue.size
        if (numFlushed < queueSize) logger.warn("The metric queue contains " + queueSize + " elements; the last buffer flush emptied out " + numFlushed + " elements.")
      }
    }

    private def asBufferItem(m:MetricEntry) = {
      val metrics = new Metrics()
      val metric = new Metric(m.recordType, m.value)
      metrics.put(m.name, metric)
      BufferItem(m.entityId, metrics, m.timestamp)
    }

    def start(delay:Long, interval:Long) = {
      actualBuffer.start()
      flushExecutor.scheduleWithFixedDelay(new BufferFlusher(actualBuffer), delay, interval, TimeUnit.SECONDS)
      dequeueExecutor.submit(new PreBufferDequeuer(actualBuffer))
    }

    def stop() {
      keepRunning = false
      dequeueExecutor.shutdown()
      while(!dequeueExecutor.awaitTermination(timeout, TimeUnit.MILLISECONDS))
        logger.info("Emptying out BalboaClient queue; " + queue.size + " elements left.")
      dequeueExecutor.shutdownNow()
      flushExecutor.shutdown()
      while(!flushExecutor.awaitTermination(timeout, TimeUnit.MILLISECONDS))
        logger.info("Allowing BalboaClient buffer to finish flushing; " + actualBuffer.size() + " elements left.")
      flushExecutor.shutdownNow()
      actualBuffer.stop()
    }
  }

  def MetricDequeuer() = new MetricDequeuer()
}
