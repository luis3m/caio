package caio

import caio.std.{CaioAsync, CaioApplicative, CaioClock, CaioConcurrent, CaioFunctor, CaioMonad, CaioMonadCancel, CaioSpawn, CaioTemporal}
import cats.{Align, Functor, Monoid, Parallel, SemigroupK, Traverse}
import cats.data.{Ior, NonEmptyList}
import cats.effect.{Async, Deferred, IO, Ref, Resource}
import cats.effect.kernel.{ParallelF, Poll}

import scala.annotation.tailrec
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

sealed trait Caio[-C, +V, +L, +A] {
  final def <*[C1 <: C, V1 >: V, L1 >: L](fb: => Caio[C1, V1, L1, Any]): Caio[C1, V1, L1, A] =
    flatMap[C1, V1, L1, A](a => fb.as(a))

  final def <*>[C1 <: C, V1 >: V, L1 >: L, B](fb: => Caio[C1, V1, L1, B]): Caio[C1, V1, L1, (A, B)] =
    flatMap[C1, V1, L1, (A, B)](a => fb.map(b => (a, b)))

  final def *>[C1 <: C, V1 >: V, L1 >: L, B](fb: => Caio[C1, V1, L1, B]): Caio[C1, V1, L1, B] =
    flatMap[C1, V1, L1, B](_ => fb)

  final def <&[C1 <: C, V1 >: V, L1 >: L: Monoid](that: Caio[C1, V1, L1, Any]): Caio[C1, V1, L1, A] =
    both(that).map { case (a, _) => a }

  final def <&>[C1 <: C, V1 >: V, L1 >: L: Monoid, B](that: Caio[C1, V1, L1, B]): Caio[C1, V1, L1, (A, B)] =
    both(that)

  final def &>[C1 <: C, V1 >: V, L1 >: L: Monoid, B](that: Caio[C1, V1, L1, B]): Caio[C1, V1, L1, B] =
    both(that).map { case (_, b) => b }

  final def as[B](b: => B): Caio[C, V, L, B] =
    this.map(_ => b)

  final def attempt: Caio[C, V, L, Either[Throwable, A]] =
    map(Right.apply).handleErrorWith(ex => Caio.pure(Left(ex)))

  final def background[C1 <: C, V1 >: V, L1 >: L, A1 >: A](implicit M: Monoid[L1]): Resource[Caio[C1, V1, L1, *], Caio[C1, V1, L1, OutcomeCaio[C1, V1, L1, A1]]] =
    CaioSpawn[C1, V1, L1].background(this)

  final def bracket[C1 <: C, V1 >: V, L1 >: L, A1 >: A, B]
    (use: A1 => Caio[C1, V1, L1, B])
    (release: A1 => Caio[C1, V1, L1, Unit])
    (implicit M: Monoid[L1]): Caio[C1, V1, L1, B] =
    bracketCase(use){ case (a, _) => release(a) }

  final def bracketCase[C1 <: C, V1 >: V, L1 >: L, A1 >: A, B]
    (use: A1 => Caio[C1, V1, L1, B])
    (release: (A1, OutcomeCaio[C1, V1, L1, B]) => Caio[C1, V1, L1, Unit])
    (implicit M: Monoid[L1]): Caio[C1, V1, L1, B] =
    CaioMonadCancel[C1, V1, L1].bracketCase(this)(use)(release)

  final def both[C1 <: C, V1 >: V, L1 >: L: Monoid, B](other: => Caio[C1, V1, L1, B]): Caio[C1, V1, L1, (A, B)] =
    CaioAsync[C1, V1, L1].both(this, other)

  final def censor[L1 >: L](f: L1 => L1): Caio[C, V, L1, A] =
    Caio.CensorCaio(this, f)

  final def clear[L1 >: L](implicit M: Monoid[L1]): Caio[C, V, L1, A] =
    censor[L1](_ => M.empty)

  final def delayBy[L1 >: L](duration: FiniteDuration)(implicit M: Monoid[L1]): Caio[C, V, L1, A] =
    sleep[C, V, L1](duration) *> this

