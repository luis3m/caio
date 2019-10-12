package caio.mtl

import cats.{Applicative, Functor, Monad, MonadError}
import cats.effect.{Async, Bracket, Concurrent, LiftIO, Sync}
import cats.mtl.{ApplicativeAsk, MonadState}

class ProviderConcurrentTests {

  class StateFunctor[M[_]:MonadState[*[_], Int]:Functor] {
    def run:M[Int] = MonadState[M,Int].get
  }

  class StateApplicative[M[_]:MonadState[*[_], Int]:Applicative] {
    def run:M[Int] = MonadState[M,Int].get
  }

  class StateMonad[M[_]:MonadState[*[_], Int]:Monad] {
    def run:M[Int] = MonadState[M,Int].get
  }

  class AskMonadError[M[_]:ApplicativeAsk[*[_], Int]:MonadError[*[_], Throwable]] {
    def run:M[Int] = ApplicativeAsk[M, Int].ask
  }

  class AskBracket[M[_]:ApplicativeAsk[*[_], Int]:Bracket[*[_], Throwable]] {
    def run:M[Int] = ApplicativeAsk[M, Int].ask
  }

  class StateSync[M[_]:MonadState[*[_], Int]:Sync] {
    def run:M[Int] = MonadState[M,Int].get
  }
  class StateAsync[M[_]:MonadState[*[_], Int]:Async] {
    def run:M[Int] = MonadState[M,Int].get
  }

  class StateConcurrent[M[_]:MonadState[*[_], Int]:Concurrent] {
    def run:M[Int] = MonadState[M,Int].get
  }

  class AskLiftIO[M[_]:ApplicativeAsk[*[_], Int]:LiftIO] {
    def run:M[Int] = ApplicativeAsk[M, Int].ask
  }

  class FunctorCheck[M[_]:Provider:Monad] {
    val functor = implicitly[Functor[M]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val stateFunctor = new StateFunctor[E.FE]
  }

  class FunctorCheck2[M[_]:Provider:Sync] {
    val functor = implicitly[Functor[M]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val stateFunctor = new StateFunctor[E.FE]
  }

  class FunctorCheck3[M[_]:Provider:Concurrent] {
    val functor = implicitly[Functor[M]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val stateFunctor = new StateFunctor[E.FE]
  }


  class MonadCheck[M[_]:Provider:Sync] {

    val functor = implicitly[Functor[M]]

    val applicative = implicitly[Applicative[M]]

    val monad = implicitly[Monad[M]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val stateApplicative = new StateApplicative[E.FE]

    val stateFunctor = new StateFunctor[E.FE]

    val stateMonad = new StateMonad[E.FE]
  }

  class MonadErrorCheck[M[_]:Provider:Sync] {

    val functor = implicitly[Functor[M]]

    val applicative = implicitly[Applicative[M]]

    val monad = implicitly[Monad[M]]

    val monadError = implicitly[MonadError[M, Throwable]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val stateApplicative = new StateApplicative[E.FE]

    val stateFunctor = new StateFunctor[E.FE]

    val stateMonad = new StateMonad[E.FE]

    val askMonadError = new AskMonadError[E.FE]
  }

  class BracketCheck[M[_]:Provider:Sync] {

    val functor = implicitly[Functor[M]]

    val applicative = implicitly[Applicative[M]]

    val monad = implicitly[Monad[M]]

    val monadError = implicitly[MonadError[M, Throwable]]

    val bracket = implicitly[Bracket[M, Throwable]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val stateApplicative = new StateApplicative[E.FE]

    val stateFunctor = new StateFunctor[E.FE]

    val stateMonad = new StateMonad[E.FE]

    val askMonadError = new AskMonadError[E.FE]

    val askBracket = new AskBracket[E.FE]
  }

  class SyncCheck[M[_]:Provider:Sync] {

    val functor = implicitly[Functor[M]]

    val applicative = implicitly[Applicative[M]]

    val monad = implicitly[Monad[M]]

    val monadError = implicitly[MonadError[M, Throwable]]

    val bracket = implicitly[Bracket[M, Throwable]]

    val sync = implicitly[Sync[M]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val stateApplicative = new StateApplicative[E.FE]

    val stateFunctor = new StateFunctor[E.FE]

    val stateMonad = new StateMonad[E.FE]

    val askMonadError = new AskMonadError[E.FE]

    val askBracket = new AskBracket[E.FE]

    val syncBracket = new StateSync[E.FE]
  }

  class LiftIOCheck[M[_]:Provider:LiftIO] {

    val liftIO = implicitly[LiftIO[M]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val askLiftIO = new AskLiftIO[E.FE]
  }

  class AsyncCheck[M[_]:Provider:Concurrent] {

    val functor = implicitly[Functor[M]]

    val applicative = implicitly[Applicative[M]]

    val monad = implicitly[Monad[M]]

    val monadError = implicitly[MonadError[M, Throwable]]

    val bracket = implicitly[Bracket[M, Throwable]]

    val sync = implicitly[Sync[M]]

    val async = implicitly[Async[M]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val stateApplicative = new StateApplicative[E.FE]

    val stateFunctor = new StateFunctor[E.FE]

    val stateMonad = new StateMonad[E.FE]

    val askMonadError = new AskMonadError[E.FE]

    val askBracket = new AskBracket[E.FE]

    val stateSync = new StateSync[E.FE]

    val stateAsync = new StateAsync[E.FE]

    val askLiftIO = new AskLiftIO[E.FE]
  }


  class ConcurrentCheck[M[_]:Provider:Concurrent] {

    val functor = implicitly[Functor[M]]

    val applicative = implicitly[Applicative[M]]

    val monad = implicitly[Monad[M]]

    val monadError = implicitly[MonadError[M, Throwable]]

    val bracket = implicitly[Bracket[M, Throwable]]

    val sync = implicitly[Sync[M]]

    val async = implicitly[Async[M]]

    val concurrent = implicitly[Concurrent[M]]

    import Contextual._
    implicit val E = Provider[M].apply[Int]

    val stateApplicative = new StateApplicative[E.FE]

    val stateFunctor = new StateFunctor[E.FE]

    val stateMonad = new StateMonad[E.FE]

    val askMonadError = new AskMonadError[E.FE]

    val askBracket = new AskBracket[E.FE]

    val stateSync = new StateSync[E.FE]

    val stateAsync = new StateAsync[E.FE]

    val askLiftIO = new AskLiftIO[E.FE]

    val stateConcurrent = new StateConcurrent[E.FE]
  }
//
//  class LiftIOCheck[M[_]:Provider:LiftIO] {
//
//    import Contextual._
//
//    implicit val E = implicitly[Provider[M]].apply[Int]
//
//    val stateLiftIO = new StateLiftIO[E.FE]
//
//  }
//
//  class MonadCheck[M[_]:Provider:Monad] {
//
//    import Contextual._
//
//    implicit val E = implicitly[Provider[M]].apply[Int]
//
//    val stateMonad = new StateMonad[E.FE]
//
//  }
}
