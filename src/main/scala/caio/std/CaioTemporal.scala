package caio.std

import caio.Caio
import cats.Monoid
import cats.effect.IO
import cats.effect.kernel.{GenTemporal, Spawn, Unique}
import scala.concurrent.duration.FiniteDuration

abstract class CaioTemporal[C, V, L: Monoid] extends CaioConcurrent[C, V, L] with GenTemporal[Caio[C, V, L, *], Throwable] with CaioClock[C, V, L] {
  final def sleep(time: FiniteDuration): Caio[C, V, L, Unit] =
    Caio.liftIO(IO.sleep(time))
}

object CaioTemporal {
  def apply[C, V, L: Monoid]: CaioTemporal[C, V, L] =
    new CaioTemporal[C, V, L] {
      final def unique: Caio[C,V,L, Unique.Token] =
        Caio.liftIO(Spawn[IO].unique)

      final def never[A]: Caio[C, V, L, A] =
        Caio.never
    }
}