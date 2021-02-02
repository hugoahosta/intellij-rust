/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import com.intellij.openapi.util.Segment
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.rust.ide.injected.isDoctestInjection
import org.rust.ide.inspections.RsProblemsHolder
import org.rust.ide.inspections.fixes.SubstituteTextFix
import org.rust.ide.utils.isCfgUnknown
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVisitor
import org.rust.lang.core.psi.ext.rangeWithPrevSpace
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.types.controlFlowGraph
import org.rust.stdext.mapToMutableList
import java.util.*

class RsUnreachableCodeInspection : RsLintInspection() {
    override fun getDisplayName(): String = "Unreachable code"

    override fun getLint(element: PsiElement): RsLint = RsLint.UnreachableCode

    override fun buildVisitor(holder: RsProblemsHolder, isOnTheFly: Boolean) = object : RsVisitor() {
        override fun visitFunction(func: RsFunction) {
            if (func.isDoctestInjection) return
            val controlFlowGraph = func.controlFlowGraph ?: return

            val elementsToReport = controlFlowGraph
                .unreachableElements
                .takeIf { it.isNotEmpty() }
                ?: return

            // Collect text ranges of unreachable elements and merge them in order to highlight
            // most enclosing ranges entirely, instead of highlighting each element separately
            val sortedRangesInFunction = elementsToReport
                .filter { it.isPhysical }
                .mapToMutableList { it.rangeWithPrevSpace.shiftLeft(func.startOffset) }
                .apply { sortWith(Segment.BY_START_OFFSET_THEN_END_OFFSET) }
                .takeIf { it.isNotEmpty() }
                ?: return

            val mergedRanges = mergeRanges(sortedRangesInFunction)
            for (rangeInFunction in mergedRanges) {
                registerUnreachableProblem(holder, func, rangeInFunction)
            }
        }
    }

    /** Merges intersecting (including adjacent) text ranges into one */
    private fun mergeRanges(sortedRanges: List<TextRange>): Collection<TextRange> {
        val mergedRanges = ArrayDeque<TextRange>()
        mergedRanges.add(sortedRanges[0])
        for (range in sortedRanges.drop(1)) {
            val leftNeighbour = mergedRanges.peek()
            if (leftNeighbour.intersects(range)) {
                mergedRanges.pop()
                mergedRanges.push(leftNeighbour.union(range))
            } else {
                mergedRanges.push(range)
            }
        }
        return mergedRanges
    }

    private fun registerUnreachableProblem(holder: RsProblemsHolder, func: RsFunction, rangeInFunction: TextRange) {
        val rangeInFile = rangeInFunction.shiftRight(func.startOffset)
        holder.registerLintProblem(
            func,
            "Unreachable code",
            rangeInFunction,
            SubstituteTextFix.delete("Remove unreachable code", func.containingFile, rangeInFile)
        )
    }
}
