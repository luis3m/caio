package caio.std

import caio._
import cats.Monoid
import cats.effect.IO
import cats.effect.kernel.{Deferred, GenConcurrent, Spawn, Ref, Unique}

abstract class CaioConcurrent[C, V, L: Monoid] extends CaioSpawn[C, V, L] with GenConcurrent[Caio[C, V, L, *], Throwable] { 
  final def ref[A](a: A): Caio[C, V, L, Ref[Caio[C, V, L, *], A]] =
    Caio.apply(Ref.unsafe[Caio[C, V, L, *], A](a)(CaioSync[C, V, L]))

  final def deferred[A]: Caio[C, V, L, Deferred[Caio[C, V, L, *], A]] =
    Caio.apply(Deferred.unsafe[Caio[C, V, L, *], A](CaioAsync[C, V, L]))
}

object CaioConcurrent {
  def apply[C, V, L: Monoid]: CaioConcurrent[C, V, L] =
    new CaioConcurrent[C, V, L] {
      final def unique: Caio[C,V,L, Unique.Token] =
        Caio.liftIO(Spawn[IO].unique)

      final def never[A]: Caio[C, V, L, A] =
        Caio.never
    }
}