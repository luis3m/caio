package caio.std

import caio.Caio
import cats.Functor

trait CaioFunctor[C, V, L] extends Functor[Caio[C, V, L, *]]{
  final def map[A, B](fa: Caio[C, V, L, A])(f: A => B): Caio[C, V, L, B] =
    fa.map(f)
}

object CaioFunctor {
  def apply[C, V, L]: CaioFunctor[C, V, L] =
    new CaioFunctor[C, V, L] {}
}