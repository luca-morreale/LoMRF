/*
 * o                        o     o   o         o
 * |             o          |     |\ /|         | /
 * |    o-o o--o    o-o  oo |     | O |  oo o-o OO   o-o o   o
 * |    | | |  | | |    | | |     |   | | | |   | \  | |  \ /
 * O---oo-o o--O |  o-o o-o-o     o   o o-o-o   o  o o-o   o
 *             |
 *          o--o
 * o--o              o               o--o       o    o
 * |   |             |               |    o     |    |
 * O-Oo   oo o-o   o-O o-o o-O-o     O-o    o-o |  o-O o-o
 * |  \  | | |  | |  | | | | | |     |    | |-' | |  |  \
 * o   o o-o-o  o  o-o o-o o o o     o    | o-o o  o-o o-o
 *
 * Logical Markov Random Fields.
 *
 * Copyright (C) 2012 Anastasios Skarlatidis.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package lomrf.logic

import org.scalatest.{Matchers, FunSpec}

/**
 * @author Anastasios Skarlatidis
 */
final class AtomSpecTest extends FunSpec with Matchers {

  private val predicateSchema = Map[AtomSignature, List[String]](
    AtomSignature("Happens", 2) -> List("event", "time")
  )

  private val functionsSchema = Map[AtomSignature, (String, List[String])](
    AtomSignature("walking", 1) ->("event", List("id"))
  )

  private val parser = new ExtendedKBParser(predicateSchema, functionsSchema)


  /**
   * Parse an atomic formula with two constants
   */
  describe("The sentence: 'HappensAt(Foo,10)'") {
    val strHappens = "Happens(Foo,10)"
    val atomHappens = AtomicFormula("Happens", List(Constant("Foo"), Constant("10")))
    val result = parser.parsePredicate(strHappens)

    it("is an atomic formula") {
      result shouldEqual atomHappens
    }

    it("prints as '" + strHappens + "'") {
      result.toText should be(strHappens)
      result.toText should be(atomHappens.toText)
    }

    it("is has signature: 'Happens/2'") {
      result.signature shouldEqual AtomSignature("Happens", 2)
    }

    it("has no variables") {
      result.variables.isEmpty should be(true)
    }

    it("has no functions") {
      result.functions.isEmpty should be(true)
    }

    it("is ground") {
      result.isGround should be(true)
    }

    it("has two constants: 'Foo' and '10')") {
      result.terms shouldEqual List(Constant("Foo"), Constant("10"))
    }
  }

  /**
   * Parse an atomic formula with one constant and one variable
   */
  describe("The sentence: 'HappensAt(Foo,t)'") {
    val strHappens = "Happens(Foo,t)"
    val atomHappens = AtomicFormula("Happens", List(Constant("Foo"), Variable("t", "time")))
    val result = parser.parsePredicate(strHappens)


    it("is an atomic formula") {
      result shouldEqual atomHappens
    }

    it("prints as '" + strHappens + "'") {
      result.toText should be(strHappens)
      result.toText should be(atomHappens.toText)
    }

    it("is has signature: 'Happens/2'") {
      result.signature shouldEqual AtomSignature("Happens", 2)
    }

    it("has one variable") {
      result.variables.size should be(1)
    }

    it("has no functions") {
      result.functions.isEmpty should be(true)
    }

    it("is not ground") {
      result.isGround should be(false)
    }

    it("composed of constant 'Foo' and variable 't'") {
      result.terms shouldEqual List(Constant("Foo"), Variable("t", "time"))
    }
  }

  /**
   * Parse an atomic formula with one function and one variable or constant
   * (term1: TermFunction, term2: variable or constant, no. of constants, no. of variables, string representation)
   */
  val functionsToTest = List(
    // Happens(walking(ID0),0) : 2 constants and 0 variables
    (TermFunction("walking", List(Constant("ID0")), "event"), Constant("0"), 2, 0, "Happens(walking(ID0),0)"),

    // Happens(walking(ID0),t) : 1 constant and 1 variable
    (TermFunction("walking", List(Constant("ID0")), "event"), Variable("t", "time"), 1, 1, "Happens(walking(ID0),t)"),

    // Happens(walking(x),0) : 1 constant and 1 variable
    (TermFunction("walking", List(Variable("x", "id")), "event"), Constant("0"), 1, 1, "Happens(walking(x),0)"),

    // Happens(walking(x),t) : 0 constants and 2 variables
    (TermFunction("walking", List(Variable("x", "id")), "event"), Variable("t", "time"), 0, 2, "Happens(walking(x),t)")
  )

  for ((term1, term2, nConst, nVar, strHappens) <- functionsToTest) describe("The sentence: '" + strHappens + "'") {
    val atomHappens = AtomicFormula("Happens", List(term1, term2))
    val result = parser.parsePredicate(strHappens)

    it("is an atomic formula") {
      result shouldEqual atomHappens
    }

    it("prints as '" + strHappens + "'") {
      result.toText should be(strHappens)
      result.toText should be(atomHappens.toText)
    }

    it("has '" + nConst + "' constant(s)") {
      result.constants.size should equal(nConst)
      result.constants should equal(atomHappens.constants)
    }

    it("has '" + nVar + "' variable(s)") {
      result.variables.size should equal(nVar)
      result.variables should equal(atomHappens.variables)
    }

    it("contains the function "+term1.toText){
      result.functions.size should equal(1)
      result.functions.head should equal(term1)
    }

    if (nVar > 0) it("is ground") {
      result.isGround should not be (true)
    }
    else it("is not ground") {
      result.isGround should be(true)
    }

    it("composed of '"+term1.toText+"' and '"+term2.toText+"'") {
      result.terms shouldEqual List(term1, term2)
    }

  }


}
