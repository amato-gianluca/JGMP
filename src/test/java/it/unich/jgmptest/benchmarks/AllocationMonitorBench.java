package it.unich.jgmptest.benchmarks;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import it.unich.jgmp.AllocationMonitor;
import it.unich.jgmp.MPZ;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Fork(value = 5, jvmArgs = { "-Xms2G", "-Xmx2G" })
@Warmup(iterations = 5)
@Measurement(iterations = 5)

public class AllocationMonitorBench {

    @Param({ "10", "100", "1000", "10000", "100000" })
    public int fact;

    @Param({ "1", "32", "128" })
    public int numCrossThreshold;

    @Param({ "16", "8", "4", "2", "1" })
    public int startAllocationThresholdDivisor;

    @Param({ "16", "8", "4", "2", "1" })
    public int maxAllocationThresholdDivisor;

    @Setup(Level.Trial)
    public void AllocationMonitorSetup() {
        if (maxAllocationThresholdDivisor > startAllocationThresholdDivisor)
            System.exit(0);
        AllocationMonitor.setDebugLevel(0);
        AllocationMonitor.setNumCrossThreshold(numCrossThreshold);
        AllocationMonitor.setAllocationThreshold(Runtime.getRuntime().maxMemory() / startAllocationThresholdDivisor);
        AllocationMonitor.setMaxAllocationThreshold(Runtime.getRuntime().maxMemory() / maxAllocationThresholdDivisor);
        AllocationMonitor.enable();
    }

    @Benchmark
    public MPZ factorialMPZfast() {
        return MPZ.facUi(fact);
    }

    @Benchmark
    public MPZ factorialMPZ() {
        var x = fact;
        var f = new MPZ(1);
        while (x >= 1) {
            f.mulAssign(f, x);
            x -= 1;
        }
        return f;
    }

    @Benchmark
    public MPZ factorialMPZImmutable() {
        var x = fact;
        var f = new MPZ(1);
        while (x >= 1) {
            f = f.mul(x);
            x -= 1;
        }
        return f;
    }

}