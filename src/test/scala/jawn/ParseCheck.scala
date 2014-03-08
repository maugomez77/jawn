package jawn

import org.scalatest.matchers.ShouldMatchers
import org.scalatest._
import prop._
import org.scalacheck.Arbitrary._
import org.scalacheck._
import Gen._
import Arbitrary.arbitrary

import scala.collection.mutable

class ParseCheck extends PropSpec with Matchers with GeneratorDrivenPropertyChecks {

  // in theory we could test larger number values than longs, but meh?
  // we need to exclude nan, +inf, and -inf from our doubles
  // we want to be sure we test every possible unicode character

  val jnull   = Gen.oneOf(JNull :: Nil)
  val jfalse  = Gen.oneOf(JFalse :: Nil)
  val jtrue   = Gen.oneOf(JTrue :: Nil)
  val jlong   = arbitrary[Long].map(LongNum(_))
  val jdouble = Gen.choose(Double.MinValue, Double.MaxValue).map(DoubleNum(_))
  val jstring = arbitrary[String].map(JString(_))

  // totally unscientific atom frequencies
  val jatom: Gen[Atom] =
    Gen.frequency((1, 'n), (5, 'f), (5, 't), (8, 'l), (8, 'd), (16, 's)).flatMap {
      case 'n => jnull
      case 'f => jfalse
      case 't => jtrue
      case 'l => jlong
      case 'd => jdouble
      case 's => jstring
    }

  // use lvl to limit the depth of our jvalues
  // otherwise we will end up with SOE real fast

  def jarray(lvl: Int): Gen[JArray] =
    Gen.containerOf[Array, JValue](jvalue(lvl + 1)).map(JArray(_))

  def jitem(lvl: Int): Gen[(String, JValue)] =
    for { s <- arbitrary[String]; j <- jvalue(lvl) } yield (s, j)

  def jobject(lvl: Int): Gen[JObject] =
    Gen.containerOf[List, (String, JValue)](jitem(lvl + 1)).map(JObject.fromSeq)

  def jvalue(lvl: Int): Gen[JValue] =
    if (lvl < 10) {
      Gen.frequency((8, 'ato), (1, 'arr), (2, 'obj)).flatMap {
        case 'ato => jatom
        case 'arr => jarray(lvl)
        case 'obj => jobject(lvl)
      }
    } else {
      jatom
    }
  
  implicit lazy val arbJValue: Arbitrary[JValue] =
    Arbitrary(jvalue(0))

  // so it's only one property, but it exercises:
  //
  // * parsing from strings
  // * rendering jvalues to string
  // * jvalue equality
  //
  // not bad.
  property("idempotent parsing/rendering") {
    forAll { value1: JValue =>
      val json = value1.j
      val result = JParser.parseFromString(json)
      result shouldBe Right(value1)
      result.right.map(_.j) shouldBe Right(json)
    }
  }
}
