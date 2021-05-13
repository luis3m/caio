package caio.implicits

import caio.Caio
import caio.mtl.{ApplicativeFail, InvariantAsk}
import caio.std._

import cats.{CommutativeMonad, Monoid, Parallel}
import cats.mtl.{Censor, Local, Stateful}
import cats.effect.{Async, Concurrent, LiftIO, Sync}
import cats.effect.instances.spawn

class StaticImplicits[C, V, L] {

  implicit val staticCaioMonad: CommutativeMonad[Caio[C,V,L, *]] =
    new CaioMonad[C, V, L]

  implicit val staticCaioSync: Sync[Caio[C,V,L, *]] =
    CaioSync[C, V, L]

  implicit val staticCaioAsync: Async[Caio[C,V,L, *]] =
    new CaioAsync[C, V, L]()

  implicit val staticCaioConcurrent: Concurrent[Caio[C,V,L, *]] =
    CaioConcurrent[C, V, L]

  implicit val staticCaioApplicativeFail: ApplicativeFail[Caio[C, V, L, *], V] =
    new CaioApplicativeFail[C, V, L]

  implicit def staticCaioCensor(implicit ML: Monoid[L]): Censor[Caio[C, V, L, *], L] =
    new CaioCensor[C, V, L]()(ML)

  implicit val staticCaioAsk: InvariantAsk[Caio[C, V, L, *], C] =
    new CaioAsk[C, V, L]

  implicit val staticCaioLocal: Local[Caio[C, V, L, *], C] =
    new CaioLocal[C, V, L]

  implicit val staticCaioStateful: Stateful[Caio[C, V, L, *], C] =
    new CaioStateful[C, V, L]

  implicit val staticLiftIO: LiftIO[Caio[C, V, L, *]] =
    new CaioLiftIO[C, V, L]

  implicit val staticCaioParallel: Parallel.Aux[Caio[C, V, L, *], Caio.Par[C, V, L, *]] =
    spawn.parallelForGenSpawn[Caio[C, V, L, *], Throwable]
}
