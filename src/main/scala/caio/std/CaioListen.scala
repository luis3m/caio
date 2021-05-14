package caio.std

import caio.Caio
import cats.Monoid
import cats.mtl.Listen

class CaioListen[C, V, L: Monoid] extends CaioTell[C, V, L] with Listen[Caio[C, V, L, *], L]  {
  final def listen[A](fa: Caio[C, V, L, A]): Caio[C, V, L, (A, L)] =
    fa.listen

  override final def listens[A, B](fa: Caio[C, V, L, A])(f: L => B): Caio[C, V, L, (A, B)] =
    fa.listen.map { case (a, l) => a -> f(l) }
}