  final def either: Caio[C, Nothing, L, Either[NonEmptyList[V], A]] =
    map(Right.apply).handleFailuresWith[C, V, Nothing, L, Either[NonEmptyList[V], A]](fs => Caio.pure(Left(fs)))

  final def flatMap[C1 <: C, V1 >: V, L1 >: L, B](f: A => Caio[C1, V1, L1, B]): Caio[C1, V1, L1, B] =
    Caio.BindCaio(this, f)

  final def flatten[C1 <: C, V1 >: V, L1 >: L, B](implicit ev: A <:< Caio[C1, V1, L1, B]): Caio[C1, V1, L1, B] =
    flatMap[C1, V1, L1, B](ev)

  final def forceR[C1 <: C, V1 >: V, L1 >: L, B](other: Caio[C1, V1, L1, B]): Caio[C1, V1, L1, B] =
    this.either.attempt *> other

  final def foreverM: Caio[C, V, L, Nothing] =
    CaioMonad[C, V, L].foreverM[A, Nothing](this)

  final def guarantee[C1 <: C, V1 >: V, L1 >: L: Monoid, A1 >: A](finalizer: Caio[C1, V1, L1, Unit]): Caio[C1, V1, L1, A1] =
    guaranteeCase[C1, V1, L1, A1](_ => finalizer)

  final def guaranteeCase[C1 <: C, V1 >: V, L1 >: L: Monoid, A1 >: A](finalizer: OutcomeCaio[C1, V1, L1, A1] => Caio[C1, V1, L1, Unit]): Caio[C1, V1, L1, A1] =
    CaioMonadCancel[C1, V1, L1].guaranteeCase[A1](this)(finalizer)

  final def handleErrorWith[C1 <: C, V1 >: V, L1 >: L, A2 >: A](f: Throwable => Caio[C1, V1, L1, A2]): Caio[C1, V1, L1, A2] =
    Caio.HandleErrorCaio[C1, V1, L1, A2](this, { case ex: Throwable => f(ex) })

  final def handleFailuresWith[C1 <: C, V1 >: V, V2, L1 >: L, A2 >: A](f: NonEmptyList[V1] => Caio[C1, V2, L1, A2]): Caio[C1, V2, L1, A2] =
    Caio.HandleFailureCaio[C1, V1, V2, L1, A2](this, { case failures: NonEmptyList[V1] => f(failures) })

  final def handleSomeErrorWith[C1 <: C, V1 >: V, L1 >: L, A1 >: A](f: PartialFunction[Throwable, Caio[C1, V1, L1, A1]]): Caio[C1, V1, L1, A1] =
    Caio.HandleErrorCaio(this, f)

  final def handleSomeFailuresWith[C1 <: C, V1 >: V, V2, L1 >: L, A2 >: A](f: PartialFunction[NonEmptyList[V1], Caio[C1, V2, L1, A2]]): Caio[C1, V2, L1, A2] =
    Caio.HandleFailureCaio(this, f)

  final def iterateUntil(p: A => Boolean): Caio[C, V, L, A] =
    CaioMonad[C, V, L].iterateUntil(this)(p)

  final def iterateWhile(p: A => Boolean): Caio[C, V, L, A] =
    CaioMonad[C, V, L].iterateWhile(this)(p)

  final def listen: Caio[C, V, L, (A, L)] =
    Caio.ListenCaio(this)

  final def localContext[C1 <: C](f: C1 => C1): Caio[C1, V, L, A] =
    Caio.LocalContextCaio(this, f)

  final def map[B](f:A => B): Caio[C, V, L, B] =
    Caio.MapCaio(this, f)

  final def memoize[L1 >: L: Monoid]: Caio[C, V, L1, Caio[C, V, L1, A]] =
    CaioConcurrent[C, V, L1].memoize(this)

  final def onCancel[C1 <: C, V1 >: V, L1 >: L: Monoid](finalizer: Caio[C1, V1, L1, Unit]): Caio[C1, V1, L1, A] =
    Caio.getContext[C1].flatMap { c =>
      Caio.RefIOCaio[L1, A] { ref =>
        Caio.run(this, c, ref).onCancel(Caio.run(finalizer, c, ref))
      }
    }

