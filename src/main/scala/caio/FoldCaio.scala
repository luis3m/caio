package caio

import cats.effect.IO

sealed trait FoldCaio[C, V, L, +A] {

  /**
   * Required for transforming context outside of the evaluation GADT
   * Can transform Error and Failed cases as well
   * @param f
   * @tparam C2
   * @return
   */
  def contextMap[C2](f: C => C2): FoldCaio[C2, V, L, A]

  def flatMap[B](f: (C, Option[L], A) => FoldCaio[C, V, L, B]): FoldCaio[C, V, L, B]

  def toIO: IO[FoldCaioPure[C, V, L, A]]

  def map[B](f: A => B): FoldCaio[C, V, L, B]

  /**
   * Required for transforming EventLog, cant use FlatMap
   * Can transform Error and Failed cases as well
   * @param f
   * @return
   */
  def mapL[B](f: L => L): FoldCaio[C, V, L, A]
}

sealed trait FoldCaioPure[C, V, L, +A] extends FoldCaio[C, V, L, A] {

  def c: C

  def opt: Option[L]

  def contextMap[C2](f: C => C2): FoldCaioPure[C2, V, L, A]

  def map[B](f:A => B): FoldCaioPure[C, V, L, B]

  def flatMap[B](f: (C, Option[L], A) => FoldCaio[C, V, L, B]): FoldCaio[C, V, L, B]

  def toIO: IO[FoldCaioPure[C, V, L, A]] =
    IO.pure(this)

  def mapL[B](f: L => L): FoldCaioPure[C, V, L, A]
}

final private[caio] case class FoldCaioSuccess[C, V, L, +A](c: C, opt: Option[L], a: A) extends FoldCaioPure[C, V, L, A] {

  def contextMap[C2](f: C => C2): FoldCaioPure[C2, V, L, A] =
    this.copy(c = f(this.c))

  def map[B](f:A => B): FoldCaioPure[C, V, L, B] =
    this.copy(a = f(a))

  def flatMap[B](f: (C, Option[L], A) => FoldCaio[C, V, L, B]): FoldCaio[C, V, L, B] =
    f(c, opt, a)

  def mapL[B](f: L => L): FoldCaioPure[C, V, L, A] =
    this.copy(opt = opt.map(f))
}

final private[caio] case class FoldCaioFailure[C, V, L, +A](c: C, opt: Option[L], head: V, tail: List[V]) extends FoldCaioPure[C, V, L, Nothing] {

  def contextMap[C2](f: C => C2): FoldCaioPure[C2, V, L, Nothing] =
    this.copy(c = f(this.c))


  def map[B](f: Nothing => B): FoldCaioPure[C, V, L, B] =
    this

  def flatMap[B](f: (C, Option[L], Nothing) => FoldCaio[C, V, L, B]): FoldCaio[C, V, L, B] =
    this

  def mapL[B](f: L => L): FoldCaioPure[C, V, L, Nothing] =
    this.copy(opt = opt.map(f))
}

final private[caio] case class FoldCaioError[C, V, L, +A](c: C, opt: Option[L], e: Throwable) extends FoldCaioPure[C, V, L, Nothing] {

  def contextMap[C2](f: C => C2): FoldCaioPure[C2, V, L, Nothing] =
    this.copy(c = f(this.c))

  def map[B](f: Nothing => B): FoldCaioPure[C, V, L, B] =
    this

  def flatMap[B](f: (C, Option[L], Nothing) => FoldCaio[C, V, L, B]): FoldCaio[C, V, L, B] =
    this

  def mapL[B](f: L => L): FoldCaioPure[C, V, L, Nothing] =
    this.copy(opt = opt.map(f))
}

final private[caio] case class FoldCaioIO[C, V, L, +A](io: IO[FoldCaioPure[C, V, L, A]]) extends FoldCaio[C, V, L, A] {

  def contextMap[C2](f: C => C2): FoldCaioIO[C2, V, L, A] =
    FoldCaioIO[C2, V, L, A](io.map(_.contextMap(f)))

  def flatMap[B](f: (C, Option[L], A) => FoldCaio[C, V, L, B]): FoldCaio[C, V, L, B] =
    FoldCaioIO {
      io.flatMap(_.flatMap(f) match {
        case FoldCaioIO(io2) =>
          io2
        case p:FoldCaioPure[C, V, L, B] =>
          IO.pure(p)
      })
    }

  def map[B](f: A => B): FoldCaio[C, V, L, B] =
    FoldCaioIO(io.map(_.map(f)))

  def mapL[B](f: L => L): FoldCaio[C, V, L, A] =
    FoldCaioIO(io.map(_.mapL(f)))

  def toIO: IO[FoldCaioPure[C, V, L, A]] =
    io
}