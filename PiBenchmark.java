import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * PiBenchmark
 * * A simple, cross-platform Java CPU benchmark tool that calculates Pi
 * to a specified number of digits using multiple threads.
 * * This tool is designed to be run from the command line to test CPU performance.
 * * How to compile:
 * javac PiBenchmark.java
 * * How to run:
 * java PiBenchmark [digits] [repsPerThread] [threads]
 * * Arguments:
 * [digits]:        The number of decimal places to calculate Pi (e.g., 5000)
 * [repsPerThread]: The number of repetitions EACH thread will run (e.g., 1000)
 * [threads]:       The number of concurrent threads to use (e.g., 8)
 * * Example:
 * java PiBenchmark 5000 1000 8
 */
public class PiBenchmark {

    // These constants are used for the Machin-like formula for Pi
    // Pi = 16 * arctan(1/5) - 4 * arctan(1/239)
    private static final BigDecimal FOUR = new BigDecimal("4");
    private static final BigDecimal SIXTEEN = new BigDecimal("16");
    private static final BigDecimal BD_5 = new BigDecimal("5");
    private static final BigDecimal BD_239 = new BigDecimal("239");

    public static void main(String[] args) {
        
        // --- 1. Argument Parsing ---
        if (args.length != 3) {
            System.err.println("Usage: java PiBenchmark [digits] [repsPerThread] [threads]");
            System.err.println("Example: java PiBenchmark 5000 1000 8");
            return;
        }

        int digits = 0;
        int repsPerThread = 0;
        int numThreads = 0;

        try {
            digits = Integer.parseInt(args[0]);
            repsPerThread = Integer.parseInt(args[1]);
            numThreads = Integer.parseInt(args[2]);

            if (digits <= 0 || repsPerThread <= 0 || numThreads <= 0) {
                throw new NumberFormatException("Inputs must be positive integers.");
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid arguments. All inputs must be positive integers.");
            System.err.println(e.getMessage());
            return;
        }

        // --- 2. Warm-up Phase ---
        // This is crucial to allow the JVM's JIT compiler to optimize the code
        // before we start the timed benchmark.
        // We run a smaller number of calculations to "warm up" the critical methods.
        int warmUpReps = (int)Math.max(1, repsPerThread * 0.1); // 10% of reps, or at least 1
        for (int i = 0; i < warmUpReps; i++) {
            calculatePi(digits);
        }

        // --- 3. Timed Benchmark ---
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        long startTime = System.nanoTime();

        // Create and submit a task for each thread
        // Each task will run the Pi calculation 'repsPerThread' times
        for (int i = 0; i < numThreads; i++) {
            final int taskDigits = digits; // Need final variable for lambda
            final int taskReps = repsPerThread;
            
            Runnable task = () -> {
                for (int j = 0; j < taskReps; j++) {
                    calculatePi(taskDigits);
                }
            };
            executor.submit(task);
        }

        // Wait for all threads to finish
        executor.shutdown();
        try {
            // Wait a long time, but this will return as soon as all tasks are done
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            System.err.println("Benchmark was interrupted.");
            Thread.currentThread().interrupt();
            return;
        }

        long endTime = System.nanoTime();

        // --- 4. Calculate and Report Results ---
        long totalTimeNano = endTime - startTime;
        double totalTimeMs = totalTimeNano / 1_000_000.0;
        double totalTimeSec = totalTimeNano / 1_000_000_000.0;

        long totalCalculations = (long)repsPerThread * numThreads;
        double calculationsPerSecond = totalCalculations / totalTimeSec;

        // Print the final, single-line output
        System.out.printf(
            "Test:Pi, Digits:%d, RepsPerThread:%d, Threads:%d, TotalTime(ms):%.2f, CalcPerSec:%.2f%n",
            digits,
            repsPerThread,
            numThreads,
            totalTimeMs,
            calculationsPerSecond
        );
    }

    /**
     * Calculates Pi to the specified number of digits using a Machin-like formula.
     * This method is the "work" for the benchmark.
     * * @param digits The number of decimal places to calculate.
     * @return A BigDecimal representing Pi.
     */
    public static BigDecimal calculatePi(int digits) {
        // We need extra precision for intermediate calculations
        int scale = digits + 10;
        
        // arctan(x) = x - x^3/3 + x^5/5 - x^7/7 + ...
        // We use the formula: Pi = 16 * arctan(1/5) - 4 * arctan(1/239)
        BigDecimal term1 = arctan(BD_5.pow(-1), scale);
        BigDecimal term2 = arctan(BD_239.pow(-1), scale);
        
        // 16 * term1
        BigDecimal pi = term1.multiply(SIXTEEN);
        // pi = pi - (4 * term2)
        pi = pi.subtract(term2.multiply(FOUR));
        
        // Set the final scale to the requested number of digits
        return pi.setScale(digits, RoundingMode.HALF_UP);
    }

    /**
     * Calculates arctan(x) using the Taylor series expansion.
     * * @param x     The value (as BigDecimal)
     * @param scale The precision required
     * @return arctan(x)
     */
    private static BigDecimal arctan(BigDecimal x, int scale) {
        BigDecimal result = x;
        BigDecimal xSquared = x.multiply(x);
        BigDecimal term = x;
        BigDecimal divisor = BigDecimal.ONE;
        
        // Loop until the term is too small to affect the result
        while (true) {
            divisor = divisor.add(BigDecimal.valueOf(2)); // 3, 5, 7, ...
            term = term.multiply(xSquared).negate(); // -x^3, +x^5, -x^7, ...
            
            // Add the new term: term / divisor
            BigDecimal termToAdd = term.divide(divisor, scale, RoundingMode.HALF_EVEN);
            
            // If the new term is 0, we've reached the limit of our precision
            if (termToAdd.signum() == 0) {
                break;
            }
            result = result.add(termToAdd);
        }
        return result;
    }
}
