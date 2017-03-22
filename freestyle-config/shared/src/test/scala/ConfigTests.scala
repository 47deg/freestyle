package freestyle

import org.scalatest._

import freestyle.implicits._
import freestyle.config._
import freestyle.config.implicits._
import scala.concurrent.{ExecutionContext, Future}
import cats.instances.future._

class ConfigTests extends AsyncWordSpec with Matchers {

  import algebras._

  implicit override def executionContext = ExecutionContext.Implicits.global

  "Shocon config integration" should {

    "allow configuration to be interleaved inside a program monadic flow" in {
      val program = for {
        _      <- app.nonConfig.x
        config <- app.configM.empty
      } yield config
      program.exec[Future] map { _.isInstanceOf[Config] shouldBe true }
    }

    "allow configuration to parse strings" in {
      val program = app.configM.parseString("{n = 1}")
      program.exec[Future] map { _.int("n") shouldBe Some(1) }
    }

    "allow configuration to load classpath files" in {
      val program = app.configM.load
      program.exec[Future] map { _.int("s") shouldBe Some(3) }
    }

  }

}

object algebras {
  @free
  trait NonConfig {
    def x: OpSeq[Int]
  }

  implicit def nonConfigHandler: NonConfig.Handler[Future] =
    new NonConfig.Handler[Future] {
      def x: Future[Int] = Future.successful(1)
    }

  @module
  trait App {
    val nonConfig: NonConfig
    val configM: ConfigM
  }

  val app = App[App.Op]

}
