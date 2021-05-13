package caio.std

import caio._
import cats.Monoid
import cats.effect.{IO, Sync}
import cats.effect.kernel.CancelScope

abstract class CaioSync[C, V, L: Monoid] extends CaioMonadCancel[C, V, L] with Sync[Caio[C, V, L, *]] with CaioClock[C, V, L] {
  final def suspend[A](hint: Sync.Type)(thunk: => A): Caio[C, V, L, A] =
    Caio.liftIO(IO.suspend(hint)(thunk))
}

object CaioSync {
  def apply[C, V, L: Monoid]: CaioSync[C, V, L] =
    new CaioSync[C, V, L] {
      override final def rootCancelScope: CancelScope =
        CancelScope.Cancelable
    }
}