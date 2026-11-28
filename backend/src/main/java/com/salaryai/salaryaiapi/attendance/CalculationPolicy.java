package com.salaryai.salaryaiapi.attendance;

/** Organization-level policy applied when daily attendance is calculated. */
public enum CalculationPolicy {
    /** One check-in/check-out pair per work date; late arrivals are reported. */
    STANDARD,
    /** Multiple check-in/check-out pairs are allowed by policy. */
    FLEXIBLE,
    /** Strict pairing: an unmatched punch is always treated as an error. */
    STRICT
}
