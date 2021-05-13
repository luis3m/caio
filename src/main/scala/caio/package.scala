import cats.data.NonEmptyList

import cats.effect.{Fiber, Outcome}

package object caio {

  type <~>[F[_], G[_]] = BijectionK[F, G]

  type ErrorOrFailure[V] = Throwable Either NonEmptyList[V]

  type OutcomeCaio[C, V, L, A] = Outcome[Caio[C, V, L, *], Throwable, A]

  type FiberCaio[C, V, L, A] = Fiber[Caio[C, V, L, *], Throwable, A]

  implicit class ErrorOps[V](val eof: ErrorOrFailure[V]) extends AnyVal {
    def toThrowable:Throwable =
      eof.fold[Throwable](identity, CaioUnhandledFailuresException.apply)
  }

}
