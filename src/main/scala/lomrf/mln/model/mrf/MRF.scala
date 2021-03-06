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
 * Copyright (c) Anastasios Skarlatidis.
 *
 * This file is part of Logical Markov Random Fields (LoMRF).
 *
 * LoMRF is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * LoMRF is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with LoMRF. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package lomrf.mln.model.mrf

import gnu.trove.map.TIntObjectMap
import gnu.trove.map.hash.TIntObjectHashMap
import lomrf.mln.grounding.DependencyMap
import lomrf.mln.model._

/**
 * This class represents a ground Markov Random Field.
 *
 * @param mln the source Markov Logic.
 * @param constraints the indexed collection of ground clauses.
 * @param atoms a indexed collection of ground atoms.
 * @param pLit2Constraints a map that keeps track which clauses contain the same positive literal.
 * @param nLit2Constraints a map that keeps track which clauses contain the same negative literal.
 * @param queryAtomStartID the id (integer) of the first query atom.
 * @param queryAtomEndID  the id (integer) of the last query atom.
 * @param weightHard the estimated weight for all hard-constrained ground clauses.
 * @param maxNumberOfLiterals that is the length of the bigger ground clause in this MRF.
 *
 */
class MRF(val mln: MLN,
          val constraints: TIntObjectMap[Constraint],
          val atoms: TIntObjectMap[GroundAtom],
          val pLit2Constraints: TIntObjectMap[collection.Iterable[Constraint]],
          val nLit2Constraints: TIntObjectMap[collection.Iterable[Constraint]],
          val queryAtomStartID: Int,
          val queryAtomEndID: Int,
          val weightHard: Double,
          val maxNumberOfLiterals: Int,
          val dependencyMap: Option[DependencyMap] = None) {

  val numberOfConstraints = constraints.size()
  val numberOfAtoms = atoms.size()

  def apply(cid: Int) = constraints.get(cid)

  def fetchAtom(literal: Int) = atoms.get(math.abs(literal))

  /**
   * Update constraint weights from the sum of newly found parent weights in
   * order to reconstruct the ground network faster in order to run inference.
   *
   * Basic reconstruction steps:
   *
   * For each clause that produced the constraint do:
   *    If weight has been inverted then:
   *      multiply the clause weight learned so far and the number of times (frequency)
   *      this clause produced the corresponding constraint and subtract the result from
   *      the total weight of the constraint so far.
   *
   *    If weight has not been inverted then:
   *      Do the same but add the result to the total weight instead of subtract it.
   *
   * Note: In case the clause is hard then just assign the hard weight to the constraint.
   */
  private[mln] def updateConstraintWeights(weights: IndexedSeq[Double]) = {

    val dependencyMap = this.dependencyMap.getOrElse(sys.error("Dependency map does not exists."))

    val constraints = this.constraints.iterator()

    while(constraints.hasNext) {
      constraints.advance()
      val constraint = constraints.value()
      val iterator = dependencyMap.get(constraint.id).iterator()

      constraint.setWeight(0.0)
      while(iterator.hasNext) {
        iterator.advance()

        val clauseIdx = iterator.key()
        val frequency = iterator.value()

        // Frequency would never be negative because we always start using positive unit weights
        if(mln.clauses(clauseIdx).isHard) constraint.setWeight(weightHard)
        else constraint.setWeight(constraint.getWeight + weights(clauseIdx) * frequency)
      }
    }
  }

}

object MRF {

  val NO_ATOM_ID = 0
  val NO_CONSTRAINT_ID = -1
  val NO_ATOM = new GroundAtom(0, 0)
  val NO_CONSTRAINT: Constraint = new Constraint(Double.NaN, Array(0), true, 0)

  val MODE_MWS = 0
  val MODE_SAMPLE_SAT = 1

  /**
   *
   * @param mln the source Markov Logic.
   * @param constraints the indexed collection of ground clauses.
   * @param atoms a indexed collection of ground atoms.
   * @param weightHard the estimated weight for all hard-constrained ground clauses.
   * @param dependencyMap represents the relations between FOL clauses and their groundings. Specifically, the structure
   *                      stores for each ground clause the it of the FOL clause that becomes, as well as how many times
   *                      the this ground class is generated by the same FOL clause (freq). Please note that when the
   *                      'freq' number is negative, then we implicitly declare that the  weight of the corresponding FOL
   *                      clause has been inverted during the grounding process.
   *
   * @return a new MRF object
   */
  def apply(mln: MLN,
            constraints: TIntObjectMap[Constraint],
            atoms: TIntObjectMap[GroundAtom],
            weightHard: Double,
            dependencyMap: Option[DependencyMap] = None): MRF = {

    val queryAtomStartID = mln.space.queryStartID
    val queryAtomEndID = mln.space.queryEndID

    // create positive-and-negative literal to constraint occurrence maps
    val iterator = constraints.iterator()
    val pLit2Constraints = new TIntObjectHashMap[List[Constraint]]()
    val nLit2Constraints = new TIntObjectHashMap[List[Constraint]]()

    var maxNumberOfLiterals = 0

    while (iterator.hasNext) {
      iterator.advance()
      val constraint = iterator.value()

      if (constraint.literals.length > maxNumberOfLiterals) maxNumberOfLiterals = constraint.literals.length

      for (literal <- constraint.literals) {
        val atomID = math.abs(literal)
        if (literal > 0) {
          val constraints = pLit2Constraints.get(atomID)
          if (constraints eq null)
            pLit2Constraints.put(atomID, List(constraint))
          else
            pLit2Constraints.put(atomID, constraint :: constraints)
        }
        else {
          val constraints = nLit2Constraints.get(atomID)
          if (constraints eq null)
            nLit2Constraints.put(atomID, List(constraint))
          else
            nLit2Constraints.put(atomID, constraint :: constraints)
        }
      }
    }

    new MRF(mln, constraints, atoms,
      pLit2Constraints.asInstanceOf[TIntObjectMap[collection.Iterable[Constraint]]],
      nLit2Constraints.asInstanceOf[TIntObjectMap[collection.Iterable[Constraint]]],
      queryAtomStartID, queryAtomEndID, weightHard, maxNumberOfLiterals, dependencyMap)
  }

  def build(mln: MLN, noNegWeights: Boolean = false, eliminateNegatedUnit: Boolean = false, createDependencyMap: Boolean = false): MRF ={
    new lomrf.mln.grounding.MRFBuilder(mln, noNegWeights, eliminateNegatedUnit, createDependencyMap).buildNetwork
  }
}



