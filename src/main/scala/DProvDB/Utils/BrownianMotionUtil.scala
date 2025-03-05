package edu.DProvDB.Utils

import breeze.stats.distributions.{Gaussian, RandBasis, ThreadLocalRandomGenerator}
import breeze.optimize.LBFGS
import breeze.math.MutableInnerProductModule
import org.apache.commons.math3.random.MersenneTwister
import scala.math._
import breeze.linalg.DenseVector
import breeze.optimize.DiffFunction
import spire.syntax.interval
import breeze.util.LazyLogger

import org.apache.commons.math3.analysis.UnivariateFunction
import org.apache.commons.math3.optim.MaxEval
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType
import org.apache.commons.math3.optim.univariate.{BrentOptimizer, SearchInterval, UnivariateObjectiveFunction}
import org.apache.commons.math3.random.MersenneTwister

object BrownianMotionUtil {
    val maxTime: Double = 1000
    val steps: Int = 10000
    val increment = maxTime / steps

    def mixtureValue(t: Double, sensitivity: Double, delta: Double, p: Double): Double = {
        return pow(sensitivity, 2) / (2 * t) + sensitivity / (t) * sqrt(2 * (t + p) * log(1 / delta * sqrt((t + p) / p)))
    }

    def mixtureDerivative(t: Double, sensitivity: Double, delta: Double, p: Double): Double = {
        return (sensitivity * (2 * p * log(sqrt((t + p) / p) / delta) - t)) / (pow(2,3d/2d) * t * p * sqrt((p + t) * log(sqrt((t + p) / p) / delta)))
    }

    def linearValue(t: Double, sensitivity: Double, delta: Double, a: Double): Double = {
        val b = log(1d / delta) / a / 2
        return sensitivity / t * (sensitivity / 2 + b) + sensitivity * a
    }

    def linearDerivative(t: Double, sensitivity: Double, delta: Double, a: Double): Double = {
        return sensitivity + (sensitivity * log(delta)) / (2 * t * pow(a, 2))
    }

    def findMinValue(step: Int, sensitivity: Double, delta: Double): Double = {
        val t = step * increment

        val goal = GoalType.MINIMIZE
        val interval = new SearchInterval(0,maxTime)
        val funcToMinimize: UnivariateFunction = (x: Double) => mixtureValue(t,sensitivity,delta,x)
        val optimizer = new BrentOptimizer(0.05, 0.01)
        val objective = new UnivariateObjectiveFunction(funcToMinimize)

        val result = optimizer.optimize(objective, goal, interval, MaxEval.unlimited)

        val root = result.getPoint

        return result.getValue()
    }
    def findMinValue_ibfgs(step: Int, sensitivity: Double, delta: Double): Double = {
        val t = step * increment
        val optimizer = new LBFGS[DenseVector[Double]](maxIter = 4)
        class diff(t: Double) extends DiffFunction[DenseVector[Double]] {
            def calculate(x: DenseVector[Double]): (Double, DenseVector[Double]) = {
                (mixtureValue(t,sensitivity,delta,x(0)),DenseVector(mixtureDerivative(t,sensitivity,delta,x(0))))
            }
        }
        // print("---",new diff(t).calculate(DenseVector(1)))
        val p = optimizer.minimize(new diff(t),DenseVector(1))(0)
        return mixtureValue(t,sensitivity,delta,p)
    }

    def findMinValue_old(step: Int, sensitivity: Double, delta: Double): Double = {
        var v = 10000d
        val t = step * increment
        for ( p <- 0d to 1000d by 0.001) {
            val next_v = mixtureValue(t,sensitivity,delta,p)
            if (next_v < v) {
                v = next_v
                //println(t,sensitvity,delta,p,v)
            }
        }
        var w = 10000d
        for ( p <- 0d to 1000d by 0.001) {
            val next_v = linearValue(t,sensitivity,delta,p)
            if (next_v < w) {
                w = next_v
                //println(t,sensitvity,delta,p,v)
            }
        }
        return v
    }

    def retrieveAccuracy(v: Double, sensitivity: Double, delta: Double): (Int, Double) = {
        val t = (v/increment).floor.toInt
        return (t, findMinValue(t,sensitivity,delta))
    }

    def retrievePrivacy(epsilon: Double, sensitivity: Double, delta: Double): (Int, Double) = {
        //print(epsilon,sensitvity,delta)
        var lo = 0
        var hi = steps
        var v = 0d
        while(lo < hi) {
            //print(lo,hi)
            val mid = lo + (hi - lo) / 2
            v = findMinValue(mid,sensitivity,delta)
            //println(v)
            if (v < epsilon) {
                hi = mid
            } else if (v > epsilon) {
                lo = mid + 1
            } else {
                return (mid,v)
            }
        }
        return (lo,v)
    }
}

class BrownianMotion {
    val B = Gaussian(0, BrownianMotionUtil.increment)(new RandBasis(new ThreadLocalRandomGenerator(new MersenneTwister())))
    .sample(BrownianMotionUtil.steps).toList.scanLeft(0d)(_+_)

    def get(i: Int): Double = {
        return B(i)
    }
}