/*-*- mode:C; -*- */
/*
 * Check: a unit test framework for C
 * Copyright (C) 2001, 2002 Arien Malec
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

#ifndef CHECK_H
#define CHECK_H

#include <stddef.h>
#include <string.h>
#include <math.h>
#include <float.h>

#include <check_stdint.h>

/*
   Macros and functions starting with _ (underscore) are internal and
   may change without notice. You have been warned!.
*/


#ifdef __cplusplus
#define CK_CPPSTART extern "C" {
#define CK_CPPEND }
CK_CPPSTART
#endif

#if defined(__GNUC__) && defined(__GNUC_MINOR__)
#define GCC_VERSION_AT_LEAST(major, minor) \
((__GNUC__ > (major)) || \
 (__GNUC__ == (major) && __GNUC_MINOR__ >= (minor)))
#else
#define GCC_VERSION_AT_LEAST(major, minor) 0
#endif

#if GCC_VERSION_AT_LEAST(2,95)
#define CK_ATTRIBUTE_UNUSED __attribute__ ((unused))
#else
#define CK_ATTRIBUTE_UNUSED
#endif /* GCC 2.95 */

#if GCC_VERSION_AT_LEAST(2,5)
#define CK_ATTRIBUTE_NORETURN __attribute__ ((noreturn))
#else
#define CK_ATTRIBUTE_NORETURN
#endif /* GCC 2.5 */

#include <sys/types.h>

#if defined(_MSC_VER)
/* define pid_t for Windows, as it is needed later */
#define pid_t int
#endif /* _MSC_VER */

/*
 * Used to create the linker script for hiding lib-local symbols. Shall
 * be put directly in front of the exported symbol.
 */
#define CK_EXPORT

/*
 * Used for MSVC to create the export attribute
 * CK_DLL_EXP is defined during the compilation of the library
 * on the command line.
 */
#ifndef CK_DLL_EXP
#define CK_DLL_EXP
#endif

/* check version numbers */

#define CHECK_MAJOR_VERSION (0)
#define CHECK_MINOR_VERSION (12)
#define CHECK_MICRO_VERSION (0)

CK_DLL_EXP extern int CK_EXPORT check_major_version;
CK_DLL_EXP extern int CK_EXPORT check_minor_version;
CK_DLL_EXP extern int CK_EXPORT check_micro_version;

#ifndef NULL
#define NULL ((void*)0)
#endif

/**
 * Type for a test case
 *
 * A TCase represents a test case.  Create with tcase_create, free
 * with tcase_free.  For the moment, test cases can only be run
 * through a suite
*/
typedef struct TCase TCase;

/**
 * Type for a test function
 */
typedef void (*TFun) (int);

/**
 * Type for a setup/teardown function
 */
typedef void (*SFun) (void);

/**
 * Type for a test suite
 */
typedef struct Suite Suite;

/**
 * Creates a test suite with the given name.
 *
 * Create a suite, which will contain test cases. Once
 * created, use suite_add_tcase() to add test cases.
 * When finished, create a suite runner from the
 * suite using srunner_create()
 *
 * @param name name of the suite
 *
 * @return suite
 *
 * @since 0.6.0
 */
CK_DLL_EXP Suite *CK_EXPORT suite_create(const char *name);

/**
 * Determines whether a given test suite contains a case named after a
 * given string.
 *
 * @param s suite to check
 * @param tcname test case to look for
 *
 * @return 1 iff the given test case is within the given suite;
 *          0 otherwise
 *
 * @since 0.9.9
 */
CK_DLL_EXP int CK_EXPORT suite_tcase(Suite * s, const char *tcname);

/**
 * Add a test case to a suite.
 *
 * Note that if the TCase has already been added attempting
 * to add it again will be ignored.
 *
 * @param s suite to add test case to
 * @param tc test case to add to suite
 *
 * @since 0.6.0
 */
CK_DLL_EXP void CK_EXPORT suite_add_tcase(Suite * s, TCase * tc);

/**
 * Create a test case.
 *
 * Once created, tests can be added with the tcase_add_test()
 * function, and the test case assigned to a suite with the
 * suite_add_tcase() function.
 *
 * @param name name of the test case
 *
 * @return test case containing no tests
 *
 * @since 0.6.0
 * */
CK_DLL_EXP TCase *CK_EXPORT tcase_create(const char *name);

/**
 * Associate a test case with certain tags.
 * Replaces any existing tags with the new set.
 *
 * @param tc the test case
 *
 * @param tags string containing arbitrary tags separated by spaces.
 *        This will be copied. Passing NULL clears all tags.
 *
 * @since 0.11.0
 * */
CK_DLL_EXP void CK_EXPORT tcase_set_tags(TCase * tc,
					 const char *tags);
/**
 * Add a test function to a test case
 *
 * @param tc test case to add test to
 * @param tf test function to add to test case
 *
 * @since 0.6.0
 * */
#define tcase_add_test(tc,tf) tcase_add_test_raise_signal(tc,tf,0)

/**
 * Add a test function with signal handling to a test case
 *
 * The added test is expected to terminate by throwing the given signal
 *
 * @param tc test case to add test to
 * @param tf test function to add to test case
 * @param signal expected signal for test function to throw in order for
 *                the test to be considered passing
 *
 * @since 0.9.2
 * */
