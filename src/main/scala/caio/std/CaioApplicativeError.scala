package caio.std

import caio.Caio
import cats.ApplicativeError

class CaioApplicativeError[C, V, L] extends CaioApplicative[C, V, L] with ApplicativeError[Caio[C, V, L, *], Throwable] {
  final def raiseError[A](ex: Throwable): Caio[C, V, L, A] =
    Caio.raiseError(ex)

  final def handleErrorWith[A](fa: Caio[C, V, L, A])(f: Throwable => Caio[C, V, L, A]): Caio[C, V, L, A] =
    fa.handleErrorWith(f)
}