  final def provideContext[L1 >: L](c: C)(implicit M: Monoid[L1]): Caio[Any, V, L1, A] =
    Caio.RefIOCaio[L1, A](ref => Caio.run[C, V, L1, A](this, c, ref))

  final def race[C1 <: C, V1 >: V, L1 >: L, B](other: Caio[C1, V1, L1, B])(implicit M: Monoid[L1]): Caio[C1, V1, L1, Either[A, B]] =
    CaioSpawn[C1, V1, L1].race(this, other)

  final def racePair[C1 <: C, V1 >: V, L1 >: L, A1 >: A, B](other: Caio[C1, V1, L1, B])(implicit M: Monoid[L1])
    : Caio[C1, V1, L1, Either[(OutcomeCaio[C1, V1, L1, A1], FiberCaio[C1, V1, L1, B]), (FiberCaio[C1, V1, L1, A1], OutcomeCaio[C1, V1, L1, B])]] =
    CaioSpawn[C1, V1, L1].racePair[A1, B](this, other)

  final def redeem[B](recover: Throwable => B, map: A => B): Caio[C, V, L, B] =
    attempt.map(_.fold(recover, map))

  final def redeemAll[B](recover: Throwable => B, handleFailures: NonEmptyList[V] => B, map: A => B): Caio[C, Nothing, L, B] =
    redeem(recover, map).handleFailuresWith[C, V, Nothing, L, B](fs => Caio.pure(handleFailures(fs)))

  final def redeemAllWith[C1 <: C, V1 >: V, V2, L1 >: L, B](
    recover: Throwable => Caio[C1, V1, L1, B],
    handleFailures: NonEmptyList[V1] => Caio[C1, V2, L1, B],
    bind: A => Caio[C1, V1, L1, B]
  ): Caio[C1, V2, L1, B] =
    redeemWith[C1, V1, L1, B](recover, bind).handleFailuresWith[C1, V1, V2, L1, B](handleFailures)

  final def redeemWith[C1 <: C, V1 >: V, L1 >: L, B](recover: Throwable => Caio[C1, V1, L1, B], bind: A => Caio[C1, V1, L1, B]): Caio[C1, V1, L1, B] =
    attempt.flatMap(_.fold(recover, bind))

  final def sleep[C1 <: C, V1 >: V, L1 >: L](duration: FiniteDuration)(implicit M: Monoid[L1]): Caio[C1, V1, L1, Unit] =
     CaioTemporal[C1, V1, L1].sleep(duration)

  final def start[C1 <: C, V1 >: V, L1 >: L, A1 >: A](implicit M: Monoid[L1]): Caio[C1, V1, L1, FiberCaio[C1, V1, L1, A1]] =
    CaioSpawn[C1, V1, L1].start[A1](this)

  final def tapError[C1 <: C, V1 >: V, L1 >: L](f: Throwable => Caio[C1, V1, L1, Any]): Caio[C1, V1, L1, A] =
    handleErrorWith(ex => f(ex) *> Caio.raiseError(ex))

  final def tapFailures[C1 <: C, V1 >: V, L1 >: L](f: NonEmptyList[V1] => Caio[C1, V1, L1, Any]): Caio[C1, V1, L1, A] =
    handleFailuresWith[C1, V1, V1, L1, A](fs => f(fs) *> Caio.failMany(fs))

  final def timeoutTo[C1 <: C, V1 >: V, L1 >: L, A1 >: A](duration: FiniteDuration, fallback: Caio[C1, V1, L1, A1])(implicit M: Monoid[L1]): Caio[C1, V1, L1, A1] =
    CaioTemporal[C1, V1, L1].timeoutTo(this, duration, fallback)

  final def timeout[C1 <: C, V1 >: V, L1 >: L](duration: FiniteDuration)(implicit M: Monoid[L1]): Caio[C1, V1, L1, A] =
    CaioTemporal[C1, V1, L1].timeout(this, duration)

  final def timed: Caio[C, V, L, (FiniteDuration, A)] =
    CaioClock[C, V, L].timed(this)

  final def uncancelable[L1 >: L: Monoid]: Caio[C, V, L1, A] =
    Caio.uncancelable[C, V, L1, A](_ => this)

