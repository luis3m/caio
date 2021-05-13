package caio.std

import caio.{Caio, CaioUnhandledFailuresException, FiberCaio, FoldCaioError, FoldCaioFailure, FoldCaioSuccess, FoldCaioPure, OutcomeCaio}
import cats.Monoid
import cats.effect.{OutcomeIO, IO, FiberIO}
import cats.effect.kernel.{Fiber, GenSpawn, Spawn, Outcome, Unique}
import cats.effect.unsafe.implicits.global
import cats.data.NonEmptyList

abstract class CaioSpawn[C, V, L: Monoid] extends CaioMonadCancel[C, V, L] with GenSpawn[Caio[C, V, L, *], Throwable] {
  final def start[A](fa: Caio[C, V, L, A]): Caio[C, V, L, FiberCaio[C, V, L, A]] =
    Caio.KleisliCaio[C, V, L, FiberCaio[C, V, L, A]] { case (c, ref) =>
      Caio.foldIO[C, V, L, A](fa, c, ref).start.map { fiber =>
        FoldCaioSuccess(c, Monoid[L].empty, fiber2Caio(fiber))
      }
    }
  
  final def cede: Caio[C,V,L,Unit] =
    Caio.liftIO(Spawn[IO].cede)
  
  def racePair[A, B](fa: Caio[C, V, L, A], fb: Caio[C, V, L, B]): Caio[C, V, L, Either[(OutcomeCaio[C, V, L, A], FiberCaio[C, V, L, B]), (FiberCaio[C, V, L, A], OutcomeCaio[C, V, L, B])]] =
    Caio.KleisliCaio[C, V, L, Either[((Option[C], OutcomeCaio[C, V, L, A]), FiberCaio[C, V, L, B]), (FiberCaio[C, V, L, A], (Option[C], OutcomeCaio[C, V, L, B]))]] { case (c, ref) =>
      val sa = Caio.foldIO[C, V, L, A](fa, c, ref)
      val sb = Caio.foldIO[C, V, L, B](fb, c, ref)

      IO.racePair(sa, sb).flatMap {
        case Left((outcomeIO @ (Outcome.Canceled() | Outcome.Errored(_)), fiberB)) =>
          ref.get.map(l => FoldCaioSuccess(c, l, Left((toOutcomeCaio(outcomeIO).copy(_1 = Some(c)), fiber2Caio(fiberB)))))

        case Left((Outcome.Succeeded(io), fiberB)) =>
          io.flatMap {
            case e: FoldCaioError[C, V, L, _] =>
              fiberB.cancel.map(_ => e)
            case f: FoldCaioFailure[C, V, L, _] =>
              fiberB.cancel.map(_ => f)
            case s: FoldCaioSuccess[C, V, L, A] =>
              IO.pure(s.map(a => Left((Some(s.c), Outcome.succeeded[Caio[C, V, L, *], Throwable, A](Caio.pure(a))) -> fiber2Caio(fiberB))))
          }

        case Right((fiberA, outcomeIO @ (Outcome.Canceled() | Outcome.Errored(_)))) =>
          ref.get.map(l => FoldCaioSuccess(c, l, Right((fiber2Caio(fiberA), toOutcomeCaio(outcomeIO).copy(_1 = Some(c))))))

        case Right((fiberA, Outcome.Succeeded(io))) =>
          io.flatMap {
            case e: FoldCaioError[C, V, L, _] =>
              fiberA.cancel.map(_ => e)
            case f: FoldCaioFailure[C, V, L, _] =>
              fiberA.cancel.map(_ => f)
            case s: FoldCaioSuccess[C, V, L, B] =>
              IO.pure(s.map(b => Right((fiber2Caio(fiberA), (Some(s.c), Outcome.succeeded[Caio[C, V, L, *], Throwable, B](Caio.pure(b)))))))
          }
      }
    } flatMap {
      case Left((tuple, fiber)) =>
        setContext(tuple).map(outcomeCaio => Left((outcomeCaio, fiber)))
      case Right((fiber, tuple)) =>
        setContext(tuple).map(outcomeCaio => Right((fiber, outcomeCaio)))
    }

  private def setContext[A](tuple: (Option[C], OutcomeCaio[C, V, L, A])): Caio[C, V, L, OutcomeCaio[C, V, L, A]] =
    tuple match {
      case (Some(c), outcomeCaio) =>
        Caio.setContext(c).as(outcomeCaio)
      case (None, outcomeCaio) =>
        Caio.pure(outcomeCaio)
    }

  private def toOutcomeCaio[A](outcomeIO: OutcomeIO[FoldCaioPure[C, V, L, A]]): (Option[C], OutcomeCaio[C, V, L, A]) =
    outcomeIO match {
      case Outcome.Canceled() =>
        (None, Outcome.canceled[Caio[C, V, L, *], Throwable, A])
      case Outcome.Errored(ex) =>
        (None, Outcome.errored[Caio[C, V, L, *], Throwable, A](ex))
      case Outcome.Succeeded(io) =>
        io.unsafeRunSync() match {
          case FoldCaioError(c, _, e) =>
            (Some(c), Outcome.errored[Caio[C, V, L, *], Throwable, A](e))
          case FoldCaioFailure(c, _, head, tail) =>
            (Some(c), Outcome.errored[Caio[C, V, L, *], Throwable, A](CaioUnhandledFailuresException[V](NonEmptyList(head, tail))))
          case FoldCaioSuccess(c, _, a) =>
            (Some(c), Outcome.succeeded(Caio.pure(a)))
        }
    }

  private def fiber2Caio[A](fiber: FiberIO[FoldCaioPure[C, V, L, A]]): FiberCaio[C, V, L, A] =
    new Fiber[Caio[C, V, L, *], Throwable, A] {
      final def cancel: Caio[C, V, L, Unit] =
        Caio.liftIO(fiber.cancel)
      
      final def join: Caio[C, V, L, OutcomeCaio[C, V, L, A]] =
        Caio.liftIO(fiber.join.map(toOutcomeCaio[A])).flatMap(setContext[A])
    }
}

object CaioSpawn {
  def apply[C, V, L: Monoid]: CaioSpawn[C, V, L] =
    new CaioSpawn[C, V, L] {
      def unique: Caio[C,V,L, Unique.Token] =
        Caio.liftIO(Spawn[IO].unique)

      def never[A]: Caio[C, V, L, A] =
        Caio.never
    }
}