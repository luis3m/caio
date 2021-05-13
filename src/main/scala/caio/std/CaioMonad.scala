package caio.std

import caio.Caio
import cats.{CommutativeMonad, StackSafeMonad}

class CaioMonad[C, V, L] extends CaioApplicative[C, V, L] with StackSafeMonad[Caio[C, V, L, *]] with CommutativeMonad[Caio[C, V, L, *]] {
  final def flatMap[A, B](fa: Caio[C, V, L, A])(f: A => Caio[C, V, L, B]): Caio[C, V, L, B] =
    fa.flatMap(f)

  override final def map[A, B](fa: Caio[C, V, L, A])(f: A => B): Caio[C, V, L, B] =
    fa.map(f)
}

object CaioMonad {
  def apply[C, V, L]: CaioMonad[C, V, L] =
    new CaioMonad[C, V, L] {}
}