#define tcase_add_test_raise_signal(tc,tf,signal) \
   _tcase_add_test((tc),(tf),"" # tf "",(signal), 0, 0, 1)

/**
 * Add a test function with an expected exit value to a test case
 *
 * The added test is expected to terminate by exiting with the given value
 *
 * @param tc test case to add test to
 * @param tf test function to add to test case
 * @param expected_exit_value exit value for test function to return in
 *                             order for the test to be considered passing
 *
 * @since 0.9.7
 */
#define tcase_add_exit_test(tc, tf, expected_exit_value) \
  _tcase_add_test((tc),(tf),"" # tf "",0,(expected_exit_value),0,1)

/**
 * Add a looping test function to a test case
 *
 * The test will be called in a for(i = s; i < e; i++) loop with each
 * iteration being executed in a new context. The loop variable 'i' is
 * available in the test.
 *
 * @param tc test case to add test to
 * @param tf function to add to test case
 * @param s starting index for value "i" in test
 * @param e ending index for value "i" in test
 *
 * @since 0.9.4
 */
#define tcase_add_loop_test(tc,tf,s,e) \
  _tcase_add_test((tc),(tf),"" # tf "",0,0,(s),(e))

/**
 * Add a looping test function with signal handling to a test case
 *
 * The test will be called in a for(i = s; i < e; i++) loop with each
 * iteration being executed in a new context. The loop variable 'i' is
 * available in the test.
 *
 * The added test is expected to terminate by throwing the given signal
 *
 * @param tc test case to add test to
 * @param tf function to add to test case
 * @param signal expected signal for test function to throw in order for
 *                the test to be considered passing
 * @param s starting index for value "i" in test
 * @param e ending index for value "i" in test
 *
 * @since 0.9.5
 */
#define tcase_add_loop_test_raise_signal(tc,tf,signal,s,e) \
  _tcase_add_test((tc),(tf),"" # tf "",(signal),0,(s),(e))

/**
 * Add a looping test function with an expected exit value to a test case
 *
 * The test will be called in a for(i = s; i < e; i++) loop with each
 * iteration being executed in a new context. The loop variable 'i' is
 * available in the test.
 *
 * The added test is expected to terminate by exiting with the given value
 *
 * @param tc test case to add test to
 * @param tf function to add to test case
 * @param expected_exit_value exit value for test function to return in
 *                             order for the test to be considered passing
 * @param s starting index for value "i" in test
 * @param e ending index for value "i" in test
 *
 * @since 0.9.7
 */
#define tcase_add_loop_exit_test(tc,tf,expected_exit_value,s,e) \
  _tcase_add_test((tc),(tf),"" # tf "",0,(expected_exit_value),(s),(e))

/* Add a test function to a test case
  (function version -- use this when the macro won't work
*/
CK_DLL_EXP void CK_EXPORT _tcase_add_test(TCase * tc, TFun tf,
                                          const char *fname, int _signal,
                                          int allowed_exit_value, int start,
                                          int end);

/**
 * Add unchecked fixture setup/teardown functions to a test case
 *
 * Unchecked fixture functions are run at the start and end of the
 * test case, and not before and after unit tests. Further,
 * unchecked fixture functions are not run in a separate address space,
 * like test functions, and so must not exit or signal (e.g.,
 * segfault).
 *
 * Also, when run in CK_NOFORK mode, unchecked fixture functions may
 * lead to different unit test behavior if unit tests change data
 * setup by the fixture functions.
 *
 * Note that if a setup function fails, the remaining setup functions
 * will be omitted, as will the test case and the teardown functions.
 * If a teardown function fails the remaining teardown functins will be
 * omitted.
 *
 * @param tc test case to add unchecked fixture setup/teardown to
 * @param setup function to add to be executed before the test case;
 *               if NULL no setup function is added
 * @param teardown function to add to be executed after the test case;
 *               if NULL no teardown function is added
 * @since 0.8.0
 */
CK_DLL_EXP void CK_EXPORT tcase_add_unchecked_fixture(TCase * tc, SFun setup,
                                                      SFun teardown);

/**
 * Add checked fixture setup/teardown functions to a test case
 *
 * Checked fixture functions are run before and after each unit test inside
 * of the address space of the test. Thus, if using CK_FORK
 * mode the separate process running the unit test will survive signals
 * or unexpected exits in the fixture function. Also, if the setup
 * function is idempotent, unit test behavior will be the same in
 * CK_FORK and CK_NOFORK modes.
 *
 * However, since fixture functions are run before and after each unit
 * test, they should not be expensive code.
 *
 * Note that if a setup function fails, the remaining setup functions
 * will be omitted, as will the test and the teardown functions. If a
 * teardown function fails the remaining teardown functins will be
 * omitted.
 *
 * @param tc test case to add checked fixture setup/teardown to
 * @param setup function to add to be executed before each unit test in
 *               the test case;  if NULL no setup function is added
 * @param teardown function to add to be executed after each unit test in
 *               the test case; if NULL no teardown function is added
 *
 * @since 0.8.0
*/
CK_DLL_EXP void CK_EXPORT tcase_add_checked_fixture(TCase * tc, SFun setup,
                                                    SFun teardown);

/**
 * Set the timeout for all tests in a test case.
 *
 * A test that lasts longer than the timeout (in seconds) will be killed
 * and thus fail with an error.
 *
 * If not set, the default timeout is one assigned at compile time. If
 * the environment variable CK_DEFAULT_TIMEOUT is defined and no timeout
 * is set, the value in the environment variable is used.
 *
 * If Check is compile without fork() support this call is ignored,
 * as timeouts are not possible.
 *
 * @param tc test case to assign timeout to
 * @param timeout to use, in seconds. If the value contains a decimal
 *                 portion, but no high resolution timer is available,
 *                 the value is rounded up to the nearest second.
 *
 * @since 0.9.2
 */
CK_DLL_EXP void CK_EXPORT tcase_set_timeout(TCase * tc, double timeout);

/* Internal function to mark the start of a test function */
CK_DLL_EXP void CK_EXPORT tcase_fn_start(const char *fname, const char *file,
                                         int line);

/**
 * Retreive the name of the current running test. This is the name
 * of the test passed to START_TEST. This is only valid when called
 * from a running test. The value return outside of a running test is
 * undefined.
 *
 * @since 0.11.0
 */
CK_DLL_EXP const char* CK_EXPORT tcase_name(void);

/**
 * Start a unit test with START_TEST(unit_name), end with END_TEST.
 *
 * One must use braces within a START_/END_ pair to declare new variables
 *
 * @since 0.6.0
 */
#define START_TEST(__testname)\
static void __testname (int _i CK_ATTRIBUTE_UNUSED)\
{\
  tcase_fn_start (""# __testname, __FILE__, __LINE__);

/**
 *  End a unit test
 *
 * @since 0.6.0
 */
#define END_TEST }

/*
 * Fail the test case unless expr is false
 *
 * This call is deprecated.
 */
#define fail_unless ck_assert_msg

/*
 * Fail the test case if expr is false
 *
 * This call is deprecated.
 *
 * NOTE: The space before the comma sign before ## is essential to be compatible
 * with gcc 2.95.3 and earlier.
 * FIXME: these macros may conflict with C89 if expr is
 * FIXME:   strcmp (str1, str2) due to excessive string length.
 */
#define fail_if(expr, ...)\
  (expr) ? \
     _ck_assert_failed(__FILE__, __LINE__, "Failure '"#expr"' occurred" , ## __VA_ARGS__, NULL) \
     : _mark_point(__FILE__, __LINE__)

/*
 * Fail the test
 *
 * This call is deprecated.
 */
#define fail ck_abort_msg

/*
 * This is called whenever an assertion fails.
 * Note that it only has the noreturn modifier when
 * using fork. If fork is unavailable, the function
 * calls longjmp() when a test assertion fails. Marking
 * the function as noreturn causes gcc to make assumptions
 * which are not valid, as longjmp() is like a return.
 */
#if 1
CK_DLL_EXP void CK_EXPORT _ck_assert_failed(const char *file, int line,
                                            const char *expr,
                                            ...) CK_ATTRIBUTE_NORETURN;
#else
CK_DLL_EXP void CK_EXPORT _ck_assert_failed(const char *file, int line,
                                            const char *expr, ...);
#endif

/**
 * Fail the test if expression is false
 *
 * @param expr expression to evaluate
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.6
 */
#define ck_assert(expr) ck_assert_msg(expr, NULL)

/* The space before the comma sign before ## is essential to be compatible
   with gcc 2.95.3 and earlier.
*/
/**
 * Fail the test if the expression is false; print message on failure
 *
 * @param expr expression to evaluate
 * @param ... message to print (in printf format) if expression is false
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.6
 */
#define ck_assert_msg(expr, ...) \
  (expr) ? \
     _mark_point(__FILE__, __LINE__) : \
     _ck_assert_failed(__FILE__, __LINE__, "Assertion '"#expr"' failed" , ## __VA_ARGS__, NULL)

/**
 * Unconditionally fail the test
 *
 * @note Once called, the remaining of the test is aborted
 *
 * @since 0.9.6
 */
#define ck_abort() ck_abort_msg(NULL)
/**
 * Unconditionally fail the test; print a message
 *
 * @param ... message to print (in printf format)
 *
 * @note Once called, the remaining of the test is aborted
 *
 * @since 0.9.6
 */
#define ck_abort_msg(...) _ck_assert_failed(__FILE__, __LINE__, "Failed" , ## __VA_ARGS__, NULL)

/* Signed and unsigned integer comparison macros with improved output compared to ck_assert(). */
/* OP may be any comparison operator. */
#define _ck_assert_int(X, OP, Y) do { \
  intmax_t _ck_x = (X); \
  intmax_t _ck_y = (Y); \
  ck_assert_msg(_ck_x OP _ck_y, "Assertion '%s' failed: %s == %jd, %s == %jd", #X" "#OP" "#Y, #X, _ck_x, #Y, _ck_y); \
} while (0)

/**
 * Check two signed integers to determine if X==Y
 *
 * If not X==Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.6
 */
#define ck_assert_int_eq(X, Y) _ck_assert_int(X, ==, Y)
/**
 * Check two signed integers to determine if X!=Y
 *
 * If not X!=Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.6
 */
#define ck_assert_int_ne(X, Y) _ck_assert_int(X, !=, Y)
/**
 * Check two signed integers to determine if X<Y
 *
 * If not X<Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_int_lt(X, Y) _ck_assert_int(X, <, Y)
/**
 * Check two signed integers to determine if X<=Y
 *
 * If not X<=Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_int_le(X, Y) _ck_assert_int(X, <=, Y)
/**
 * Check two signed integers to determine if X>Y
 *
 * If not X>Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_int_gt(X, Y) _ck_assert_int(X, >, Y)
/**
 * Check two signed integers to determine if X>=Y
 *
 * If not X>=Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_int_ge(X, Y) _ck_assert_int(X, >=, Y)

#define _ck_assert_uint(X, OP, Y) do { \
  uintmax_t _ck_x = (X); \
  uintmax_t _ck_y = (Y); \
  ck_assert_msg(_ck_x OP _ck_y, "Assertion '%s' failed: %s == %ju, %s == %ju", #X" "#OP" "#Y, #X, _ck_x, #Y, _ck_y); \
} while (0)
/**
 * Check two unsigned integers to determine if X==Y
 *
 * If not X==Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_uint_eq(X, Y) _ck_assert_uint(X, ==, Y)
/**
 * Check two unsigned integers to determine if X!=Y
 *
 * If not X!=Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_uint_ne(X, Y) _ck_assert_uint(X, !=, Y)
/**
 * Check two unsigned integers to determine if X<Y
 *
 * If not X<Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_uint_lt(X, Y) _ck_assert_uint(X, <, Y)
/**
 * Check two unsigned integers to determine if X<=Y
 *
 * If not X<=Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_uint_le(X, Y) _ck_assert_uint(X, <=, Y)
/**
 * Check two unsigned integers to determine if X>Y
 *
 * If not X>Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_uint_gt(X, Y) _ck_assert_uint(X, >, Y)
/**
 * Check two unsigned integers to determine if X>=Y
 *
 * If not X>=Y, the test fails.
 *
 * @param X signed integer
 * @param Y signed integer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_uint_ge(X, Y) _ck_assert_uint(X, >=, Y)

/* Number of digits after the decimal point to output via printf */
#ifndef CK_FLOATING_DIG
# define CK_FLOATING_DIG 6
#endif /* CK_FLOATING_DIG */

/* Floating point number comparison macros with improved output
 * compared to ck_assert(). */
/* OP may be any comparison operator, TP is type, TM is type modifier. */
#define _ck_assert_floating(X, OP, Y, TP, TM) do { \
  TP _ck_x = (X); \
  TP _ck_y = (Y); \
  ck_assert_msg(_ck_x OP _ck_y, \
  "Assertion '%s' failed: %s == %.*" TM "g, %s == %.*" TM "g", \
  #X" "#OP" "#Y, \
  #X, (int)CK_FLOATING_DIG, _ck_x, \
  #Y, (int)CK_FLOATING_DIG, _ck_y); \
} while (0)

/* Check floating point number is finise. */
/* TP is type, TM is type modifier. */
#define _ck_assert_floating_finite(X, TP, TM) \
do { \
  TP _ck_x = (X); \
  ck_assert_msg(isfinite(_ck_x), \
    "Assertion '%s' failed: %s == %.*" TM "g", \
    #X" is finite", \
    #X, (int)CK_FLOATING_DIG, _ck_x); \
} while (0)

/* Check floating point number is infinise. */
/* TP is type, TM is type modifier. */
#define _ck_assert_floating_infinite(X, TP, TM) \
do { \
  TP _ck_x = (X); \
  ck_assert_msg(isinf(_ck_x), \
    "Assertion '%s' failed: %s == %.*" TM "g", \
    #X" is infinite", \
    #X, (int)CK_FLOATING_DIG, _ck_x); \
} while (0)

/* Check floating point number is "Not a Number". */
/* TP is type, TM is type modifier. */
#define _ck_assert_floating_nan(X, TP, TM) \
do { \
  TP _ck_x = (X); \
  ck_assert_msg(isnan(_ck_x), \
    "Assertion '%s' failed: %s == %.*" TM "g", \
    #X" is NaN", \
    #X, (int)CK_FLOATING_DIG, _ck_x); \
} while (0)

/* Check floating point number is not "Not a Number". */
/* TP is type, TM is type modifier. */
#define _ck_assert_floating_nonnan(X, TP, TM) \
do { \
  TP _ck_x = (X); \
  ck_assert_msg(!isnan(_ck_x), \
    "Assertion '%s' failed: %s == %.*" TM "g", \
    #X" is not NaN", \
    #X, (int)CK_FLOATING_DIG, _ck_x); \
} while (0)

/* Floating point tolerance comparison macros with improved output
 * compared to ck_assert(). */
/* OP, D can have values: >, -1; <, 1. */
#define _ck_assert_floating_op_tol(X, OP, Y, T, D, TP, TM) do { \
  TP _ck_x = (X); \
  TP _ck_y = (Y); \
  TP _ck_t = (T); \
  ck_assert_msg((_ck_x - _ck_y) OP _ck_t * (D), \
  "Assertion '%s' failed: %s == %.*" TM "g, %s == %.*" TM "g, %s == %.*" TM "g", \
  #X" "#OP"= "#Y", error < "#T, \
  #X, (int)CK_FLOATING_DIG, _ck_x, \
  #Y, (int)CK_FLOATING_DIG, _ck_y, \
  #T, (int)CK_FLOATING_DIG, _ck_t); \
} while (0)

/* Floating point tolerance comparison macros with improved output
 * compared to ck_assert(). */
/* OP can have values: <; >=. */
#define _ck_assert_floating_absdiff_op_tol(X, Y, OP, T, TP, TM) \
do { \
  TP _ck_x = (X); \
  TP _ck_y = (Y); \
  TP _ck_t = (T); \
  ck_assert_msg(fabsl(_ck_y - _ck_x) OP _ck_t, \
    "Assertion '%s' failed: %s == %.*" TM "g, %s == %.*" TM "g, %s == %.*" TM "g", \
    "fabsl("#Y" - "#X") "#OP" "#T, \
    #X, (int)CK_FLOATING_DIG, _ck_x, \
    #Y, (int)CK_FLOATING_DIG, _ck_y, \
    #T, (int)CK_FLOATING_DIG, _ck_t); \
} while (0)

/**
 * Check two single precision floating point numbers to determine if X == Y
 *
 * If not X == Y, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_eq(X, Y) _ck_assert_floating(X, ==, Y, float, "")
/**
 * Check two single precision floating point numbers to determine if X != Y
 *
 * If not X != Y, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_ne(X, Y) _ck_assert_floating(X, !=, Y, float, "")
/**
 * Check two single precision floating point numbers to determine if X < Y
 *
 * If not X < Y, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_lt(X, Y) _ck_assert_floating(X, <, Y, float, "")
/**
 * Check two single precision floating point numbers to determine if X <= Y
 *
 * If not X <= Y, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_le(X, Y) _ck_assert_floating(X, <=, Y, float, "")
/**
 * Check two single precision floating point numbers to determine if X > Y
 *
 * If not X > Y, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_gt(X, Y) _ck_assert_floating(X, >, Y, float, "")
/**
 * Check two single precision floating point numbers to determine if X >= Y
 *
 * If not X >= Y, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_ge(X, Y) _ck_assert_floating(X, >=, Y, float, "")

/**
 * Check two single precision floating point numbers to determine if X≈Y
 * with specified tolerance
 *
 * If not X ≈ Y with error < T, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 * @param T tolerance (float)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_eq_tol(X, Y, T)  _ck_assert_floating_absdiff_op_tol(X, Y, <, T, float, "")

/**
 * Check two single precision floating point numbers to determine if not X≈Y
 * with specified tolerance
 *
 * If X ≈ Y with error < T, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 * @param T tolerance (float)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_ne_tol(X, Y, T) _ck_assert_floating_absdiff_op_tol(X, Y, >=, T, float, "")

/**
 * Check two single precision floating point numbers to determine if X>≈Y
 * with specified tolerance
 *
 * If not X >≈ Y with error < T, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 * @param T tolerance (float)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_ge_tol(X, Y, T) _ck_assert_floating_op_tol(X, >, Y, T, -1, float, "")

/**
 * Check two single precision floating point numbers to determine if X<≈Y
 * with specified tolerance
 *
 * If not X <≈ Y with error < T, the test fails.
 *
 * @param X floating point number (float)
 * @param Y floating point number (float) to compare against X
 * @param T tolerance (float)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_le_tol(X, Y, T) _ck_assert_floating_op_tol(X, <, Y, T, 1, float, "")

/**
 * Check that a single precision floating point number is finite; i.e. is
 * not +infinity, -infinity, or "Not a Number" (NaN)
 *
 * If X is +INFINITY or X is -INFINITY, or X is NaN, the test fails.
 *
 * @param X floating point number (float) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_finite(X) _ck_assert_floating_finite(X, float, "")

/**
 * Check that a single precision floating point number is infinite,
 * either +infinity or -infinity
 *
 * If X is not +INFINITY and X is not -INFINITY, the test fails.
 *
 * @param X floating point number (float) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_infinite(X) _ck_assert_floating_infinite(X, float, "")

/**
 * Check that a single precision floating point number
 * is "Not a Number" (NaN)
 *  
 * If X is not NaN, the test fails.
 *
 * @param X floating point number (float) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_nan(X) _ck_assert_floating_nan(X, float, "")

/**
 * Check that a single precision floating point number is
 * not "Not a Number" (NaN)
 *
 * If X is NaN, the test fails.
 *
 * @param X floating point number (float) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_float_nonnan(X) _ck_assert_floating_nonnan(X, float, "")

/**
 * Check two double precision floating point numbers to determine if X == Y
 *
 * If not X == Y, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_eq(X, Y) _ck_assert_floating(X, ==, Y, double, "")
/**
 * Check two double precision floating point numbers to determine if X != Y
 *
 * If not X != Y, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_ne(X, Y) _ck_assert_floating(X, !=, Y, double, "")
/**
 * Check two double precision floating point numbers to determine if X < Y
 *
 * If not X < Y, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_lt(X, Y) _ck_assert_floating(X, <, Y, double, "")
/**
 * Check two double precision floating point numbers to determine if X <= Y
 *
 * If not X <= Y, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_le(X, Y) _ck_assert_floating(X, <=, Y, double, "")
/**
 * Check two double precision floating point numbers to determine if X > Y
 *
 * If not X > Y, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_gt(X, Y) _ck_assert_floating(X, >, Y, double, "")
/**
 * Check two double precision floating point numbers to determine if X >= Y
 *
 * If not X >= Y, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_ge(X, Y) _ck_assert_floating(X, >=, Y, double, "")

/**
 * Check two double precision floating point numbers to determine if X≈Y
 * with specified tolerance
 *
 * If not X ≈ Y with error < T, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 * @param T tolerance (double)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_eq_tol(X, Y, T)  _ck_assert_floating_absdiff_op_tol(X, Y, <, T, double, "")

/**
 * Check two double precision floating point numbers to determine if not X≈Y
 * with specified tolerance
 *
 * If X ≈ Y with error < T, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 * @param T tolerance (double)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_ne_tol(X, Y, T) _ck_assert_floating_absdiff_op_tol(X, Y, >=, T, double, "")

/**
 * Check two double precision floating point numbers to determine if X>≈Y
 * with specified tolerance
 *
 * If not X >≈ Y with error < T, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 * @param T tolerance (double)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_ge_tol(X, Y, T) _ck_assert_floating_op_tol(X, >, Y, T, -1, double, "")

/**
 * Check two double precision floating point numbers to determine if X<≈Y
 * with specified tolerance
 *
 * If not X <≈ Y with error < T, the test fails.
 *
 * @param X floating point number (double)
 * @param Y floating point number (double) to compare against X
 * @param T tolerance (double)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_le_tol(X, Y, T) _ck_assert_floating_op_tol(X, <, Y, T, 1, double, "")

/**
 * Check that a double precision floating point number is finite; i.e. is
 * not +infinity, -infinity, or "Not a Number" (NaN)
 *
 * If X is +INFINITY or X is -INFINITY, or X is NaN, the test fails.
 *
 * @param X floating point number (double) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_finite(X) _ck_assert_floating_finite(X, double, "")

/**
 * Check that a double precision floating point number is infinite,
 * either +infinity or -infinity
 *
 * If X is not +INFINITY and X is not -INFINITY, the test fails.
 *
 * @param X floating point number (double) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_infinite(X) _ck_assert_floating_infinite(X, double, "")

/**
 * Check that a double precision floating point number
 * is "Not a Number" (NaN)
 *  
 * If X is not NaN, the test fails.
 *
 * @param X floating point number (double) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_nan(X) _ck_assert_floating_nan(X, double, "")

/**
 * Check that a double precision floating point number is
 * not "Not a Number" (NaN)
 *
 * If X is NaN, the test fails.
 *
 * @param X floating point number (double) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_double_nonnan(X) _ck_assert_floating_nonnan(X, double, "")

/** 
 * Check two double precision floating point numbers to determine if X == Y
 *
 * If not X == Y, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_eq(X, Y) _ck_assert_floating(X, ==, Y, long double, "L")
/**
 * Check two double precision floating point numbers to determine if X != Y
 *
 * If not X != Y, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_ne(X, Y) _ck_assert_floating(X, !=, Y, long double, "L")
/**
 * Check two double precision floating point numbers to determine if X < Y
 *
 * If not X < Y, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_lt(X, Y) _ck_assert_floating(X, <, Y, long double, "L")
/**
 * Check two double precision floating point numbers to determine if X <= Y
 *
 * If not X <= Y, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_le(X, Y) _ck_assert_floating(X, <=, Y, long double, "L")
/**
 * Check two double precision floating point numbers to determine if X > Y
 *
 * If not X > Y, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_gt(X, Y) _ck_assert_floating(X, >, Y, long double, "L")
/**
 * Check two double precision floating point numbers to determine if X >= Y
 *
 * If not X >= Y, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_ge(X, Y) _ck_assert_floating(X, >=, Y, long double, "L")

/**
 * Check two double precision floating point numbers to determine if X≈Y
 * with specified tolerance
 *
 * If not X ≈ Y with error < T, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 * @param T tolerance (long double)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_eq_tol(X, Y, T)  _ck_assert_floating_absdiff_op_tol(X, Y, <, T, long double, "L")

/**
 * Check two double precision floating point numbers to determine if not X≈Y
 * with specified tolerance
 *
 * If X ≈ Y with error < T, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 * @param T tolerance (long double)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_ne_tol(X, Y, T) _ck_assert_floating_absdiff_op_tol(X, Y, >=, T, long double, "L")

/**
 * Check two double precision floating point numbers to determine if X>≈Y
 * with specified tolerance
 *
 * If not X >≈ Y with error < T, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 * @param T tolerance (long double)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_ge_tol(X, Y, T) _ck_assert_floating_op_tol(X, >, Y, T, -1, long double, "L")

/**
 * Check two double precision floating point numbers to determine if X<≈Y
 * with specified tolerance
 *
 * If not X <≈ Y with error < T, the test fails.
 *
 * @param X floating point number (long double)
 * @param Y floating point number (long double) to compare against X
 * @param T tolerance (long double)
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_le_tol(X, Y, T) _ck_assert_floating_op_tol(X, <, Y, T, 1, long double, "L")

/**
 * Check that a double precision floating point number is finite; i.e. is
 * not +infinity, -infinity, or "Not a Number" (NaN)
 *
 * If X is +INFINITY or X is -INFINITY, or X is NaN, the test fails.
 *
 * @param X floating point number (long double) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_finite(X) _ck_assert_floating_finite(X, long double, "L")

/**
 * Check that a double precision floating point number is infinite,
 * either +infinity or -infinity
 *
 * If X is not +INFINITY and X is not -INFINITY, the test fails.
 *
 * @param X floating point number (long double) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_infinite(X) _ck_assert_floating_infinite(X, long double, "L")

/**
 * Check that a double precision floating point number
 * is "Not a Number" (NaN)
 *  
 * If X is not NaN, the test fails.
 *
 * @param X floating point number (long double) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_nan(X) _ck_assert_floating_nan(X, long double, "L")

/**
 * Check that a double precision floating point number is
 * not "Not a Number" (NaN)
 *
 * If X is NaN, the test fails.
 *
 * @param X floating point number (long double) to be checked
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ldouble_nonnan(X) _ck_assert_floating_nonnan(X, long double, "L")

/* String comparison macros with improved output compared to ck_assert() */
/* OP might be any operator that can be used in '0 OP strcmp(X,Y)' comparison. */
/* String pointer could be compared againts NULL with == (NULLEQ = 1) and != (NULLNE = 1) operators. */
/* The x and y parameter swap in strcmp() is needed to handle >, >=, <, <= operators. */
/* If the x or y parameter is NULL its value will be printed without quotes. */
#define _ck_assert_str(X, OP, Y, NULLEQ, NULLNE) do { \
  const char* _ck_x = (X); \
  const char* _ck_y = (Y); \
  const char* _ck_x_s; \
  const char* _ck_y_s; \
  const char* _ck_x_q; \
  const char* _ck_y_q; \
  if (_ck_x != NULL) { \
    _ck_x_q = "\""; \
    _ck_x_s = _ck_x; \
  } else { \
    _ck_x_q = ""; \
    _ck_x_s = "(null)"; \
  } \
  if (_ck_y != NULL) { \
    _ck_y_q = "\""; \
    _ck_y_s = _ck_y; \
  } else { \
    _ck_y_q = ""; \
    _ck_y_s = "(null)"; \
  } \
  ck_assert_msg( \
    (NULLEQ && (_ck_x == NULL) && (_ck_y == NULL)) || \
    (NULLNE && ((_ck_x == NULL) || (_ck_y == NULL)) && (_ck_x != _ck_y)) || \
    ((_ck_x != NULL) && (_ck_y != NULL) && (0 OP strcmp(_ck_y, _ck_x))), \
    "Assertion '%s' failed: %s == %s%s%s, %s == %s%s%s", \
    #X" "#OP" "#Y, \
    #X, _ck_x_q, _ck_x_s, _ck_x_q, \
    #Y, _ck_y_q, _ck_y_s, _ck_y_q); \
} while (0)

/**
 * Check two strings to determine if 0==strcmp(X,Y)
 *
 * If X or Y is NULL the test failes.
 * If (0==strcmp(X,Y)), the test fails.
 *
 * @param X string
 * @param Y string to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.6
 */
#define ck_assert_str_eq(X, Y) _ck_assert_str(X, ==, Y, 0, 0)

/**
 * Check two strings to determine if 0!=strcmp(X,Y)
 *
 * If X or Y is NULL the test fails.
 * If not 0!=strcmp(X,Y), the test fails.
 *
 * @param X string
 * @param Y string to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.6
 */
#define ck_assert_str_ne(X, Y) _ck_assert_str(X, !=, Y, 0, 0)

/**
 * Check two strings to determine if 0<strcmp(X,Y), (e.g. strcmp(X,Y)>0)
 *
 * If X or Y is NULL the test fails.
 * If not 0<strcmp(X,Y), the test fails.
 *
 * @param X string
 * @param Y string to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_str_lt(X, Y) _ck_assert_str(X, <, Y, 0, 0)

/**
 * Check two strings to determine if 0<=strcmp(X,Y) (e.g. strcmp(X,Y)>=0)
 *
 * If X or Y is NULL the test fails.
 * If not 0<=strcmp(X,Y), the test fails.
 *
 * @param X string
 * @param Y string to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_str_le(X, Y) _ck_assert_str(X, <=, Y, 0, 0)

/**
 * Check two strings to determine if 0<strcmp(X,Y) (e.g. strcmp(X,Y)>0)
 *
 * If X or Y is NULL the test fails.
 * If not 0<strcmp(X,Y), the test fails.
 *
 * @param X string
 * @param Y string to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_str_gt(X, Y) _ck_assert_str(X, >, Y, 0, 0)

/**
 * Check two strings to determine if 0>=strcmp(X,Y) (e.g. strcmp(X,Y)<=0)
 *
 * If X or Y is NULL the test fails.
 * If not 0>=strcmp(X,Y), the test fails.
 *
 * @param X string
 * @param Y string to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_str_ge(X, Y) _ck_assert_str(X, >=, Y, 0, 0)

/**
 * Check two strings to determine if 0==strcmp(X,Y) or if both are undefined 
 *
 * If both X and Y are NULL the test passes. However, if only one is NULL
 * the test fails.
 * If not ((X==NULL)&&(Y==NULL)) || (0==strcmp(X,Y)), the test fails.
 *
 * @param X string
 * @param Y string to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_pstr_eq(X, Y) _ck_assert_str(X, ==, Y, 1, 0)

/**
 * Check two strings to determine if 0!=strcmp(X,Y) or one of them is undefined
 *
 * If either X or Y is NULL the test passes, however if both are NULL
 * the test fails.
 * If not (X!=NULL)&&(Y!=NULL)&&(0!=strcmp(X,Y)), the test fails.
 *
 * @param X string
 * @param Y string to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_pstr_ne(X, Y) _ck_assert_str(X, !=, Y, 0, 1)

/* Memory location comparison macros with improved output compared to ck_assert() */
/* OP might be any operator that can be used in '0 OP memcmp(X,Y,L)' comparison */
/* The x and y parameter swap in memcmp() is needed to handle >, >=, <, <= operators */
/* Output is limited to CK_MAX_ASSERT_MEM_PRINT_SIZE bytes */
#ifndef CK_MAX_ASSERT_MEM_PRINT_SIZE
#define CK_MAX_ASSERT_MEM_PRINT_SIZE 64
#endif

/* Memory location comparison macros with improved output compared to ck_assert() */
/* OP might be any operator that can be used in '0 OP memcmp(X,Y,L)' comparison */
/* The x and y parameter swap in memcmp() is needed to handle >, >=, <, <= operators */
/* Output is limited to CK_MAX_ASSERT_MEM_PRINT_SIZE bytes */
#ifndef CK_MAX_ASSERT_MEM_PRINT_SIZE
#define CK_MAX_ASSERT_MEM_PRINT_SIZE 64
#endif

#define _ck_assert_mem(X, OP, Y, L) do { \
  const uint8_t* _ck_x = (const uint8_t*)(X); \
  const uint8_t* _ck_y = (const uint8_t*)(Y); \
  size_t _ck_l = (L); \
  char _ck_x_str[CK_MAX_ASSERT_MEM_PRINT_SIZE * 2 + 1]; \
  char _ck_y_str[CK_MAX_ASSERT_MEM_PRINT_SIZE * 2 + 1]; \
  static const char _ck_hexdigits[] = "0123456789abcdef"; \
  size_t _ck_i; \
  size_t _ck_maxl = (_ck_l > CK_MAX_ASSERT_MEM_PRINT_SIZE) ? CK_MAX_ASSERT_MEM_PRINT_SIZE : _ck_l; \
  for (_ck_i = 0; _ck_i < _ck_maxl; _ck_i++) { \
    _ck_x_str[_ck_i * 2  ]   = _ck_hexdigits[(_ck_x[_ck_i] >> 4) & 0xF]; \
    _ck_y_str[_ck_i * 2  ]   = _ck_hexdigits[(_ck_y[_ck_i] >> 4) & 0xF]; \
    _ck_x_str[_ck_i * 2 + 1] = _ck_hexdigits[_ck_x[_ck_i] & 0xF]; \
    _ck_y_str[_ck_i * 2 + 1] = _ck_hexdigits[_ck_y[_ck_i] & 0xF]; \
  } \
  _ck_x_str[_ck_i * 2] = 0; \
  _ck_y_str[_ck_i * 2] = 0; \
  if (_ck_maxl != _ck_l) { \
    _ck_x_str[_ck_i * 2 - 2] = '.'; \
    _ck_y_str[_ck_i * 2 - 2] = '.'; \
    _ck_x_str[_ck_i * 2 - 1] = '.'; \
    _ck_y_str[_ck_i * 2 - 1] = '.'; \
  } \
  ck_assert_msg(0 OP memcmp(_ck_y, _ck_x, _ck_l), \
    "Assertion '%s' failed: %s == \"%s\", %s == \"%s\"", #X" "#OP" "#Y, #X, _ck_x_str, #Y, _ck_y_str); \
} while (0)
/**
 * Check two memory locations to determine if 0==memcmp(X,Y,L)
 *
 * If not 0==memcmp(X,Y,L), the test fails.
 *
 * @param X memory location
 * @param Y memory location to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_mem_eq(X, Y, L) _ck_assert_mem(X, ==, Y, L)
/**
 * Check two memory locations to determine if 0!=memcmp(X,Y,L)
 *
 * If not 0!=memcmp(X,Y,L), the test fails.
 *
 * @param X memory location
 * @param Y memory location to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_mem_ne(X, Y, L) _ck_assert_mem(X, !=, Y, L)
/**
 * Check two memory locations to determine if 0<memcmp(X,Y,L), (e.g. memcmp(X,Y,L)>0)
 *
 * If not 0<memcmp(X,Y,L), the test fails.
 *
 * @param X memory location
 * @param Y memory location to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_mem_lt(X, Y, L) _ck_assert_mem(X, <, Y, L)
/**
 * Check two memory locations to determine if 0<=memcmp(X,Y,L) (e.g. memcmp(X,Y,L)>=0)
 *
 * If not 0<=memcmp(X,Y,L), the test fails.
 *
 * @param X memory location
 * @param Y memory location to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_mem_le(X, Y, L) _ck_assert_mem(X, <=, Y, L)
/**
 * Check two memory locations to determine if 0<memcmp(X,Y,L) (e.g. memcmp(X,Y,L)>0)
 *
 * If not 0<memcmp(X,Y,L), the test fails.
 *
 * @param X memory location
 * @param Y memory location to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_mem_gt(X, Y, L) _ck_assert_mem(X, >, Y, L)
/**
 * Check two memory locations to determine if 0>=memcmp(X,Y,L) (e.g. memcmp(X,Y,L)<=0)
 *
 * If not 0>=memcmp(X,Y,L), the test fails.
 *
 * @param X memory location
 * @param Y memory location to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_mem_ge(X, Y, L) _ck_assert_mem(X, >=, Y, L)

/* Pointer comparison macros with improved output compared to ck_assert(). */
/* OP may only be == or !=  */
#define _ck_assert_ptr(X, OP, Y) do { \
  const void* _ck_x = (X); \
  const void* _ck_y = (Y); \
  ck_assert_msg(_ck_x OP _ck_y, "Assertion '%s' failed: %s == %#x, %s == %#x", #X" "#OP" "#Y, #X, _ck_x, #Y, _ck_y); \
} while (0)

/* Pointer against NULL comparison macros with improved output
 * compared to ck_assert(). */
/* OP may only be == or !=  */
#define _ck_assert_ptr_null(X, OP) do { \
  const void* _ck_x = (X); \
  ck_assert_msg(_ck_x OP NULL, \
  "Assertion '%s' failed: %s == %#x", \
  #X" "#OP" NULL", \
  #X, _ck_x); \
} while (0)

/**
 * Check if two pointers are equal.
 *
 * If the two passed pointers are not equal, the test
 * fails.
 *
 * @param X pointer
 * @param Y pointer to compare against X
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.9.10
 */
#define ck_assert_ptr_eq(X, Y) _ck_assert_ptr(X, ==, Y)

/**
 * Check if two pointers are not.
 *
 * If the two passed pointers are equal, the test fails.
 *
 * @param X pointer
 * @param Y pointer to compare against X
 *
 * @since 0.9.10
 */
#define ck_assert_ptr_ne(X, Y) _ck_assert_ptr(X, !=, Y)

/**
 * Check if a pointer is equal to NULL.
 *
 * If X != NULL, the test fails.
 *
 * @param X pointer to compare against NULL
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ptr_null(X) _ck_assert_ptr_null(X, ==)

/**
 * Check if a pointer is not equal to NULL.
 *
 * If X == NULL, the test fails.
 *
 * @param X pointer to compare against NULL
 *
 * @note If the check fails, the remaining of the test is aborted
 *
 * @since 0.11.0
 */
#define ck_assert_ptr_nonnull(X) _ck_assert_ptr_null(X, !=)

/**
 * Mark the last point reached in a unit test.
 *
 * If the test throws a signal or exits, the location noted with the
 * failure is the last location of a ck_assert*() or ck_abort() call.
 * Use mark_point() to record intermediate locations (useful for tracking down
 * crashes or exits).
 *
 * @since 0.6.0
*/
#define mark_point() _mark_point(__FILE__,__LINE__)

/* Non macro version of #mark_point */
CK_DLL_EXP void CK_EXPORT _mark_point(const char *file, int line);

/**
 * Enum describing the possible results of a test
 */
enum test_result
{
    CK_TEST_RESULT_INVALID,     /**< Default value; should not encounter this */
    CK_PASS,                    /**< Test passed */
    CK_FAILURE,                 /**< Test completed but failed */
    CK_ERROR                    /**< Test failed to complete
                                   (unexpected signal or non-zero early exit) */
};

/**
 * Enum specifying the verbosity of output a SRunner should produce
 */
enum print_output
{
    CK_SILENT,                  /**< No output */
    CK_MINIMAL,                 /**< Only summary output */
    CK_NORMAL,                  /**< All failed tests */
    CK_VERBOSE,                 /**< All tests */
    CK_ENV,                     /**< Look at environment var CK_VERBOSITY
                                     for what verbosity to use, which can be
                                     either "silent", "minimal", "normal",
                                     or "verbose". If the environment variable
                                     is not set, then CK_NORMAL will be used.*/
#if 0
    CK_SUBUNIT,                 /**< Run as a subunit child process */
#endif
    CK_LAST                     /**< Not a valid option */
};

/**
 * Holds state for a running of a test suite
 */
typedef struct SRunner SRunner;

/**
 * Opaque type for a test failure
 */
typedef struct TestResult TestResult;

/**
 * Enum representing the types of contexts for a test
 */
enum ck_result_ctx
{
    CK_CTX_INVALID,             /**< Default value; should not encounter this */
    CK_CTX_SETUP,               /**< Setup before a test */
    CK_CTX_TEST,                /**< Body of test itself */
    CK_CTX_TEARDOWN             /**< Teardown after a test */
};

/**
 * Retrieve type of result that the given test result represents.
 *
 * This is a member of test_result, and can represent a
 * pass, failure, or error.
 *
 * @param tr test result to retrieve result from
 *
 * @return result of given test
 *
 * @since 0.6.0
 */
CK_DLL_EXP int CK_EXPORT tr_rtype(TestResult * tr);

/**
 * Retrieve context in which the result occurred for the given test result.
 *
 * The types of contents include the test setup, teardown, or the
 * body of the test itself.
 *
 * @param tr test result to retrieve context from
 *
 * @return context to which the given test result applies
 *
 * @since 0.8.0
 */
CK_DLL_EXP enum ck_result_ctx CK_EXPORT tr_ctx(TestResult * tr);

/**
 * Retrieve failure message from test result, if applicable.
 *
 * @return pointer to a message, if one exists. NULL otherwise.
 *
 * @since 0.6.0
 */
CK_DLL_EXP const char *CK_EXPORT tr_msg(TestResult * tr);

/**
 * Retrieve line number at which a failure occurred, if applicable.
 *
 * @return If the test resulted in a failure, returns the line number
 *          that the failure occurred on; otherwise returns -1.
 *
 * @since 0.6.0
 */
CK_DLL_EXP int CK_EXPORT tr_lno(TestResult * tr);

/**
 * Retrieve file name at which a failure occurred, if applicable.
 *
 * @return If the test resulted in a failure, returns a string
 *          containing the name of the file where the failure
 *          occurred; otherwise returns NULL.
 *
 * @since 0.6.0
 */
CK_DLL_EXP const char *CK_EXPORT tr_lfile(TestResult * tr);

/**
 * Retrieve test case name in which a failure occurred, if applicable.
 *
 * @return If the test resulted in a failure, returns a string
 *          containing the name of the test suite where the failure
 *          occurred; otherwise returns NULL.
 *
 * @since 0.6.0
 */
CK_DLL_EXP const char *CK_EXPORT tr_tcname(TestResult * tr);

/**
 * Creates a suite runner for the given suite.
 *
 * Once created, additional suites can be added to the
 * suite runner using srunner_add_suite(), and the suite runner can be
 * run with srunner_run_all(). Once finished, the suite runner
 * must be freed with srunner_free().
 *
 * @param s suite to generate a suite runner for
 *
 * @return suite runner for the given suite
 *
 * @since 0.6.0
 */
CK_DLL_EXP SRunner *CK_EXPORT srunner_create(Suite * s);

/**
 * Add an additional suite to a suite runner.
 *
 * The first suite in a suite runner is always added in srunner_create().
 * This call adds additional suites to a suite runner.
 *
 * @param sr suite runner to add the given suite
 * @param s suite to add to the given suite runner
 *
 * @since 0.7.0
 */
CK_DLL_EXP void CK_EXPORT srunner_add_suite(SRunner * sr, Suite * s);

/**
 * Frees a suite runner, including all contained suite and test cases.
 *
 * This call is responsible for freeing all resources related to a
 * suite runner and all contained suites and test cases. Suite and
 * test cases need not be freed individually, as this call handles that.
 *
 * @param sr suite runner to free
 *
 * @since 0.6.0
 */
CK_DLL_EXP void CK_EXPORT srunner_free(SRunner * sr);

/**
 * Runs a suite runner and all contained suite, printing results to
 * stdout as specified by the print_mode.
 *
 * In addition to running all suites, if the suite runner has been
 * configured to output to a log, that is also performed.
 *
 * Note that if the CK_RUN_CASE, CK_RUN_SUITE, CK_INCLUDE_TAGS and/or
 * CK_EXCLUDE_TAGS environment variables are defined, then only the
 * named suites or test cases will run.
 *
 * @param sr suite runner to run all suites from
 * @param print_mode the verbosity in which to report results to stdout
 *
 * @since 0.6.0
 */
CK_DLL_EXP void CK_EXPORT srunner_run_all(SRunner * sr,
                                          enum print_output print_mode);

/**
 * Run a specific suite or test case from a suite runner, printing results
 * to stdout as specified by the print_mode.
 *
 * In addition to running any applicable suites or test cases, if the
 * suite runner has been configured to output to a log, that is also
 * performed.
 *
 * Note that if the sname and tcname parameters are passed as null
 * then the function will fallback to using the environment variables
 * CK_RUN_SUITE and CK_RUN_CASE respectively in order to select the
 * suite/cases.
 * 
 * Similarly if the CK_INCLUDE_TAGS and/or CK_EXCLUDE_TAGS environment
 * variables are defined then these will further filter the test cases
 * (see srunner_run_tagged, below).
 *
 * @param sr suite runner where the given suite or test case must be
 * @param sname suite name to run. A NULL means use the value of the
 * environment variable CK_RUN_SUITE if set, otherwise run "any/every
 * suite".
 * @param tcname test case name to run. A NULL means use the value of
 * the environment variable CK_RUN_CASE if set, otherwise run
 * "any/every case".
 * @param print_mode the verbosity in which to report results to stdout
 *
 * @since 0.9.9
 */
CK_DLL_EXP void CK_EXPORT srunner_run(SRunner * sr, const char *sname,
                                      const char *tcname,
                                      enum print_output print_mode);


/**
 * Run a specific suite or test case or testcases with specific tags
 * from a suite runner, printing results to stdout as specified by the
 * print_mode.
 *
 * In addition to running any applicable suites or test cases, if the
 * suite runner has been configured to output to a log, that is also
 * performed.
 *
 * Note that if sname, tcname, include_tags, exclude_tags parameters
 * are passed as NULL then if the environment variables CK_RUN_SUITE,
 * CK_RUN_CASE, CK_INCLUDE_TAGS, CK_EXCLUDE_TAGS are defined then these
 * values will be used instead.
 *
 * @param sr suite runner where the given suite or test case must be
 * @param sname suite name to run. A NULL means use the value of the
 * environment variable CK_RUN_SUITE if set, otherwise run "any/every
 * suite".
 * @param tcname test case name to run. A NULL means use the value of
 * the environment variable CK_RUN_CASE if set, otherwise run
 * "any/every case".
 * @param include_tags space separate list of tags. Only run test
 * cases that share one of these tags. A NULL means use the value of
 * the environment variable CK_INCLUDE_TAGS if set, otherwise run
 * "any/every test case".
 * @param exclude_tags space separate list of tags. Only run test
 * cases that do not share one of these tags even if they are selected
 * by an included tag. A NULL means use the value of the environment
 * variable CK_EXCLUDE_TAGS if set, otherwise run "any/every test
 * case".
 * @param print_mode the verbosity in which to report results to stdout
 *
 * @since 0.11.0
 */
CK_DLL_EXP void CK_EXPORT srunner_run_tagged(SRunner * sr, const char *sname,
					     const char *tcname,
					     const char *include_tags,
					     const char *exclude_tags,
					     enum print_output print_mode);

/**
 * Retrieve the number of failed tests executed by a suite runner.
 *
 * This value represents both test failures and errors.
 *
 * @param sr suite runner to query for all failed tests
 *
 * @return number of test failures and errors found by the suite runner
 *
 * @since 0.6.1
 */
CK_DLL_EXP int CK_EXPORT srunner_ntests_failed(SRunner * sr);

/**
 * Retrieve the total number of tests run by a suite runner.
 *
 * @param sr suite runner to query for all tests run
 *
 * @return number of all tests run by the suite runner
 *
 * @since 0.6.1
 */
CK_DLL_EXP int CK_EXPORT srunner_ntests_run(SRunner * sr);

/**
 * Return an array of results for all failures found by a suite runner.
 *
 * Number of results is equal to srunner_nfailed_tests().
 *
 * Information about individual results can be queried using:
 * tr_rtype(), tr_ctx(), tr_msg(), tr_lno(), tr_lfile(), and tr_tcname().
 *
 * Memory is malloc'ed and must be freed; however free the entire structure
 * instead of individual test cases.
 *
 * @param sr suite runner to retrieve results from
 *
 * @return array of TestResult objects
 *
 * @since 0.6.0
 */
CK_DLL_EXP TestResult **CK_EXPORT srunner_failures(SRunner * sr);

/**
 * Return an array of results for all tests run by a suite runner.
 *
 * Number of results is equal to srunner_ntests_run(), and excludes
 * failures due to setup function failure.
 *
 * Information about individual results can be queried using:
 * tr_rtype(), tr_ctx(), tr_msg(), tr_lno(), tr_lfile(), and tr_tcname().
 *
 * Memory is malloc'ed and must be freed; however free the entire structure
 * instead of individual test cases.
 *
 * @param sr suite runner to retrieve results from
 *
 * @return array of TestResult objects
 *
 * @since 0.6.1
*/
CK_DLL_EXP TestResult **CK_EXPORT srunner_results(SRunner * sr);

/**
 * Print the results contained in an SRunner to stdout.
 *
 * @param sr suite runner to print results for to stdout
 * @param print_mode the print_output (verbosity) to use to report
 *         the result
 *
 * @since 0.7.0
 */
CK_DLL_EXP void CK_EXPORT srunner_print(SRunner * sr,
                                        enum print_output print_mode);

/**
 * Set the suite runner to output the result in log format to the
 * given file.
 *
 * Note: log file setting is an initialize only operation -- it should
 * be done immediately after SRunner creation, and the log file can't be
 * changed after being set.
 *
 * This setting does not conflict with the other log output types;
 * all logging types can occur concurrently if configured.
 *
 * @param sr suite runner to log results of in log format
 * @param fname file name to output log results to
 *
 * @since 0.7.1
*/
CK_DLL_EXP void CK_EXPORT srunner_set_log(SRunner * sr, const char *fname);

/**
 * Checks if the suite runner is assigned a file for log output.
 *
 * @param sr suite runner to check
 *
 * @return 1 iff the suite runner currently is configured to output
 *         in log format; 0 otherwise
 *
 * @since 0.7.1
 */
CK_DLL_EXP int CK_EXPORT srunner_has_log(SRunner * sr);

/**
 * Retrieves the name of the currently assigned file
 * for log output, if any exists.
 *
 * @return the name of the log file, or NULL if none is configured
 *
 * @since 0.7.1
 */
CK_DLL_EXP const char *CK_EXPORT srunner_log_fname(SRunner * sr);

/**
 * Set the suite runner to output the result in XML format to the
 * given file.
 *
 * Note: XML file setting is an initialize only operation -- it should
 * be done immediately after SRunner creation, and the XML file can't be
 * changed after being set.
 *
 * This setting does not conflict with the other log output types;
 * all logging types can occur concurrently if configured.
 *
 * @param sr suite runner to log results of in XML format
 * @param fname file name to output XML results to
 *
 * @since 0.9.1
*/
CK_DLL_EXP void CK_EXPORT srunner_set_xml(SRunner * sr, const char *fname);

/**
 * Checks if the suite runner is assigned a file for XML output.
 *
 * @param sr suite runner to check
 *
 * @return 1 iff the suite runner currently is configured to output
 *         in XML format; 0 otherwise
 *
 * @since 0.9.1
 */
CK_DLL_EXP int CK_EXPORT srunner_has_xml(SRunner * sr);

/**
 * Retrieves the name of the currently assigned file
 * for XML output, if any exists.
 *
 * @return the name of the XML file, or NULL if none is configured
 *
 * @since 0.9.1
 */
CK_DLL_EXP const char *CK_EXPORT srunner_xml_fname(SRunner * sr);

/**
 * Set the suite runner to output the result in TAP format to the
 * given file.
 *
 * Note: TAP file setting is an initialize only operation -- it should
 * be done immediately after SRunner creation, and the TAP file can't be
 * changed after being set.
 *
 * This setting does not conflict with the other log output types;
 * all logging types can occur concurrently if configured.
 *
 * @param sr suite runner to log results of in TAP format
 * @param fname file name to output TAP results to
 *
 * @since 0.9.12
*/
CK_DLL_EXP void CK_EXPORT srunner_set_tap(SRunner * sr, const char *fname);

/**
 * Checks if the suite runner is assigned a file for TAP output.
 *
 * @param sr suite runner to check
 *
 * @return 1 iff the suite runner currently is configured to output
 *         in TAP format; 0 otherwise
 *
 * @since 0.9.12
 */
CK_DLL_EXP int CK_EXPORT srunner_has_tap(SRunner * sr);

/**
 * Retrieves the name of the currently assigned file
 * for TAP output, if any exists.
 *
 * @return the name of the TAP file, or NULL if none is configured
 *
 * @since 0.9.12
 */
CK_DLL_EXP const char *CK_EXPORT srunner_tap_fname(SRunner * sr);

/**
 * Enum describing the current fork usage.
 */
enum fork_status
{
    CK_FORK_GETENV,             /**< look in the environment for CK_FORK */
    CK_FORK,                    /**< call fork to run tests */
    CK_NOFORK                   /**< don't call fork */
};

/**
 * Retrieve the current fork status for the given suite runner
 *
 * @param sr suite runner to check fork status of
 *
 * @since 0.8.0
 */
CK_DLL_EXP enum fork_status CK_EXPORT srunner_fork_status(SRunner * sr);

/**
 * Set the fork status for a given suite runner.
 *
 * The default fork status is CK_FORK_GETENV, which will look
 * for the CK_FORK environment variable, which can be set to
 * "yes" or "no". If the environment variable is not present,
 * CK_FORK will be used if fork() is available on the system,
 * otherwise CK_NOFORK is used.
 *
 * If set to CK_FORK or CK_NOFORK, the environment variable
 * if defined is ignored.
 *
 * If Check is compiled without support for fork(), attempting
 * to set the status to CK_FORK is ignored.
 *
 * @param sr suite runner to assign the fork status to
 * @param fstat fork status to assign
 *
 * @since 0.8.0
 */
CK_DLL_EXP void CK_EXPORT srunner_set_fork_status(SRunner * sr,
                                                  enum fork_status fstat);

/**
 * Invoke fork() during a test and assign the child to the same
 * process group that the rest of the test case uses.
 *
 * One can invoke fork() directly during a test; however doing so
 * may not guarantee that any children processes are destroyed once
 * the test finishes. Once a test has completed, all processes in
 * the process group will be killed; using this wrapper will prevent
 * orphan processes.
 *
 * If Check is compiled without fork() support this call simply
 * return -1 and does nothing.
 *
 * @return On success, the PID of the child process is returned in
 *          the parent, and 0 is returned in the child.  On failure,
 *          a value of -1 is returned to the parent process and no
 *          child process is created.
 *
 * @since 0.9.3
 */
CK_DLL_EXP pid_t CK_EXPORT check_fork(void);

/**
 * Wait for the pid and exit.
 *
 * This is to be used in conjunction with check_fork(). When called,
 * will wait for the given process to terminate. If the process
 * exited without error, exit(EXIT_SUCCESS) is invoked; otherwise
 * exit(EXIT_FAILURE) is invoked.
 *
 * If Check is compiled without support for fork(), this invokes
 * exit(EXIT_FAILURE).
 *
 * @param pid process to wait for, created by check_fork()
 *
 * @since 0.9.3
 */
CK_DLL_EXP void CK_EXPORT check_waitpid_and_exit(pid_t pid) CK_ATTRIBUTE_NORETURN;

/**
 * Set the maximal assertion message size.
 *
 * This protects the code against unintentional extremely large assertion messages
 * (values of up to 4GB were seen in the wild).
 * The usual size for a message is less than 80 bytes.
 *
 * If the environment variable CK_MAX_MSG_SIZE is defined to a positive value, it is used.
 * Otherwise, if a positive maximal message size is set via this function, it is used.
 * Otherwise, the maximal message size is one assigned at compile time (4K bytes).
 *
 * @param max_msg_size the maximal assertion message size.
 *
 * @since 0.12.0
 */
CK_DLL_EXP void CK_EXPORT check_set_max_msg_size(size_t max_msg_size);

#ifdef __cplusplus
CK_CPPEND
#endif

#endif /* CHECK_H */