  final def void: Caio[C, V, L, Unit] =
    as(())

  /**
   * Exceptions in stack will get thrown,
   * Failures will get thrown as CaioUnhandledFailuresException
   * @param c
   * @param M
   * @return
   */
  final def run[L1 >: L](c: C)(implicit M: Monoid[L1]): IO[A] =
    Caio.run[C, V, L1, A](this, c, Ref.unsafe[IO, L1](M.empty))

  final def run[C1 <: C, L1 >: L](implicit M: Monoid[L1], ev: C1 =:= Any): IO[A] =
    Caio.run[Any, V, L1, A](this.asInstanceOf[Caio[Any, V, L1, A]], (), Ref.unsafe[IO, L1](M.empty))

  final def runFail[L1 >: L](c: C)(implicit M: Monoid[L1]): IO[Either[NonEmptyList[V], A]] =
    Caio.runFail[C, V, L1, A](this, c, Ref.unsafe[IO, L1](M.empty))

  final def runFail[C1 <: C, L1 >: L](implicit M: Monoid[L1], ev: C1 =:= Any): IO[Either[NonEmptyList[V], A]] =
    Caio.runFail[Any, V, L1, A](this.asInstanceOf[Caio[Any, V, L1, A]], (), Ref.unsafe[IO, L1](M.empty))

  final def runContext[C1 <: C, V1 >: V, L1 >: L](c: C1)(implicit M: Monoid[L1]): IO[(C1, L1, Either[ErrorOrFailure[V1], A])] =
    Caio.foldIO[C1, V, L1, A](this, c, Ref.unsafe[IO, L1](M.empty)).map {
      case FoldCaioSuccess(cOut, l, a) =>
        (cOut, l, Right(a))
      case FoldCaioFailure(cOut, l, head, tail) =>
        (cOut, l, Left(Right(NonEmptyList(head, tail))))
      case FoldCaioError(cOut, l, ex: CaioUnhandledFailuresException[V]) =>
        (cOut, l, Left(Right(ex.failure)))
      case FoldCaioError(cOut, l, ex) =>
        (cOut, l, Left(Left(ex)))
    }
}

case class CaioUnhandledFailuresException[V](failure: NonEmptyList[V])
  extends Exception("Caio failures have not been handled.")

object Caio {
  type Par[C, V, L, A] = ParallelF[Caio[C, V, L, *], A]

  def apply[A](thunk: => A): Caio[Any, Nothing, Nothing, A] =
    IOCaio[A](IO.apply(thunk))

  def async[C, V, L: Monoid, A](k: (Either[Throwable, A] => Unit) => Caio[C, V, L, Option[Caio[C, V, L, Unit]]]): Caio[C, V, L, A] =
    CaioAsync[C, V, L].async[A](k)

  def async_[C, V, L: Monoid, A](k: (Either[Throwable, A] => Unit) => Unit): Caio[C, V, L, A] =
    CaioAsync[C, V, L].async_[A](k)

  def both[C, V, L: Monoid, A, B](`this`: Caio[C, V, L, A], that: Caio[C, V, L, B]): Caio[C, V, L, (A, B)] =
    `this`.both(that)

  def deferred[C, V, L: Monoid, A]: Caio[C, Nothing, Nothing, Deferred[Caio[C, V, L, *], A]] =
    effect(Deferred.unsafe[Caio[C, V, L, *], A])

  def effect[A](thunk: => A): Caio[Any, Nothing, Nothing, A] =
    apply(thunk)

  def fail[V](failure: V, failures: V*): Caio[Any, V, Nothing,  Nothing] =
    FailureCaio(failure, failures.toList)

  def failMany[V](failures: NonEmptyList[V]): Caio[Any, V, Nothing,  Nothing] =
    FailureCaio(failures.head, failures.tail)

  def fromEither[A](either: => Either[Throwable, A]): Caio[Any, Nothing, Nothing, A] =
    IOCaio(IO.fromEither(either))

  def fromEitherFailure[V, A](either: => Either[V, A]): Caio[Any, V, Nothing, A] =
    fromTry(Try(either)).flatMap(_.fold(FailureCaio(_, Nil), PureCaio(_)))

