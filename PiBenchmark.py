#!/usr/bin/env python3

"""
PiBenchmark (Python Version)

A simple, cross-platform Python CPU benchmark tool that calculates Pi
to a specified number of digits using multiple processes.

This tool is designed to be run from the command line to test CPU performance.

How to run:
python3 pi_benchmark.py [digits] [repsPerThread] [threads]

Arguments:
[digits]:        The number of decimal places to calculate Pi (e.g., 5000)
[repsPerThread]: The number of repetitions EACH process will run (e.g., 1000)
[threads]:       The number of concurrent processes to use (e.g., 8)

Example:
python3 pi_benchmark.py 5000 1000 8

NOTE: This script uses 'multiprocessing' instead of 'threading' to bypass
the Python Global Interpreter Lock (GIL) and achieve true parallel
CPU computation. The '[threads]' argument is mapped to the number of processes.
"""

import sys
import time
from decimal import Decimal, getcontext
import multiprocessing

# --- Pi Calculation Logic (Machin-like formula) ---
# This section replicates the logic from the Java version using Python's
# 'decimal' module, which is equivalent to 'BigDecimal'.

def arctan(x_inv_str, scale):
    """
    Calculates arctan(1/x) using the Taylor series expansion.
    """
    getcontext().prec = scale
    x_inv = Decimal(x_inv_str)
    x = Decimal(1) / x_inv
    x_squared = x * x
    
    result = x
    term = x
    divisor = Decimal(1)
    
    while True:
        divisor += 2
        term *= x_squared * Decimal(-1)
        term_to_add = term / divisor
        
        # Check if the term is too small to affect the result at this precision
        # This is the Python equivalent of the Java 'signum() == 0' check
        # in a high-precision context.
        if abs(term_to_add) < Decimal('1e-%d' % scale):
            break
        result += term_to_add
        
    return result

def calculate_pi(digits):
    """
    Calculates Pi to the specified number of digits.
    This is the "work" function for the benchmark.
    
    Uses the formula: Pi = 16 * arctan(1/5) - 4 * arctan(1/239)
    """
    # We need extra precision for intermediate calculations
    scale = digits + 10
    
    # Set precision for the arctan calculations
    getcontext().prec = scale

    term1 = arctan('5', scale)
    term2 = arctan('239', scale)

    # pi = 16 * term1 - 4 * term2
    pi = (term1 * 16) - (term2 * 4)
    
    # Set the final precision
    getcontext().prec = digits
    # The `+pi` operation applies the final precision context
    return +pi

# --- Main Benchmark Execution ---

def main():
    """
    Main function to parse args, run warm-up, and execute the benchmark.
    """
    
    # --- 1. Argument Parsing ---
    if len(sys.argv) != 4:
        print("Usage: python3 pi_benchmark.py [digits] [repsPerThread] [threads]", file=sys.stderr)
        print("Example: python3 pi_benchmark.py 5000 1000 8", file=sys.stderr)
        sys.exit(1)

    try:
        digits = int(sys.argv[1])
        reps_per_thread = int(sys.argv[2])
        num_processes = int(sys.argv[3]) # This is the '[threads]' arg

        if digits <= 0 or reps_per_thread <= 0 or num_processes <= 0:
            raise ValueError("Inputs must be positive integers.")
            
    except ValueError as e:
        print(f"Invalid arguments. All inputs must be positive integers.", file=sys.stderr)
        print(f"{e}", file=sys.stderr)
        sys.exit(1)

    # --- 2. Warm-up Phase ---
    # This is still important in Python to ensure any initial setup,
    # imports, and CPU caches are "warm" before timing.
    warm_up_reps = max(1, int(reps_per_thread * 0.1)) # 10% of reps
    for _ in range(warm_up_reps):
        calculate_pi(digits)

    # --- 3. Timed Benchmark ---
    
    # Total calculations to be performed across all processes
    total_calculations = reps_per_thread * num_processes
    
    # Create a list of 'digits' arguments to map to the pool
    # The pool will distribute these tasks among the processes
    tasks = [digits] * total_calculations

    # Start the high-precision timer
    start_time = time.perf_counter()

    # Create a process pool and map the work
    # The 'with' statement automatically handles pool.close() and pool.join()
    with multiprocessing.Pool(processes=num_processes) as pool:
        # map() blocks until all tasks are complete
        pool.map(calculate_pi, tasks)

    end_time = time.perf_counter()

    # --- 4. Calculate and Report Results ---
    total_time_sec = end_time - start_time
    total_time_ms = total_time_sec * 1000.0
    
    calculations_per_second = total_calculations / total_time_sec

    # Print the final, single-line output in the exact same format
    print(
        f"Test:Pi, Digits:{digits}, RepsPerThread:{reps_per_thread}, Threads:{num_processes}, "
        f"TotalTime(ms):{total_time_ms:.2f}, CalcPerSec:{calculations_per_second:.2f}"
    )

if __name__ == "__main__":
    # This check is MANDATORY for 'multiprocessing' to work
    # correctly on all platforms (especially Windows).
    # It ensures that the 'main()' function only runs in the
    # parent process, not in the child processes.
    main()
  
