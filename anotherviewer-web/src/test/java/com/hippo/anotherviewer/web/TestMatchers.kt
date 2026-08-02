package com.hippo.anotherviewer.web

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Mockito matchers wrapped in Kotlin functions with a `returnsNotNull()`
 * contract.
 *
 * Kotlin 2.2 inserts `checkNotNullExpressionValue` at call sites when a
 * generic or platform-typed expression is passed to a non-null parameter.
 * Mockito's Java matchers return null at runtime, so those checks NPE
 * ("any(...) must not be null") when mocking Kotlin interfaces.
 *
 * The contract proves non-null to the compiler (no check at call sites).
 * The body must return null without tripping a check, so the type parameter
 * stays unbounded (generic `T` may be nullable → `null as T` is legal) and
 * the matcher registration happens via the side-effecting call before it.
 */
@OptIn(ExperimentalContracts::class)
fun <T> any(): T {
    contract { returnsNotNull() }
    ArgumentMatchers.any<T>()
    return null as T
}

@OptIn(ExperimentalContracts::class)
fun <T> any(klass: Class<T>): T {
    contract { returnsNotNull() }
    ArgumentMatchers.any(klass)
    return null as T
}

/** [ArgumentMatchers.eq]: passes the (non-null) value through untouched. */
fun <T> eq(value: T): T = ArgumentMatchers.eq(value)

/** [ArgumentMatchers.argThat] with a non-null-returning contract (same rationale as above). */
@OptIn(ExperimentalContracts::class)
fun <T> argThatK(matcher: ArgumentMatcher<T>): T {
    contract { returnsNotNull() }
    ArgumentMatchers.argThat(matcher)
    return null as T
}

/** [ArgumentCaptor.capture] with a non-null-returning contract (same rationale as above). */
@OptIn(ExperimentalContracts::class)
fun <T> captureK(captor: ArgumentCaptor<T>): T {
    contract { returnsNotNull() }
    captor.capture()
    return null as T
}