  def fromFuture[C, V, L, A](caio: Caio[C, V, L, Future[A]]): Caio[C, V, L, A] =
    caio.flatMap(future => IOCaio(IO.fromFuture(IO(future))))

  def fromIOFuture[A](iof: IO[Future[A]]): Caio[Any, Nothing, Nothing, A] =
    IOCaio(IO.fromFuture(iof))

  def fromTry[A](`try`: Try[A]): Caio[Any, Nothing, Nothing, A] =
    IOCaio(IO.fromTry(`try`))

  def getContext[C]: Caio[C, Nothing, Nothing, C] =
    GetContextCaio()

  def liftIO[A](io: => IO[A]): Caio[Any, Nothing, Nothing, A] =
    IOCaio(io)

  def modifyContext[C](f: C => C): Caio[C, Nothing, Nothing, Unit] =
    getContext[C].flatMap(c => setContext(f(c)))

  def never[A]: Caio[Any, Nothing, Nothing, A] =
    IOCaio(IO.never[A])

  def none[A]: Caio[Any, Nothing, Nothing, Option[A]] =
    pure(None)

  def parTraverseN[C, V, L: Monoid, T[_]: Traverse, A, B](n: Int)(ta: T[A])(f: A => Caio[C, V, L, B]): Caio[C, V, L, T[B]] =
    asyncForCaio[C, V, L].parTraverseN(n)(ta)(f)

  def parSequenceN[C, V, L: Monoid, T[_]: Traverse, A](n: Int)(tma: T[Caio[C, V, L, A]]): Caio[C, V, L, T[A]] =
    asyncForCaio[C, V, L].parSequenceN(n)(tma)

  def pure[A](a: A): Caio[Any, Nothing, Nothing, A] =
    PureCaio(a)

  def raiseError(ex: Throwable): Caio[Any, Nothing, Nothing, Nothing] =
    ErrorCaio(ex)

  def race[C, V, L: Monoid, A, B](fa: Caio[C, V, L, A], fb: Caio[C, V, L, B]): Caio[C, V, L, Either[A, B]] =
    fa.race(fb)

  def setContext[C](context: C): Caio[C, Nothing, Nothing, Unit] =
    SetContextCaio(context)

  def sequence[C, V, L, T[_]: Traverse, A](tma: T[Caio[C, V, L, A]]): Caio[C, V, L, T[A]] =
    Traverse[T].sequence(tma)(CaioApplicative[C, V, L])

  def sleep[C, V, L: Monoid](duration: FiniteDuration): Caio[C, V, L, Unit] =
    CaioTemporal[C, V, L].sleep(duration)

  def some[A](a: A): Caio[Any, Nothing, Nothing, Option[A]] =
    pure(Some(a))

  def tell[L](l: L):Caio[Any, Nothing, L, Unit] =
    TellCaio[L](l)

  def traverse[C, V, L, T[_]: Traverse, A, B](ta: T[A])(f: A => Caio[C, V, L, B]): Caio[C, V, L, T[B]] =
    Traverse[T].traverse(ta)(f)(CaioApplicative[C, V, L])

  def uncancelable[C, V, L: Monoid, A](body: Poll[Caio[C, V, L, *]] => Caio[C, V, L, A]): Caio[C, V, L, A] =
    GetContextCaio[C]().flatMap { c =>
      RefIOCaio[L, A] { ref =>
        IO.uncancelable { poll =>
          val newPoll = new Poll[Caio[C, V, L, *]] {
            def apply[X](fa: Caio[C, V, L, X]): Caio[C, V , L, X] =
              KleisliCaio[C, V, L, X] { case (cc, _) =>
                poll(foldIO(fa, cc, ref))
              }
          }

          run(body(newPoll), c, ref)
        }
      }
    }

  def unit: Caio[Any, Nothing, Nothing, Unit] =
    pure(())

  implicit def alignForCaio[C, V, L]: Align[Caio[C, V, L, *]] =
    new Align[Caio[C, V, L, *]] {
      def functor: Functor[Caio[C, V, L, *]] =
        CaioFunctor[C, V, L]

      def align[A, B](fa: Caio[C,V,L,A], fb: Caio[C, V, L, B]): Caio[C, V, L, Ior[A, B]] =
        fa.redeemWith(
          t => fb.redeemWith(_ => raiseError(t), b => pure(Ior.right(b))),
          a => fb.redeem(_ => Ior.left(a), b => Ior.both(a, b))
        )
    }

