package com.r3.demos.ubin2a.plan

import com.r3.demos.ubin2a.plan.solver.ComponentCycleSolver
import com.r3.demos.ubin2a.plan.solver.ComponentCycleSolverImpl
import com.r3.demos.ubin2a.plan.solver.LargestSumCycleOnlySolverImpl

object NettingMode {
    val LargestSum: ComponentCycleSolver = LargestSumCycleOnlySolverImpl()
    val BestEffort: ComponentCycleSolver = ComponentCycleSolverImpl()
}
