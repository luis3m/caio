package caio.std

import caio._
import cats.data.NonEmptyList
import cats.effect._
import cats.effect.std.Dispatcher
import cats.effect.unsafe.implicits.global
import scala.concurrent.Future

class CaioDispatcher[C, V, L]
  (c: C)
  (onSuccess: (C, Option[L]) => IO[Unit] = (_: C, _: Option[L]) => IO.unit)
  (onError: (Throwable, C, Option[L]) => IO[Unit] = (_: Throwable, _: C, _: Option[L]) => IO.unit)
  (onFailure: (NonEmptyList[V], C, Option[L]) => IO[Unit] = (_: NonEmptyList[V], _: C, _: Option[L]) => IO.unit)
  (dispatcher: Dispatcher[IO], closeDispatcher: IO[Unit])
  extends Dispatcher[Caio[C, V, L, *]] {

  import cats.instances.vector._
  import cats.syntax.parallel._

  def unsafeClose: Caio[C, V, L, Unit] =
    Caio.liftIO(closeDispatcher)

  def toIO[A](fa: Caio[C, V, L, A]): IO[FoldCaioPure[C, V, L, A]] =
    Caio.foldIO[C, V, L, A](fa, c, Ref.unsafe[IO, Option[L]](None))

  def unsafeToFutureCancelable[A](fa: Caio[C, V, L, A]): (Future[A], () => Future[Unit]) = {
    val io = IO.async[A](cb => toIO(fa).attempt.flatMap(handle(_, cb)))
    dispatcher.unsafeToFutureCancelable[A](io)
  }

  private def handle[A](either: Either[Throwable, FoldCaioPure[C, V, L, A]], cb: Either[Throwable, A] => Unit): IO[Option[IO[Unit]]] =
    either match {
      case Left(ex) =>
        Vector(IO(cb(Left(ex))), onError(ex, c, None)).parSequence_.as(None)
      case Right(FoldCaioSuccess(c2, l, a)) =>
        Vector(IO(cb(Right(a))), onSuccess(c2, l)).parSequence_.as(None)
      case Right(FoldCaioFailure(c2, l, head, tail)) =>
        val nel = NonEmptyList(head, tail)
        Vector(IO(cb(Left(CaioUnhandledFailuresException(nel)))), onFailure(nel, c2, l)).parSequence_.as(None)
      case Right(FoldCaioError(c2, l, ex@CaioUnhandledFailuresException(failures:NonEmptyList[V@unchecked]))) =>
        Vector(IO(cb(Left(ex))), onFailure(failures, c2, l)).parSequence_.as(None)
      case Right(FoldCaioError(c2, l, ex)) =>
        Vector(IO(cb(Left(ex))), onError(ex, c2, l)).parSequence_.as(None)
    }
}

object CaioDispatcher {
  def apply[C, V, L]
    (c: C)
    (onSuccess: (C, Option[L]) => IO[Unit] = (_: C, _: Option[L]) => IO.unit)
    (onError: (Throwable, C, Option[L]) => IO[Unit] = (_: Throwable, _: C, _: Option[L]) => IO.unit)
    (onFailure: (NonEmptyList[V], C, Option[L]) => IO[Unit] = (_: NonEmptyList[V], _: C, _: Option[L]) => IO.unit): Resource[Caio[C, V, L, *], CaioDispatcher[C, V, L]] =
    Resource.make[
      Caio[C, V, L, *],
      CaioDispatcher[C, V, L]
    ](Caio.apply(unsafe(c)(onSuccess)(onError)(onFailure)))(_.unsafeClose)

  def unsafe[C, V, L]
    (c: C)
    (onSuccess: (C, Option[L]) => IO[Unit] = (_: C, _: Option[L]) => IO.unit)
    (onError: (Throwable, C, Option[L]) => IO[Unit] = (_: Throwable, _: C, _: Option[L]) => IO.unit)
    (onFailure: (NonEmptyList[V], C, Option[L]) => IO[Unit] = (_: NonEmptyList[V], _: C, _: Option[L]) => IO.unit): CaioDispatcher[C, V, L] =
    Dispatcher[IO]
      .allocated
      .map { case (dispatcher, close) => new CaioDispatcher[C, V, L](c)(onSuccess)(onError)(onFailure)(dispatcher, close) }
      .unsafeRunSync()
}