  implicit def asyncForCaio[C, V, L: Monoid]: Async[Caio[C, V, L, *]] =
    CaioAsync[C, V, L]

  implicit def monoidForCaio[C, V, L, A](implicit M: Monoid[A]): Monoid[Caio[C, V, L, A]] =
    new Monoid[Caio[C, V, L, A]] {
      def combine(left: Caio[C, V, L, A], right: Caio[C, V, L, A]): Caio[C, V, L, A] =
        left.flatMap(l => right.map(r => M.combine(l, r)))

      def empty: Caio[C, V, L, A] =
        Caio.pure(M.empty)
    }

  implicit def parallelForCaio[C, V, L: Monoid]: Parallel.Aux[Caio[C, V, L, *], Par[C, V, L, *]] =
    cats.effect.instances.spawn.parallelForGenSpawn[Caio[C, V, L, *], Throwable]

  implicit def semigroupKForIO[C, V, L]: SemigroupK[Caio[C, V, L, *]] =
    new SemigroupK[Caio[C, V, L, *]] {
      final override def combineK[A](a: Caio[C, V, L, A], b: Caio[C, V, L, A]): Caio[C, V, L, A] =
        a.handleErrorWith(_ => b)
    }

  private[caio] def run[C, V, L: Monoid, A](caio: Caio[C, V, L, A], c: C, ref: Ref[IO, L]): IO[A] =
    foldIO[C, V, L, A](caio, c, ref).map {
      case FoldCaioSuccess(_, _, a) =>
        a
      case FoldCaioFailure(_, _, head, tail) =>
        throw CaioUnhandledFailuresException(NonEmptyList(head, tail))
      case FoldCaioError(_, _, ex) =>
        throw ex
    }

  private[caio] def runFail[C, V, L: Monoid, A](caio: Caio[C, V, L, A], c: C, ref: Ref[IO, L]): IO[Either[NonEmptyList[V], A]] =
    foldIO[C, V, L, A](caio, c, ref).map {
      case FoldCaioSuccess(_, _, a) =>
        Right(a)
      case FoldCaioFailure(_, _, head, tail) =>
        Left(NonEmptyList(head, tail))
      case FoldCaioError(_, _, ex: CaioUnhandledFailuresException[V @unchecked]) =>
        Left(ex.failure)
      case FoldCaioError(_, _, ex) =>
        throw ex
    }

