package caio.std

import caio.Caio
import cats.effect.kernel.Clock
import cats.effect.IO
import scala.concurrent.duration.FiniteDuration
import cats.Applicative

trait CaioClock[C, V, L] extends CaioApplicative[C, V, L] with Clock[Caio[C, V, L, *]] {  
  final def monotonic: Caio[C, V, L, FiniteDuration] =
    Caio.liftIO(IO.monotonic)
  
  final def realTime: Caio[C, V, L, FiniteDuration] =
    Caio.liftIO(IO.realTime)
}

object CaioClock {
  def apply[C, V, L]: CaioClock[C, V, L] =
    new CaioClock[C, V, L] {
      def applicative: Applicative[Caio[C, V, L , *]] =
        new CaioApplicative[C, V, L]
    }
}