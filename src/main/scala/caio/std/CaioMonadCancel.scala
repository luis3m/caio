package caio.std

import caio.Caio
import cats.effect.IO
import cats.effect.kernel.{CancelScope, MonadCancel, Poll}

abstract class CaioMonadCancel[C, V, L] extends CaioMonadError[C, V, L] with MonadCancel[Caio[C, V, L, *], Throwable] {
  final def forceR[A, B](caioA: Caio[C, V, L, A])(caioB: Caio[C, V, L, B]): Caio[C, V, L, B] =
    caioA.forceR(caioB)

  final def uncancelable[A](body: Poll[Caio[C, V, L, *]] => Caio[C, V, L, A]): Caio[C, V, L, A] =
    Caio.uncancelable(body)

  final def canceled: Caio[C, V, L, Unit] =
    Caio.liftIO(MonadCancel[IO, Throwable].canceled)

  final def onCancel[A](caio: Caio[C, V, L, A], finalizer: Caio[C, V, L, Unit]): Caio[C, V, L, A] =
    caio.onCancel(finalizer)
}

object CaioMonadCancel {
  def apply[C, V, L]: CaioMonadCancel[C, V, L] =
    new CaioMonadCancel[C, V, L] {
      override final def rootCancelScope: CancelScope =
        CancelScope.Cancelable
    }
}