  private[caio] def foldIO[C, V, L, A](caio: Caio[C, V, L, A], c: C, ref: Ref[IO, L])(implicit M: Monoid[L]): IO[FoldCaioPure[C, V, L, A]] = {
    type Continuation = (Any, Any) => Caio[Any, Any, Any, Any]
    type ErrorRecovery = (Any, Throwable) => Caio[Any, Any, Any, Any]
    type FailureRecovery = (Any, NonEmptyList[Any]) => Caio[Any, Any, Any, Any]

    sealed trait Handler
    case class OnSuccess(f: Continuation) extends Handler
    case class OnFailure(f: FailureRecovery) extends Handler
    case class OnError(f: ErrorRecovery) extends Handler

    def tryOrError(value: => Caio[Any, Any, Any, Any]): Caio[Any, Any, Any, Any] =
      try value
      catch { case NonFatal(ex) => ErrorCaio(ex) }

    @tailrec def nextHandler(fs: List[Handler]): Option[(Continuation, List[Handler])] =
      fs match {
        case OnSuccess(c) :: cs => Some((c, cs))
        case _ :: cs => nextHandler(cs)
        case Nil => None
      }

    @tailrec def nextErrorHandler(fs: List[Handler]): Option[(ErrorRecovery, List[Handler])] =
      fs match {
        case OnError(c) :: cs => Some((c, cs))
        case _ :: cs => nextErrorHandler(cs)
        case Nil => None
      }

    @tailrec def nextFailureHandler(fs: List[Handler]): Option[(FailureRecovery, List[Handler])] =
      fs match {
        case OnFailure(c) :: cs => Some((c, cs))
        case _ :: cs => nextFailureHandler(cs)
        case Nil => None
      }

    /**
     * Recursive fold of Caio GADT.
     * Requires Exception handling on function evaluation
     * Requires trampolining
     * @param caio
     * @param c
     * @param l
     * @tparam B
     * @return
     */
    def safeFold(caio: Caio[Any, Any, Any, Any], c: Any, handlers: List[Handler]): FoldCaio[Any, Any, Any, Any] = {
      @tailrec def foldCaio(caio: Caio[Any, Any, Any, Any], c: Any, handlers: List[Handler]): FoldCaio[Any, Any, Any, Any] =
        caio match {
          case PureCaio(a) =>
            nextHandler(handlers) match {
              case Some((f, fs)) =>
                foldCaio(tryOrError(f(c, a)), c, fs)
              case None =>
                FoldCaioIO(ref.get.map(l => FoldCaioSuccess(c, l, a)))
            }

          case caio: IOCaio[_] =>
            //The IO monad will bring this back into stack safety
            FoldCaioIO(caio.f().redeemWith(
              e => safeFold(ErrorCaio(e), c, handlers).toIO,
              a => safeFold(PureCaio(a), c, handlers).toIO
            ))

          case RefIOCaio(f) =>
            //The IO monad will bring this back into stack safety
            FoldCaioIO(f(ref.asInstanceOf[Ref[IO, Any]]).redeemWith(
              e => safeFold(ErrorCaio(e), c, handlers).toIO,
              a => safeFold(PureCaio(a), c, handlers).toIO
            ))

          case KleisliCaio(f) =>
            Try(f(c, ref.asInstanceOf[Ref[IO, Any]])) match {
                //Doesnt support Error or Failure handling
              case scala.util.Success(foldIO) =>
                FoldCaioIO {
                  foldIO.flatMap {
                    case FoldCaioSuccess(c, _, a) =>
                      //The IO monad will bring this back into stack safety
                      safeFold(PureCaio(a), c, handlers).toIO
                    case FoldCaioFailure(c, _, head, tail) =>
                      safeFold(FailureCaio(head, tail), c, handlers).toIO
                    case FoldCaioError(c, _, ex) =>
                      safeFold(ErrorCaio(ex), c, handlers).toIO
                  }.handleErrorWith(ex => safeFold(ErrorCaio(ex), c, handlers).toIO)
                }
              case scala.util.Failure(ex) =>
                foldCaio(ErrorCaio(ex), c, handlers)
            }

          case MapCaio(source, f) =>
            foldCaio(source, c,  OnSuccess((_, a) => PureCaio(f(a))) :: handlers)

          case BindCaio(source, f) =>
            foldCaio(source, c, OnSuccess((_, a) => f(a)) :: handlers)

          case ErrorCaio(e) =>
            nextErrorHandler(handlers) match {
              case Some((f, fs)) =>
                foldCaio(tryOrError(f(c, e)), c,  fs)
              case None =>
                FoldCaioIO(ref.get.map(l => FoldCaioError(c, l, e)))
            }

          case HandleErrorCaio(source, f) =>
            foldCaio(source, c, OnError((_, e) => if (f.isDefinedAt(e)) f(e) else ErrorCaio(e)) :: handlers)

          case FailureCaio(head, tail) =>
            nextFailureHandler(handlers) match {
              case Some((f, fs)) =>
                foldCaio(tryOrError(f(c, NonEmptyList(head, tail))), c, fs)
              case None =>
                FoldCaioIO(ref.get.map(l => FoldCaioFailure(c, l, head, tail)))
            }

          case HandleFailureCaio(source, f) =>
            foldCaio(source, c,
              OnError((_, e) =>
                if (e.isInstanceOf[CaioUnhandledFailuresException[Any @unchecked]]) {
                  val NonEmptyList(head, tail) = e.asInstanceOf[CaioUnhandledFailuresException[Any]].failure
                  FailureCaio(head, tail)
                } else
                  ErrorCaio(e)
              ) ::
              OnFailure((_, e) => if (f.isDefinedAt(e)) f(e) else FailureCaio(e.head, e.tail)) ::
              handlers
            )

          case TellCaio(l2) =>
            foldCaio(IOCaio(ref.update(l => M.combine(l.asInstanceOf[L], l2.asInstanceOf[L]))), c, handlers)

          case ListenCaio(source) =>
            foldCaio(source, c,  OnSuccess((_, a) => IOCaio(ref.get.map(a -> _))) :: handlers)

          case CensorCaio(source, f) =>
            foldCaio(source, c, OnSuccess((_, a) => MapCaio(IOCaio(ref.update(l => f(l).asInstanceOf[L])), (_: Any) => a)) :: handlers)

          case GetContextCaio() =>
            foldCaio(PureCaio(c), c, handlers)

          case SetContextCaio(replaceC) =>
            foldCaio(Caio.unit, replaceC, handlers)

          case LocalContextCaio(fa, f) =>
            foldCaio(fa, f(c),
              OnSuccess((_, a) => MapCaio(SetContextCaio(c), (_: Unit) => a)) :: 
              OnError((_, e) => BindCaio(SetContextCaio(c), (_: Unit) => ErrorCaio(e))) ::
              OnFailure((_, e) => BindCaio(SetContextCaio(c), (_: Unit) => FailureCaio(e.head, e.tail))) ::
              handlers
            )
        }

      foldCaio(caio, c, handlers)
    }

    IO.defer(safeFold(caio.asInstanceOf[Caio[Any, Any, Any, Any]], c, Nil).asInstanceOf[FoldCaio[C, V, L, A]].toIO)
  }

