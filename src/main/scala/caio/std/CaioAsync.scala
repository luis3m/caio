package caio.std

import caio.Caio
import cats.{~>, Monoid}
import cats.effect.{Async, IO, Sync, Ref}
import cats.effect.kernel.{Cont, MonadCancel}

import scala.concurrent.ExecutionContext

class CaioAsync[C, V, L: Monoid] extends CaioTemporal[C, V, L] with Async[Caio[C, V, L, *]] {
  final def suspend[A](hint: Sync.Type)(thunk: => A): Caio[C, V, L, A] =
    Caio.liftIO(IO.suspend(hint)(thunk))

  final def evalOn[A](fa: Caio[C, V, L, A], ec: ExecutionContext): Caio[C, V, L, A] =
    Caio.KleisliCaio[C, V, L, A] { case (c, ref) =>
      Async[IO].evalOn(Caio.foldIO(fa, c, ref), ec)
    }
  
  final def executionContext: Caio[C, V, L, ExecutionContext] =
    Caio.liftIO(Async[IO].executionContext)
  
  final def cont[K, R](body: Cont[Caio[C, V, L, *], K, R]): Caio[C, V, L, R] =
    Caio.getContext[C].flatMap { c =>
      Caio.RefIOCaio[L, R] { ref  =>
        Async[IO].cont {
          new Cont[IO, K, R] {
            def apply[G[_]: MonadCancel[*[_], Throwable]]: (Either[Throwable, K] => Unit, G[K], IO ~> G) => G[R] = {
              (resume, get, lift) =>
                body[G].apply(resume, get, liftCaio(lift, c, ref))
            }
          }
        }
      }
    }

  private def liftCaio[F[_]](liftF: IO ~> F, context: C, ref: Ref[IO, L]): Caio[C, V, L, *] ~> F =
    new (Caio[C, V, L, *] ~> F) {
      def apply[A](fa: Caio[C, V, L, A]): F[A] = liftF(Caio.run(fa, context, ref))
    }
}

object CaioAsync {
  def apply[C, V, L: Monoid]: CaioAsync[C, V, L] =
    new CaioAsync[C, V, L]
}