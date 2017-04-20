/*
 * Copyright 2017 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package freestyle

import cats.Monad

import scala.reflect.macros.blackbox.Context

/**
  * A tagless operation interpreted when a `H` Handler is provided
  */
trait Alg[H[_[_]], A] { self =>

  def apply[F[_]](implicit handler: H[F]): F[A]

}

trait MonadSupport[H[_[_]], A] { self =>

  import cats.implicits._

  def apply[M[_]: Monad](implicit handler: H[M]): M[A]

  def exec[M[_]: Monad](implicit handler: H[M]): M[A] = apply[M]

  def map[B](f: A => B): MonadSupport[H, B] = new MonadSupport[H, B] {
    def apply[F[_]: Monad](implicit handler: H[F]): F[B] = self[F].map(f)
  }

  def flatMap[B](f: A => MonadSupport[H, B]): MonadSupport[H, B] =
    new MonadSupport[H, B] {
      def apply[F[_]: Monad](implicit handler: H[F]): F[B] = self[F].flatMap(f(_)[F])
    }

}

trait TaglessEffectLike[F[_]] { self =>
  final type FS[A] = F[A]
}

object taglessImpl {

  def tagless(c: Context)(annottees: c.Expr[Any]*): c.universe.Tree = {
    import c.universe._
    import c.universe.internal.reificationSupport._

    /* Macro Hygiene: we first declare a long list of fresh names for term and types. */
    val OP = TypeName("Op") // Root trait of the Effect ADT
    val MM = freshTypeName("MM$") // MM Target of the Handler's natural Transformation
    val LL = freshTypeName("LL$") // LL is the target of the Lifter's Injection
    val AA = freshTypeName("AA$") // AA is the parameter inside type applications
    val inj = freshTermName("toInj")

    def fail(msg: String) = c.abort(c.enclosingPosition, msg)

    // Messages of error
    val invalid = "Invalid use of the `@tagless` annotation"
    val abstractOnly = "The `@tagless` annotation can only be applied to a trait or to an abstract class."
    val noCompanion = "The trait (or class) annotated with `@tagless` must have no companion object."
    val onlyReqs = "In a `@tagless`-annotated trait (or class), all abstract method declarations should be of type FS[_]"

    def gen(): Tree = annottees match {
      case Expr(cls: ClassDef) :: Nil =>
        if (cls.mods.hasFlag(Flag.TRAIT | Flag.ABSTRACT)) {
          val effectTrait = cls.duplicate
          q"""
            ${mkEffectTrait(effectTrait.duplicate)}
            ${mkEffectObject(effectTrait.duplicate)}
          """
        } else fail(s"$invalid in ${cls.name}. $abstractOnly")

      case Expr(cls: ClassDef) :: Expr(_) :: _ => fail(s"$invalid in ${cls.name}. $noCompanion")

      case _ => fail(s"$invalid. $abstractOnly")
    }


    def mkEffectTrait(effectTrait: ClassDef): ClassDef = {
      val FF = freshTypeName("FF$")
      val requests: List[Request] = collectRequests(effectTrait)
      val body = requests.map(_.traitDef(FF))
      // this is to make a TypeDef for `$FF[_]`
      val wildcard = TypeDef(Modifiers(Flag.PARAM), typeNames.WILDCARD, List(), TypeBoundsTree(EmptyTree, EmptyTree))
      val ffTParam = TypeDef(Modifiers(Flag.PARAM), FF, List(wildcard), TypeBoundsTree(EmptyTree, EmptyTree))
      val ClassDef(mods, name, tparams, Template(parents, self, _)) = effectTrait
      ClassDef(mods, name, ffTParam :: tparams, Template(parents, self, body))
    }

    class Request(reqDef: DefDef) {

      import reqDef.tparams

      val reqImpl = TermName(reqDef.name.toTermName.encodedName.toString)

      private[this] val Res = reqDef.tpt.asInstanceOf[AppliedTypeTree].args.last

      val params: List[ValDef] = reqDef.vparamss.flatten

      def traitDef(FF: TypeName): DefDef =
        if (params.isEmpty)
          q"def $reqImpl[..$tparams]: $FF[$Res]"
        else
          q"def $reqImpl[..$tparams](..$params): $FF[$Res]"

      def handlerDef: DefDef =
        if (params.isEmpty)
          q"def $reqImpl[..$tparams]: $LL[$Res]"
        else
          q"def $reqImpl[..$tparams](..$params): $LL[$Res]"

      def raiser(Eff: TypeName): DefDef = {
        val injected = {
          /*filter: if !v.mods.hasFlag(Flag.IMPLICIT)*/
          val args = params.map(_.name)
          val ev =  freshTermName("ev$")
          //q"FreeS.inject[$OP, $LL]( $ReqC[..${tparams.map(_.name)} ](..$args) )"
          q"""new _root_.freestyle.Alg[Handler, $Res] {
                def apply[$LL[_]](implicit $ev: Handler[$LL]): $LL[$Res] = $ev.${reqDef.name}(..${args})
             }
            """
        }

        val tpt = reqDef.tpt.asInstanceOf[AppliedTypeTree]
        q"def ${reqDef.name}[..$tparams](...${reqDef.vparamss}): _root_.freestyle.Alg[Handler, $Res] = $injected"
      }

    }

    def collectRequests(effectTrait: ClassDef): List[Request] = effectTrait.impl.collect {
      case dd@q"$mods def $name[..$tparams](...$paramss): $tyRes" => tyRes match {
        case tq"FS[..$args]" => new Request(dd.asInstanceOf[DefDef])
        case _ => fail(s"$invalid in definition of method $name in ${effectTrait.name}. $onlyReqs")
      }
    }

    def mkEffectObject(effectTrait: ClassDef): ModuleDef = {

      val requests: List[Request] = collectRequests(effectTrait)

      val Eff = effectTrait.name
      val TTs = effectTrait.tparams
      val tns = TTs.map(_.name)
      val ev = freshTermName("ev$")
      val ii = freshTermName("ii$")
      val fa = freshTermName("fa$")

      q"""
        object ${Eff.toTermName} {

          type $OP[$AA] = _root_.freestyle.Alg[Handler, $AA]

          trait Handler[$LL[_], ..$TTs] extends $Eff[$LL, ..$tns] with _root_.cats.arrow.FunctionK[$OP, $LL] {
            ..${requests.map(_.handlerDef)}
            override def apply[$AA]($fa: $OP[$AA]): $LL[$AA] = $fa(this)
          }

         object Ops {
           ..${requests.map(_.raiser(Eff))}
         }

          def apply[$LL[_], ..$TTs](implicit $ev: $Eff[$LL, ..$tns]): $Eff[$LL, ..$tns] = $ev

        }
      """
    }

    val x = gen()
    println(x)
    x
  }
}
