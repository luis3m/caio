package caio.implicits

import caio.Caio
import caio.mtl.{ApplicativeFail, Effectful}
import caio.std.{CaioApplicativeAsk, CaioAsync, CaioConcurrent, CaioContextShift, CaioEffectful, CaioMonadState, CaioParallel, CaioSync}
import cats.{Monad, Monoid, Parallel}
import cats.effect.{Async, Concurrent, ConcurrentEffect, ContextShift, IO, Sync}
import cats.mtl.{ApplicativeAsk, ApplicativeCensor, MonadState}

class DynamicContextImplicits[V, L](implicit ML: Monoid[L]) {

  private val static: StaticImplicits[Unit, V, L] =
    new StaticImplicits[Unit, V, L]()(ML) {}

  implicit def dynamicCaioMonad[C]: Monad[Caio[C, V, L, *]] =
    static.staticCaioMonad.asInstanceOf[Monad[Caio[C, V, L, *]]]

  implicit def dynamicCaioSync[C]: Sync[Caio[C, V, L, *]] =
    new CaioSync[C, V, L]()(ML)

  implicit def dynamicCaioAsync[C]: Async[Caio[C, V, L, *]] =
    new CaioAsync[C, V, L]()(ML)

  implicit def dynamicCaioConcurrent[C](implicit CS: ContextShift[IO]): Concurrent[Caio[C, V, L, *]] =
    new CaioConcurrent[C, V, L]()(ML, CS)

  implicit def dynamicCaioContextShift[C](implicit CS: ContextShift[IO]): ContextShift[Caio[C, V, L, *]] =
    new CaioContextShift[C, V, L]()(ML, CS)

  implicit def dynamicCaioParallel[C](implicit CS:ContextShift[IO]): Parallel[Caio[C, V, L, *]] =
    new CaioParallel[C, V, L]()(ML, CS)

  implicit def dynamicCaioApplicativeFail[C]: ApplicativeFail[Caio[C, V, L, *], V] =
    static.staticCaioApplicativeFail.asInstanceOf[ApplicativeFail[Caio[C, V, L, *], V]]

  implicit def dynamicCaioApplicativeCensor[C]: ApplicativeCensor[Caio[C, V, L, *], L] =
    static.staticCaioApplicativeCensor.asInstanceOf[ApplicativeCensor[Caio[C, V, L, *], L]]

  implicit def dynamicCaioApplicativeAsk[C]: ApplicativeAsk[Caio[C, V, L, *], C] =
    new CaioApplicativeAsk[C, V, L]

  implicit def dynamicCaioMonadState[C]: MonadState[Caio[C, V, L, *], C] =
    new CaioMonadState[C, V, L]

  implicit def dynamicCaioEffectful[C](implicit CE: ConcurrentEffect[Caio[Unit, V, L, *]]): Effectful[Caio[C, V, L, *]] =
    new CaioEffectful[C, V, L](dynamicCaioApplicativeAsk[C], CE)

}