  final private case class PureCaio[+A](a: A) extends Caio[Any, Nothing, Nothing, A]

  final private class IOCaio[+A] private(val f: () => IO[A]) extends Caio[Any, Nothing, Nothing, A]

  private object IOCaio {
    def apply[A](a: => IO[A]) = new IOCaio(() => a)
    def unapply[A](io: IOCaio[A]): Option[() => IO[A]] = Some(io.f)
  }

  final private[caio] case class RefIOCaio[L, +A](f: Ref[IO, L] => IO[A]) extends Caio[Any, Nothing, L, A]

  final private[caio] case class KleisliCaio[C, V, L, +A](kleisli: (C, Ref[IO, L]) => IO[FoldCaioPure[C, V, L, A]]) extends Caio[C, V, L, A]

  final private case class MapCaio[-C, +V, +L, E, +A](source: Caio[C, V, L, E], f: E => A) extends Caio[C, V, L, A]

  final private case class BindCaio[-C, +V, +L, E, +A](source: Caio[C, V, L, E], f: E => Caio[C, V, L, A]) extends Caio[C, V, L, A]

  final private case class ErrorCaio(e: Throwable) extends Caio[Any, Nothing, Nothing, Nothing]

  final private case class HandleErrorCaio[-C, +V, +L, +A](source: Caio[C, V, L, A], f: PartialFunction[Throwable, Caio[C, V, L, A]]) extends Caio[C, V, L, A]

  final private case class FailureCaio[+V](head: V, tail: List[V]) extends Caio[Any, V, Nothing, Nothing]

  final private case class HandleFailureCaio[-C, V, V1, +L, +A](source: Caio[C, V, L, A], f: PartialFunction[NonEmptyList[V], Caio[C, V1, L, A]]) extends Caio[C, V1, L, A]

  final private case class TellCaio[+L](l: L) extends Caio[Any, Nothing, L, Unit]

  final private case class ListenCaio[-C, +V, +L, +A](source: Caio[C, V, L, A]) extends Caio[C, V, L, (A, L)]

  final private case class CensorCaio[-C, +V, L, +A](source: Caio[C, V, L, A], f: L => L) extends Caio[C, V, L, A]

  final private case class GetContextCaio[C]() extends Caio[C, Nothing, Nothing, C]

  final private case class SetContextCaio[C](c: C) extends Caio[C, Nothing, Nothing, Unit]

  final private case class LocalContextCaio[C, V, L, A](source: Caio[C, V, L, A], f: C => C) extends Caio[C, V, L, A]
}