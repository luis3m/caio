package caio.implicits

import caio.Caio
import caio.mtl.{ApplicativeFail, InvariantAsk}
import caio.std.{CaioAsk, CaioAsync, CaioConcurrent, CaioLiftIO, CaioLocal, CaioStateful, CaioSync}

import cats.{CommutativeMonad, Monoid, Parallel}
import cats.mtl.{Censor, Local, Stateful}
import cats.effect.{Async, Concurrent, LiftIO, Sync}

class DynamicContextImplicits[V, L](implicit ML: Monoid[L]) {

  private val static: StaticImplicits[Unit, V, L] =
    new StaticImplicits[Unit, V, L]()(ML) {}

  implicit def dynamicCaioMonad[C]: CommutativeMonad[Caio[C, V, L, *]] =
    static.staticCaioMonad.asInstanceOf[CommutativeMonad[Caio[C, V, L, *]]]

  implicit def dynamicCaioSync[C]: Sync[Caio[C, V, L, *]] =
    CaioSync[C, V, L](ML)

  implicit def dynamicCaioAsync[C]: Async[Caio[C, V, L, *]] =
    new CaioAsync[C, V, L]()(ML)

  implicit def dynamicCaioConcurrent[C]: Concurrent[Caio[C, V, L, *]] =
    CaioConcurrent[C, V, L](ML)

  implicit def dynamicCaioApplicativeFail[C]: ApplicativeFail[Caio[C, V, L, *], V] =
    static.staticCaioApplicativeFail.asInstanceOf[ApplicativeFail[Caio[C, V, L, *], V]]

  implicit def dynamicCaioCensor[C]: Censor[Caio[C, V, L, *], L] =
    static.staticCaioCensor.asInstanceOf[Censor[Caio[C, V, L, *], L]]

  implicit def dynamicCaioAsk[C]: InvariantAsk[Caio[C, V, L, *], C] =
    new CaioAsk[C, V, L]

  implicit def dynamicCaioLocal[C]: Local[Caio[C, V, L, *], C] =
    new CaioLocal[C, V, L]

  implicit def dynamicCaioStateful[C]: Stateful[Caio[C, V, L, *], C] =
    new CaioStateful[C, V, L]

  implicit def dynamicLiftIO[C]: LiftIO[Caio[C, V, L, *]] =
    new CaioLiftIO[C, V, L]

  implicit def dynamicCaioParallel[C]: Parallel.Aux[Caio[C, V, L, *], Caio.Par[C, V, L, *]] =
    static.staticCaioParallel.asInstanceOf[Parallel.Aux[Caio[C, V, L, *], Caio.Par[C, V, L, *]]]
}
