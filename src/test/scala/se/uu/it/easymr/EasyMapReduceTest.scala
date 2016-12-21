package se.uu.it.easymr

import org.apache.spark.SharedSparkContext
import org.scalatest.FunSuite
import org.scalatest.mock.MockitoSugar
import scala.io.Source

class EasyMapReduceTest
    extends FunSuite
    with SharedSparkContext {

  test("Map (DNA reverse)") {

    val rdd = sc.textFile(getClass.getResource("dna/dna.txt").getPath)

    val resRDD = EasyMapReduce.map(
      rdd,
      imageName = "ubuntu:xenial",
      command = "rev /input | tr -d '\\n' > /output")

    rdd.collect.zip(resRDD.collect).foreach {
      case (seq1, seq2) => {
        assert(seq1.reverse == seq2)
      }
    }

  }

  test("Map whole files (DNA reverse)") {

    val rdd = sc.wholeTextFiles(getClass.getResource("dna").getPath)

    val resRDD = EasyMapReduce.mapWholeFiles(
      rdd,
      imageName = "ubuntu:xenial",
      command = "rev /input | tr -d '\\n' > /output")

    rdd.collect.zip(resRDD.collect).foreach {
      case ((_, seq1), (_, seq2)) =>
        val toMatch = Source.fromString(seq1)
          .getLines
          .map(_.reverse)
          .mkString
        assert(toMatch == seq2)
    }

  }

  test("Reduce (GC count)") {

    val rdd = sc.textFile(getClass.getResource("dna/dna.txt").getPath)
      .map(_.count(c => c == 'g' || c == 'c').toString)

    val res = EasyMapReduce.reduce(
      rdd,
      imageName = "ubuntu:xenial",
      command = "expr $(cat /input1) + $(cat /input2) | tr -d '\\n' > /output")

    val sum = rdd.reduce {
      case (lineCount1, lineCount2) =>
        (lineCount1.toInt + lineCount2.toInt).toString
    }
    assert(sum == res)

  }

}