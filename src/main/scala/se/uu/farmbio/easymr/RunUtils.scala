package se.uu.farmbio.easymr

import java.io.File
import java.io.PrintWriter
import java.util.concurrent.ExecutorService

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration.DurationInt
import scala.io.Source
import scala.sys.process.ProcessLogger
import scala.sys.process.stringSeqToProcess
import scala.util.Failure
import scala.util.Success

import org.apache.spark.Logging

import com.google.common.io.Files


class RunException(msg: String) extends Exception(msg)

object RunUtils {
  
  val FIFO_READ_TIMEOUT = 1200
  
}

class RunUtils(val threadPool: ExecutorService) extends Logging {
  
  implicit val ec = ExecutionContext.fromExecutor(threadPool)
  
  def writeToFifo(fifo: File, toWrite: String) = {
    logInfo(s"writing to fifo: ${fifo.getAbsolutePath}")
    Future {
      new PrintWriter(fifo) {
        write(toWrite)
        close
      }
    } onComplete {
      case Failure(e) => {
        logWarning(
            s"exeption while writing to ${fifo.getAbsolutePath} \n" + 
            e.getStackTraceString
        )
      }
      case Success(_) => logInfo(s"successfully wrote into ${fifo.getAbsolutePath}")
    }
  }

  def readFromFifo(fifo: File, timeoutSec: Int) = {
    logInfo(s"reading output from fifo: ${fifo.getAbsolutePath}")
    val future = Future {
      Source.fromFile(fifo).mkString
    } 
    Await.result(future, timeoutSec seconds)
  }

  def dockerRun(
    cmd: String,
    imageName: String,
    dockerOpts: String) = {
    command(s"docker run $dockerOpts $imageName sh -c ".split(" ") ++ Seq(cmd))
  }

  def mkfifo(name: String) = {
    val tmpDir = Files.createTempDir
    tmpDir.deleteOnExit
    val fifoPath = tmpDir.getAbsolutePath + s"/$name"
    val future = command(Seq("mkfifo", fifoPath), asynch = false)
    val fifo = new File(fifoPath)
    fifo.deleteOnExit
    fifo
  }

  def command(cmd: Seq[String], asynch: Boolean = true) = {
    logInfo(s"executing command: ${cmd.mkString(" ")}")
    val future = Future {
      cmd ! ProcessLogger(
        (o: String) => logInfo(o),
        (e: String) => logInfo(e))
    }
    future onComplete {
      case Success(exitCode) => {
        if (exitCode != 0) {
          throw new RunException(s"${cmd.mkString(" ")} exited with non-zero exit code: $exitCode")
        } else {
          logInfo(s"successfully executed command: ${cmd.mkString(" ")}")
        }
      }
      case Failure(e) => {
        logWarning(
            s"exeption while running ${cmd.mkString(" ")} \n" + 
            e.getStackTraceString
        )
      }
    }
    if (!asynch) {
      Await.ready(future, Duration.Inf)
    }
  }

}