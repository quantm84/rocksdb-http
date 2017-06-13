package rocksdbhttp

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import org.rocksdb.{Options, RocksDB}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class RocksDBHttp(path: String, host: String, port: Int) extends AutoCloseable {

  private val options = new Options().setCreateIfMissing(true)
  private val db = RocksDB.open(options, path)

  implicit private val system = ActorSystem()
  implicit private val materializer = ActorMaterializer()
  implicit private val executionContext = system.dispatcher

  private val serverSource = Http().bind(interface = host, port = port)
  private val bindingFuture = serverSource.to(Sink.foreach { connection =>
    connection.handleWithAsyncHandler(requestHandler)
  }).run()

  private def requestHandler(request: HttpRequest): Future[HttpResponse] = request match {
    case HttpRequest(GET, Uri.Path("/data"), _, _, _) =>
      request.discardEntityBytes()
      val key = request.uri.query().get("key")
      if (key.isDefined) {
        val value = key.map(_.getBytes)
          .map(db.get)
          .filter(_ != null)
          .getOrElse(Array[Byte]())
        Future.successful(HttpResponse(entity = value))
      } else {
        Future.successful(HttpResponse(404, entity = "No key."))
      }
    case HttpRequest(POST, Uri.Path("/data"), _, _, _) =>
      val key = request.uri.query().get("key")
      if (key.isDefined) {
        request.entity
          .dataBytes
          .runReduce(_ ++ _)
          .map(value => db.put(key.get.getBytes, value.toArray))
          .map(_ => HttpResponse(200, entity = "Done."))
      } else {
        request.discardEntityBytes()
        Future.successful(HttpResponse(404, entity = "No key."))
      }
    case _ =>
      request.discardEntityBytes()
      Future.successful(HttpResponse(404, entity = "Unknown request."))
  }

  override def close(): Unit = {
    Await.result(bindingFuture.map(_.unbind()), Duration.Inf)
    Await.result(system.terminate(), Duration.Inf)
    db.close()
    options.close()
  }
}

object RocksDBHttp {
  def main(args: Array[String]): Unit = {
    val rocksDBHttp = new RocksDBHttp(args(0), args(1), args(2).toInt)
    sys.addShutdownHook({
      rocksDBHttp.close()
    })
  }
}
