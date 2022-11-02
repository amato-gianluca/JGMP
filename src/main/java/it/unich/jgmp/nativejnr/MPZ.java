/**
* Copyright 2022 Gianluca Amato <gianluca.amato@unich.it>
*
* This file is part of JGMP. JGMP is free software: you can
* redistribute it and/or modify it under the terms of the GNU General Public
* License as published by the Free Software Foundation, either version 3 of the
* License, or (at your option) any later version.
*
* JGMP is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of a MERCHANTABILITY or FITNESS
* FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
* details.
*
* You should have received a copy of the GNU General Public License along with
* JGMP. If not, see <http://www.gnu.org/licenses/>.
*/
package it.unich.jgmp.nativejnr;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.nio.ByteBuffer;
import java.util.Optional;

import org.javatuples.Pair;
import org.javatuples.Triplet;

import jnr.ffi.Pointer;


/**
 * The class encapsulating the {@code mpz_t} data type, i.e., multi-precision
 * integer numbers.
 *
 * <p>
 * An element of {@code MPZ} contains a pointer to a native {@code mpz_t}
 * variable and registers itself with {@link GMP#cleaner} for freeing all
 * allocated memory during garbage collection.
 * <p>
 * In determining the names and prototypes of the methods of the {@code MPZ}
 * class, we adopted the following rules:
 * <ul>
 * <li>functions {@code mpz_inits}, {@code mpz_clear} and {@code mpz_clears} are
 * only used internally and are not exposed by the {@code MPZ} class;
 * <li>functions in the categories <em>Integer Import and Export</em> and
 * <em>Special Functions</em> are not exposed by the {@code MPZ} class;
 * <li>if {@code baseName} begins with {@code realloc2}, {@code set} or
 * {@code swap}, we create a method called {@code baseName} which calls the
 * original function, implicitly using {@code this} as the first {@code mpz_t}
 * parameter.
 * <li>if {@code baseName} begins with {@code init}, we create a side-effect
 * free static method (see later);
 * <li>for all the other functions:
 * <ul>
 * <li>if the function has at least a non constant {@code mpz_t} parameter, then
 * we create a method {@code baseNameAssign} which calls the original function,
 * implicitly using {@code this} as the first non-constant {@code mpz_t}
 * parameter;
 * <li>we create e side-effect free method called {@code baseName}, with the
 * exception of a few cases where such as a method would not be particularly
 * useful.
 * </ul>
 * </ul>
 * <p>
 * In general, all the parameters which are not provided implicitly to the
 * original GMP function through {@code this} should be provided explicitly by
 * having them in the method prototype.
 * <p>
 * The side-effect free methods are designed as follows. First of all, we
 * distinguish between input and output parameters for the GMP function. Some
 * parameters may have both an input and an output nature. The side-effect free
 * method takes all input parameters in its prototype, with the exception of the
 * first input {@code mpz_t} parameter which is mapped to {@code this}. If there
 * are no input {@code mpz_t} parameters, the method will be static. The method
 * creates new objects for the output parameters, eventually cloning the ones
 * also used as an input. After calling the GMP functions, the return value and
 * all the output parameters are returned by the method, eventually packed in a
 * {@link org.javatuples.Tuple}, from left to right according to the function
 * prototype. Sometimes, when the first {@code mpz_t} input parameter comes
 * after other input parameters, this procedure may lead to a prototype clash.
 * In this case, the name of the method is changed into {@code baseNameReverse}.
 */
public class MPZ extends Number implements Comparable<MPZ> {

    /**
     * Version for serializability.
     */
    private static final long serialVersionUID = 1L;

    /**
     * The pointer to the native {@code mpz_t} object.
     */
    private transient Pointer mpzPointer;

    /**
     * Result enumeration for the {@link isProbabPrime isProbabPrime} method.
     */
    public static enum PrimalityStatus {
        /**
         * The tested number is definitely non-prime.
         */
        NON_PRIME,
        /**
         * The tested number is probably prime.
         */
        PROBABLY_PRIME,
        /**
         * The tested number is definitely prime.
         */
        PRIME
    }

    /**
     * Cleaning action for the {@code MPZ} class.
     */
    private static class MPZCleaner implements Runnable {
        private Pointer mpzPointer;

        MPZCleaner(Pointer mpz) {
            this.mpzPointer = mpz;
        }

        @Override
        public void run() {
            GMP.libGMP.__gmpz_clear(mpzPointer);
        }
    }

    /**
     * A private constructor which build an {@code MPZ} starting from a pointer to
     * its native data object. The native object needs to be already initialized.
     */
    private MPZ(Pointer pointer) {
        this.mpzPointer = pointer;
        GMP.cleaner.register(this, new MPZCleaner(pointer));
    }

    /**
     * Returns the native pointer to the GMP object.
     */
    public Pointer getPointer() {
        return mpzPointer;
    }

    // Initializing Integers

    /**
     * Returns an {@code MPZ} whose value is zero.
     */
    static public MPZ init() {
        return new MPZ();
    }

    /**
     * Returns an {@code MPZ} whose value is zero, with pre-allocated space for
     * {@code n}-bit numbers.
     *
     * Calling this method is never necessary; reallocation is handled automatically
     * by GMP when needed. See the GMP function
     * <a href= "https://gmplib.org/manual/Initializing-Integers" target=
     * "_blank">{@code mpz_init2}</a>.
     *
     * @apiNote {@code n} should be treated as an unsigned long
     */
    static public MPZ init2(long n) {
        var mpzPointer = new Pointer();
        GMP.libGMP.__gmpz_init2(mpzPointer, new MPBitCntT(n));
        return new MPZ(mpzPointer);
    }

    /**
     * Changes the space allocated for this number to {@code n} bits. The value is
     * preserved if it fits, otherwise it is set to 0.
     *
     * Calling this function is never necessary; reallocation is handled
     * automatically by GMP when needed. This function can be used to increase the
     * space for a variable in order to avoid repeated automatic reallocations, or
     * to decrease it to give memory back to the heap.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ realloc2(long n) {
        GMP.libGMP.__gmpz_realloc2(mpzPointer, new MPBitCntT(n));
        return this;
    }

    // Assigning Integers

    /**
     * Sets this {@code MPZ} to {@code op}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ set(MPZ op) {
        GMP.libGMP.__gmpz_set(mpzPointer, op.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} to {@code op}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ set(long op) {
        GMP.libGMP.__gmpz_set_si(mpzPointer, new NativeLong(op));
        return this;
    }

    /**
     * Sets this {@code MPZ} to {@code op}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public MPZ setUi(long op) {
        GMP.libGMP.__gmpz_set_ui(mpzPointer, new NativeUnsignedLong(op));
        return this;
    }

    /**
     * Sets this {@code MPZ} to the truncation of {@code op}.
     *
     * @throws IllegalArgumentException if {@code op} is not a finite number. In
     *                                  this case, {@code this} is not altered.
     * @return this {@code MPZ}.
     */
    public MPZ set(double op) {
        if (!Double.isFinite(op))
            throw new IllegalArgumentException("op should be a finite number");
        GMP.libGMP.__gmpz_set_d(mpzPointer, op);
        return this;
    }

    /*
     * public MPZ set(MPQ op) {
     * GMP.libGMP.__gmpz_set_q(mpzPointer, op.mpqPointer);
     * return this;
     * }
     *
     * public MPZ set(MPF op) {
     * GMP.libGMP.__gmpz_set_f(mpzPointer, op.mpfPointer);
     * return this;
     */

    /**
     * Sets this {@code MPZ} to the number represented by the string {@code str} in
     * the specified {@code base}. See the GMP function
     * <a href="https://gmplib.org/manual/Assigning-Integers" target="
     * _blank">{@code mpz_set_str}</a>.
     *
     * @return 0 if the operation succeeded, -1 otherwise. In the latter case,
     *         {@code this} is not altered.
     */
    public int set(String str, int base) {
        return GMP.libGMP.__gmpz_set_str(mpzPointer, str, base);
    }

    /**
     * Swap the value of this {@code MPZ} with the value of {@code op}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ swap(MPZ op) {
        GMP.libGMP.__gmpz_swap(mpzPointer, op.mpzPointer);
        return this;
    }

    // Simultaneous Integer Init & Assign

    /**
     * Returns an {@code MPZ} whose value is {@code op}.
     */
    public static MPZ initSet(MPZ op) {
        return new MPZ(op);
    }

    /**
     * Returns an {@code MPZ} whose value is {@code op}.
     */
    public static MPZ initSet(long op) {
        return new MPZ(op);
    }

    /**
     * Returns an {@code MPZ} whose value is {@code op}.
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public static MPZ initSetUi(long op) {
        var mpzPointer = new Pointer();
        GMP.libGMP.__gmpz_init_set_ui(mpzPointer, new NativeUnsignedLong(op));
        return new MPZ(mpzPointer);
    }

    /**
     * Returns an {@code MPZ} whose value is the truncation of {@code op}.
     */
    public static MPZ initSet(double op) {
        return new MPZ(op);
    }

    /**
     * Returns an {@code MPZ} whose value is the number represented by the string
     * {@code str} in the specified {@code base}. See the GMP function
     * <a href="https://gmplib.org/manual/Simultaneous-Integer-Init-_0026-Assign"
     * target="_blank">{@code mpz_init_set_str}</a>.
     *
     * @return a pair whose first component is {@code 0} if the operation succeeded,
     *         and {@code -1} if either {@code base} is not valid, or {@code str} is
     *         not a valid numeric representation in the specified base. The second
     *         component of the pair is the number represented in {@code str} if the
     *         operation succeeded, {@code 0} otherwise.
     */

    public static Pair<Integer, MPZ> initSet(String str, int base) {
        var mpzPointer = new Pointer();
        var result = GMP.libGMP.__gmpz_init_set_str(mpzPointer, str, base);
        return new Pair<>(result, new MPZ(mpzPointer));
    }

    // Converting Integers

    /**
     * Converts this {@code MPZ} to an unsigned long.
     *
     * If this number is too big to fit a native unsigned long, then just the least
     * significant bits that do fit are returned. The sign of this number is
     * ignored, only the absolute value is used.
     *
     * @apiNote the return value should be treated as an unsigned long.
     */
    public long getUi() {
        return GMP.libGMP.__gmpz_get_ui(mpzPointer).longValue();
    }

    /**
     * Converts this {@code MPZ} to a signed long.
     *
     * If this number is too big to fit a native signed long, return the least
     * significant part, preserving the sign.
     */
    public long getSi() {
        return GMP.libGMP.__gmpz_get_si(mpzPointer).longValue();
    }

    /**
     * Converts this {@code MPZ} to a double, truncating if necessary. If the
     * exponent from the conversion is too big, the result is system dependent. An
     * infinity is returned where available. A hardware overflow trap may or may not
     * occur.
     */
    public double getD() {
        return GMP.libGMP.__gmpz_get_d(mpzPointer);
    }

    /**
     * Converts this {@code MPZ} to a pair made of mantissa and exponent, truncating
     * if necessary. See the GMP function
     * <a href="https://gmplib.org/manual/Converting-Integers" target=
     * "_blank">{@code mpz_get_d_2exp}</a>.
     */
    public Pair<Double, Long> getD2Exp() {
        var pexp = new NativeLongByReference();
        var d = GMP.libGMP.__gmpz_get_d_2exp(pexp, mpzPointer);
        return new Pair<>(d, pexp.getValue().longValue());
    }

    /**
     * Returns the String representation of this {@code MPZ} in the specified
     * {@code base}, or {@code null} if the base is not valid. See the GMP function
     * <a href="https://gmplib.org/manual/Converting-Integers" target=
     * "_blank">{@code mpz_get_str}</a>.
     */
    public String getStr(int base) {
        Pointer ps = GMP.libGMP.__gmpz_get_str(null, base, mpzPointer);
        if (ps == null)
            return null;
        var s = ps.getString(0);
        Native.free(Pointer.nativeValue(ps));
        return s;
    }

    // Integer Arithmetic

    /**
     * Sets this {@code MPZ} to {@code (op1 + op2)}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ addAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_add(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this + op)}.
     */
    public MPZ add(MPZ op) {
        return new MPZ().addAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to {@code (op1 + op2)}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code op2} should be treated as an unsigned long.
     */
    public MPZ addUiAssign(MPZ op1, long op2) {
        GMP.libGMP.__gmpz_add_ui(mpzPointer, op1.mpzPointer, new NativeUnsignedLong(op2));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this + op)}.
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public MPZ addUi(long op) {
        return new MPZ().addUiAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to {@code (op1 - op2)}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ subAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_sub(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this - op)}.
     */
    public MPZ sub(MPZ op) {
        return new MPZ().subAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to {@code (op1 - op2)}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code op2} should be treated as an unsigned long.
     */
    public MPZ subUiAssign(MPZ op1, long op2) {
        GMP.libGMP.__gmpz_sub_ui(mpzPointer, op1.mpzPointer, new NativeUnsignedLong(op2));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this - op)}.
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public MPZ subUi(long op) {
        return new MPZ().subUiAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to {@code (op1 - op2)}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code op1} should be treated as an unsigned long.
     */
    public MPZ uiSubAssign(long op1, MPZ op2) {
        GMP.libGMP.__gmpz_ui_sub(mpzPointer, new NativeUnsignedLong(op1), op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (op - this)}.
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public MPZ uiSub(long op) {
        return new MPZ().uiSubAssign(op, this);
    }

    /**
     * Sets this {@code MPZ} to {@code (op1 * op2)}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ mulAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_mul(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this * op)}.
     */
    public MPZ mul(MPZ op) {
        return new MPZ().mulAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to {@code (op1 * op2)}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code op2} should be treated as an unsigned long.
     */
    public MPZ mulUiAssign(MPZ op1, long op2) {
        GMP.libGMP.__gmpz_mul_ui(mpzPointer, op1.mpzPointer, new NativeUnsignedLong(op2));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this * op)}.
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public MPZ mulUi(long op) {
        return new MPZ().mulUiAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to {@code (op1 * op2)}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ mulAssign(MPZ op1, long op2) {
        GMP.libGMP.__gmpz_mul_si(mpzPointer, op1.mpzPointer, new NativeLong(op2));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this * op)}.
     */
    public MPZ mul(long op) {
        return new MPZ().mulAssign(this, op);
    }

    /**
     * Adds {@code (op1 * op2)} to this {@code MPZ}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ addmulAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_addmul(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this + op1 * op2)}.
     */
    public MPZ addmul(MPZ op1, MPZ op2) {
        return new MPZ(this).addmulAssign(op1, op2);
    }

    /**
     * Adds {@code (op1 * op2)} to this {@code MPZ}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code op2} should be treated as an unsigned long.
     */
    public MPZ addmulUiAssign(MPZ op1, long op2) {
        GMP.libGMP.__gmpz_addmul_ui(mpzPointer, op1.mpzPointer, new NativeUnsignedLong(op2));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this + op1 * op2)}.
     *
     * @apiNote {@code op2} should be treated as an unsigned long.
     */
    public MPZ addmulUi(MPZ op1, long op2) {
        return new MPZ(this).addmulUiAssign(op1, op2);
    }

    /**
     * Subtracts {@code (op1 * op2)} to this {@code MPZ}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ submulAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_submul(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this - op1 * op2)}.
     */
    public MPZ submul(MPZ op1, MPZ op2) {
        return new MPZ(this).submulAssign(op1, op2);
    }

    /**
     * Subtracts {@code (op1 * op2)} to this {@code MPZ}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code op2} should be treated as an unsigned long.
     */
    public MPZ submulUiAssign(MPZ op1, long op2) {
        GMP.libGMP.__gmpz_submul_ui(mpzPointer, op1.mpzPointer, new NativeUnsignedLong(op2));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this - op1 * op2)}.
     *
     * @apiNote {@code op2} should be treated as an unsigned long.
     */
    public MPZ submulUi(MPZ op1, long op2) {
        return new MPZ(this).submulUiAssign(op1, op2);
    }

    /**
     * Sets this {@code MPZ} to <code>(op * 2<sup>b</sup>)</code>.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ mul2ExpAssign(MPZ op, long b) {
        GMP.libGMP.__gmpz_mul_2exp(mpzPointer, op.mpzPointer, new MPBitCntT(b));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is <code>(op1 * 2<sup>b</sup>)</code>.
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public MPZ mul2Exp(long b) {
        return new MPZ().mul2ExpAssign(this, b);
    }

    /**
     * Sets this {@code MPZ} to {@code (- op)}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ negAssign(MPZ op) {
        GMP.libGMP.__gmpz_neg(mpzPointer, op.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the quotient of {@code (- this)}.
     */
    public MPZ neg() {
        return new MPZ().negAssign(this);
    }

    /**
     * Sets this {@code MPZ} to the absolute value of {@code op}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ absAssign(MPZ op) {
        GMP.libGMP.__gmpz_abs(mpzPointer, op.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the absolute value of {@code this}.
     */
    public MPZ abs() {
        return new MPZ().absAssign(this);
    }

    // Integer Division

    /**
     * Sets this {@code MPZ} to the quotient of the integer division
     * {@code (n / d)}, rounded towards zero.
     *
     * @return this {@code MPZ}.
     */
    public MPZ cdivqAssign(MPZ n, MPZ d) {
        GMP.libGMP.__gmpz_cdiv_q(mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} to the remainder of the integer division
     * {@code (n / d)}, rounded towards zero.
     *
     * @return this {@code MPZ}.
     */
    public MPZ cdivrAssign(MPZ n, MPZ d) {
        GMP.libGMP.__gmpz_cdiv_r(mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} and {@code r} to the quotient and remainder of the
     * integer division {@code (n / d)}, rounded towards zero.
     *
     * @return this {@code MPZ}.
     */
    public MPZ cdivqrAssign(MPZ r, MPZ n, MPZ d) {
        if (mpzPointer == r.mpzPointer)
            throw new IllegalArgumentException("The target of this method cannot point to the same object as r");
        GMP.libGMP.__gmpz_cdiv_qr(mpzPointer, r.mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} to the quotient of the integer division
     * {@code (n / d)}, rounded towards zero; it also returns the remainder.
     *
     * @apiNote both {@code d} and the return value should be treated as unsigned
     *          longs.
     */
    public long cdivqUiAssign(MPZ n, long d) {
        return GMP.libGMP.__gmpz_cdiv_q_ui(mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Sets this {@code MPZ} to the remainder of the integer division
     * {@code (n / d)}, rounded towards zero; it also returns the remainder.
     *
     * @apiNote both {@code d} and the return value should be treated as unsigned
     *          longs.
     */
    public long cdivrUiAssign(MPZ n, long d) {
        return GMP.libGMP.__gmpz_cdiv_r_ui(mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Sets this {@code MPZ} and {@code r} to the quotient and remainder of the
     * integer division {@code (n / d)}, rounded towards zero; it also returns the
     * remainder.
     *
     * @apiNote both {@code d} and the return value should be treated as unsigned
     *          longs.
     */
    public long cdivqrUiAssign(MPZ r, MPZ n, long d) {
        if (mpzPointer == r.mpzPointer)
            throw new IllegalArgumentException("The target of this method cannot point to the same object as r");
        return GMP.libGMP.__gmpz_cdiv_qr_ui(mpzPointer, r.mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Returns the remainder of the integer division {@code (this / d)}, rounded
     * towards zero.
     *
     * @apiNote {@code d} should be treated as an unsigned long.
     */
    public long cdivUi(long d) {
        return GMP.libGMP.__gmpz_cdiv_ui(mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Sets this {@code MPZ} to the quotient of the integer division
     * <code>(n / 2<sup>b</sup>)</code>, rounded toward zero.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ cdivq2ExpAssign(MPZ n, long b) {
        GMP.libGMP.__gmpz_cdiv_q_2exp(mpzPointer, n.mpzPointer, new MPBitCntT(b));
        return this;
    }

    /**
     * Sets this {@code MPZ} to the remainder of the integer division
     * <code>(n / 2<sup>b</sup>)</code>, rounded toward zero.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ cdivr2ExpAssign(MPZ n, long b) {
        GMP.libGMP.__gmpz_cdiv_r_2exp(mpzPointer, n.mpzPointer, new MPBitCntT(b));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the quotient of the integer division
     * {@code (this / d)}, rounded towards zero.
     */
    public MPZ cdivq(MPZ d) {
        return new MPZ().cdivqAssign(this, d);
    }

    /**
     * Returns an {@code MPZ} whose value is the remainder of the integer division
     * {@code (this / d)}, rounded towards zero.
     */
    public MPZ cdivr(MPZ d) {
        return new MPZ().cdivrAssign(this, d);
    }

    /**
     * Returns a pair of {@code MPZ}s whose values are the quotient and remainder of
     * the integer division {@code (this / d)}, rounded towards zero.
     */
    public Pair<MPZ, MPZ> cdivqr(MPZ d) {
        MPZ q = new MPZ(), r = new MPZ();
        q.cdivqrAssign(r, this, d);
        return new Pair<>(q, r);
    }

    /**
     * Returns an {@code MPZ} whose value is the quotient of the integer division
     * <code>(this / 2<sup>b</sup>)</code>, rounded towards zero.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ cdivq2Exp(long b) {
        return new MPZ().cdivq2ExpAssign(this, b);
    }

    /**
     * Returns an {@code MPZ} whose value is the remainder of the integer division
     * <code>(this / 2<sup>b</sup>)</code>, rounded towards zero.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ cdivr2Exp(long b) {
        return new MPZ().cdivr2ExpAssign(this, b);
    }

    /**
     * Sets this {@code MPZ} to the quotient of the integer division
     * {@code (n / d)}, rounded towards negative infinity.
     *
     * @return this {@code MPZ}.
     */
    public MPZ fdivqAssign(MPZ n, MPZ d) {
        GMP.libGMP.__gmpz_fdiv_q(mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} to the remainder of the integer division
     * {@code (n / d)}, rounded towards negative infinity.
     *
     * @return this {@code MPZ}.
     */
    public MPZ fdivrAssign(MPZ n, MPZ d) {
        GMP.libGMP.__gmpz_fdiv_r(mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} and {@code r} to the quotient and remainder of the
     * integer division {@code (n / d)}, rounded towards negative infinity.
     *
     * @return this {@code MPZ}.
     */
    public MPZ fdivqrAssign(MPZ r, MPZ n, MPZ d) {
        if (mpzPointer == r.mpzPointer)
            throw new IllegalArgumentException("The target of this method cannot point to the same object as r");
        GMP.libGMP.__gmpz_fdiv_qr(mpzPointer, r.mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} to the quotient of the integer division
     * {@code (n / d)}, rounded towards negative infinity; it also returns the
     * absolute value of the remainder.
     *
     * @apiNote {@code d} should be treated as an unsigned long.
     */
    public long fdivqUiAssign(MPZ n, long d) {
        return GMP.libGMP.__gmpz_fdiv_q_ui(mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Sets this {@code MPZ} to the remainder of the integer division
     * {@code (n / d)}, rounded towards negative infinity; it also returns the
     * absolute value of the remainder.
     *
     * @apiNote {@code d} should be treated as an unsigned long.
     */
    public long fdivrUiAssign(MPZ n, long d) {
        return GMP.libGMP.__gmpz_fdiv_r_ui(mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Sets this {@code MPZ} and {@code r} to the quotient and remainder of the
     * integer division {@code (n / d)}, rounded towards negative infinity; it also
     * returns the absolute value of the remainder.
     *
     * @apiNote {@code d} should be treated as an unsigned long.
     */
    public long fdivqrUiAssign(MPZ r, MPZ n, long d) {
        if (mpzPointer == r.mpzPointer)
            throw new IllegalArgumentException("The target of this method cannot point to the same object as r");
        return GMP.libGMP.__gmpz_fdiv_qr_ui(mpzPointer, r.mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Returns the remainder of the integer division {@code (this / d)}, rounded
     * towards negative infinity.
     *
     * @apiNote {@code d} should be treated as an unsigned long.
     */
    public long fdivUi(long d) {
        return GMP.libGMP.__gmpz_fdiv_ui(mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Sets this {@code MPZ} to the quotient of the integer division
     * <code>(n / 2<sup>b</sup>)</code>, rounded toward negative infinity.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ fdivq2ExpAssign(MPZ n, long b) {
        GMP.libGMP.__gmpz_fdiv_q_2exp(mpzPointer, n.mpzPointer, new NativeUnsignedLong(b));
        return this;
    }

    /**
     * Sets this {@code MPZ} to the remainder of the integer division
     * <code>(n / 2<sup>b</sup>)</code>, rounded toward negative infinity.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ fdivr2ExpAssign(MPZ n, long b) {
        GMP.libGMP.__gmpz_fdiv_r_2exp(mpzPointer, n.mpzPointer, new NativeUnsignedLong(b));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the quotient of the integer division
     * {@code (this / d)}, rounded towards negative infinity.
     */
    public MPZ fdivq(MPZ d) {
        return new MPZ().fdivqAssign(this, d);
    }

    /**
     * Returns an {@code MPZ} whose value is the remainder of the integer division
     * {@code (this / d)}, rounded towards negative infinity.
     */
    public MPZ fdivr(MPZ d) {
        return new MPZ().fdivrAssign(this, d);
    }

    /**
     * Returns two {@code MPZ}s whose values are the quotient and remainder of the
     * integer division {@code (this / d)}, rounded towards negative infinity.
     */
    public Pair<MPZ, MPZ> fdivqr(MPZ d) {
        MPZ q = new MPZ(), r = new MPZ();
        q.fdivqrAssign(r, this, d);
        return new Pair<>(q, r);
    }

    /**
     * Returns an {@code MPZ} whose value is the quotient of the integer division
     * <code>(this / 2<sup>b</sup>)</code>, rounded towards negative infinity.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ fdivq2Exp(long b) {
        return new MPZ().fdivq2ExpAssign(this, b);
    }

    /**
     * Returns an {@code MPZ} whose value is the remainder of the integer division
     * <code>(this / 2<sup>b</sup>)</code>, rounded towards negative infinity.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ fdivr2Exp(long b) {
        return new MPZ().fdivr2ExpAssign(this, b);
    }

    /**
     * Sets this {@code MPZ} to the quotient of the integer division
     * {@code (n / d)}, rounded towards zero.
     *
     * @return this {@code MPZ}.
     */
    public MPZ tdivqAssign(MPZ n, MPZ d) {
        GMP.libGMP.__gmpz_fdiv_q(mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} to the remainder of the integer division
     * {@code (n / d)}, rounded towards zero.
     *
     * @return this {@code MPZ}.
     */
    public MPZ tdivrAssign(MPZ n, MPZ d) {
        GMP.libGMP.__gmpz_tdiv_r(mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} and {@code r} to the quotient and remainder of the
     * integer division {@code (n / d)}, rounded towards zero.
     *
     * @return this {@code MPZ}.
     */
    public MPZ tdivqrAssign(MPZ r, MPZ n, MPZ d) {
        if (mpzPointer == r.mpzPointer)
            throw new IllegalArgumentException("The target of this method cannot point to the same object as r");
        GMP.libGMP.__gmpz_tdiv_qr(mpzPointer, r.mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Sets this {@code MPZ} to the quotient of the integer division
     * {@code (n / d)}, rounded towards zero; it also returns the absolute value of
     * the remainder.
     *
     * @apiNote both {@code d} and the returned value should be treateds as unsigned
     *          longs.
     */
    public long tdivqUiAssign(MPZ n, long d) {
        return GMP.libGMP.__gmpz_tdiv_q_ui(mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Sets this {@code MPZ} to the remainder of the integer division
     * {@code (n / d)}, rounded towards zero; it also returns the absolute value of
     * the remainder.
     *
     * @apiNote both {@code d} and the returned value should be treated as unsigned
     *          longs.
     */
    public long tdivrUiAssign(MPZ n, long d) {
        return GMP.libGMP.__gmpz_tdiv_r_ui(mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Sets this {@code MPZ} and {@code r} to the quotient and remainder of the
     * integer division {@code (n / d)}, rounded towards zero; it also returns the
     * absolute value of the remainder.
     *
     * @apiNote both {@code d} and the returned value should be treated as unsigned
     *          longs.
     */
    public long tdivqrUiAssign(MPZ r, MPZ n, long d) {
        if (mpzPointer == r.mpzPointer)
            throw new IllegalArgumentException("The target of this method cannot point to the same object as r");
        return GMP.libGMP.__gmpz_tdiv_qr_ui(mpzPointer, r.mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Returns the remainder of the integer division {@code (this / d)}, rounded
     * towards zero.
     *
     * @apiNote both {@code d} and the returned value should be treated as unsigned
     *          longs.
     */
    public long tdivUi(long d) {
        return GMP.libGMP.__gmpz_tdiv_ui(mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Sets this {@code MPZ} to the quotient of the integer division
     * <code>(n / 2<sup>b</sup>)</code>, rounded toward zero.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ tdivq2ExpAssign(MPZ n, long b) {
        GMP.libGMP.__gmpz_tdiv_q_2exp(mpzPointer, n.mpzPointer, new NativeUnsignedLong(b));
        return this;
    }

    /**
     * Sets this {@code MPZ} to the remainder of the integer division
     * <code>(n / 2<sup>b</sup>)</code>, rounded toward zero.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ tdivr2ExpAssign(MPZ n, long b) {
        GMP.libGMP.__gmpz_tdiv_r_2exp(mpzPointer, n.mpzPointer, new NativeUnsignedLong(b));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the quotient of the integer division
     * {@code (this / d)}, rounded towards zero.
     */
    public MPZ tdivq(MPZ d) {
        return new MPZ().tdivqAssign(this, d);
    }

    /**
     * Returns an {@code MPZ} whose value is the remainder of the integer division
     * {@code (this / d)}, rounded towards zero.
     */
    public MPZ tdivr(MPZ d) {
        return new MPZ().tdivrAssign(this, d);
    }

    /**
     * Returns two {@code MPZ}s whose values are the quotient and remainder of the
     * integer division {@code (this / d)}, rounded towards zero.
     */
    public Pair<MPZ, MPZ> tdivqr(MPZ d) {
        MPZ q = new MPZ(), r = new MPZ();
        q.tdivqrAssign(r, this, d);
        return new Pair<>(q, r);
    }

    /**
     * Returns an {@code MPZ} whose value is the quotient of the integer division
     * <code>(this / 2<sup>b</sup>)</code>, rounded towards zero.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ tdivq2Exp(long b) {
        return new MPZ().tdivq2ExpAssign(this, b);
    }

    /**
     * Returns an {@code MPZ} whose value is the remainder of the integer division
     * <code>(this / 2<sup>b</sup>)</code>, rounded towards zero.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public MPZ tdivr2Exp(long b) {
        return new MPZ().tdivr2ExpAssign(this, b);
    }

    /**
     * Sets this {@code MPZ} to {@code (n mod d)}. The sign of the divisor is
     * ignored, the result is always non-negative.
     *
     * @return this {@code MPZ}.
     */
    public MPZ modAssign(MPZ n, MPZ d) {
        GMP.libGMP.__gmpz_mod(mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this mod d)}. The sign of the
     * divisor is ignored, the result is always non-negative.
     */
    public MPZ mod(MPZ d) {
        return new MPZ().modAssign(this, d);
    }

    /**
     * Sets this {@code MPZ} to {@code (n mod d)}; it also returns the result. The
     * sign of the divisor is ignored, the result is always non-negative.
     *
     * @apiNote both {@code d} and the retuened value should be treated as unsigned
     *          longs.
     */
    public long modUiAssign(MPZ n, long d) {
        return GMP.libGMP.__gmpz_mod_ui(mpzPointer, n.mpzPointer, new NativeUnsignedLong(d)).longValue();
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this mod d)}. The sign of the
     * divisor is ignored, the result is always non-negative.
     *
     * @apiNote both {@code d} and the retuened value should be treated as unsigned
     *          longs.
     */
    public long modUi(long d) {
        return fdivUi(d);
    }

    /**
     * Sets this {@code MPZ} to the quotient of {@code (n / d)}. This method
     * produces correct results only when it is known in advance that {@code d}
     * divides {@code n}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ divexactAssign(MPZ n, MPZ d) {
        GMP.libGMP.__gmpz_divexact(mpzPointer, n.mpzPointer, d.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the quotient of {@code (this / d)}.
     * This method produces correct results only when it is known in advance that
     * {@code d} divides {@code this}.
     */
    public MPZ divexact(MPZ d) {
        return new MPZ().divexactAssign(this, d);
    }

    /**
     * Sets this {@code MPZ} to the quotient of {@code (n / d)}. This method
     * produces correct results only when it is known in advance that {@code d}
     * divides {@code n}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code d} should be treated as an unsigned long.
     */
    public MPZ divexactUiAssign(MPZ n, long d) {
        GMP.libGMP.__gmpz_divexact_ui(mpzPointer, n.mpzPointer, new NativeUnsignedLong(d));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the quotient of {@code (this / d)}.
     * This method produces correct results only when it is known in advance that
     * {@code d} divides {@code this}.
     *
     * @apiNote {@code d} should be treated as an unsigned long.
     */
    public MPZ divexactUi(long d) {
        return new MPZ().divexactUiAssign(this, d);
    }

    /**
     * Returns {@code true} if and only if {@code d} divides {@code this}.
     */
    public boolean isDivisible(MPZ d) {
        return GMP.libGMP.__gmpz_divisible_p(mpzPointer, d.mpzPointer);
    }

    /**
     * Returns {@code true} if and only if {@code d} divides {@code this}.
     *
     * @apiNote {@code d} should be treated as an unsigned long.
     */
    public boolean isDivisibleUi(long d) {
        return GMP.libGMP.__gmpz_divisible_ui_p(mpzPointer, new NativeUnsignedLong(d));
    }

    /**
     * Returns {@code true} if and only if <code>2<sup>b</sup></code> divides
     * {@code this}.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public boolean isDivisible2Exp(long b) {
        return GMP.libGMP.__gmpz_divisible_2exp_p(mpzPointer, new MPBitCntT(b));
    }

    /**
     * Returns {@code true} if and only if {@code this} is congruent to {@code c}
     * modulo {@code d}.
     */
    public boolean isCongruent(MPZ c, MPZ d) {
        return GMP.libGMP.__gmpz_congruent_p(mpzPointer, c.mpzPointer, d.mpzPointer);
    }

    /**
     * Returns {@code true} if and only if {@code this} is congruent to {@code c}
     * modulo {@code d}.
     *
     * @apiNote {@code c} and {@code d} should be treated as unsigned longs.
     */
    public boolean isCongruentUi(long c, long d) {
        return GMP.libGMP.__gmpz_congruent_ui_p(mpzPointer, new NativeUnsignedLong(c), new NativeUnsignedLong(d));
    }

    /**
     * Returns {@code true} if and only if {@code this} is congruent to {@code c}
     * modulo <code>2<sup>b</sup></code>.
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public boolean isCongruent2Exp(MPZ c, long b) {
        return GMP.libGMP.__gmpz_congruent_2exp_p(mpzPointer, c.mpzPointer, new MPBitCntT(b));
    }

    // Integer Exponentiation

    /**
     * Sets this {@code MPZ} to <code>(base<sup>exp</sup>)</code> modulo
     * {@code mod}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ powmAssign(MPZ base, MPZ exp, MPZ mod) {
        GMP.libGMP.__gmpz_powm(mpzPointer, base.mpzPointer, exp.mpzPointer, mod.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is <code>(this<sup>exp</sup>)</code>
     * modulo {@code mod}.
     */
    public MPZ powm(MPZ exp, MPZ mod) {
        return new MPZ().powmAssign(this, exp, mod);
    }

    /**
     * Sets this {@code MPZ} to <code>(base<sup>exp</sup>)</code> modulo
     * {@code mod}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code exp} should be treated as an unsigned long.
     */
    public MPZ powmUiAssign(MPZ base, long exp, MPZ mod) {
        GMP.libGMP.__gmpz_powm_ui(mpzPointer, base.mpzPointer, new NativeUnsignedLong(exp), mod.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is <code>(this<sup>exp</sup>)</code>
     * modulo {@code mod}.
     *
     * @apiNote {@code exp} should be treated as an unsigned long.
     */
    public MPZ powmUi(long exp, MPZ mod) {
        return new MPZ().powmUiAssign(this, exp, mod);
    }

    /**
     * Sets this {@code MPZ} to <code>(base<sup>exp</sup>)</code> modulo
     * {@code mod}. It is required that {@code (exp > 0} and that {@code mod} is
     * odd. This function is intended for cryptographic purposes, where resilience
     * to side-channel attacks is desired.
     *
     * @return this {@code MPZ}.
     */
    public MPZ powmSecAssign(MPZ base, MPZ exp, MPZ mod) {
        GMP.libGMP.__gmpz_powm_sec(mpzPointer, base.mpzPointer, exp.mpzPointer, mod.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is <code>(this<sup>exp</sup>)</code>
     * modulo {@code mod}. It is required that {@code (exp > 0)} and that
     * {@code mod} is odd. This function is intended for cryptographic purposes,
     * where resilience to side-channel attacks is desired.
     */
    public MPZ powmSec(MPZ exp, MPZ mod) {
        return new MPZ().powmSecAssign(this, exp, mod);
    }

    /**
     * Sets this {@code MPZ} to <code>(base<sup>exp</sup>)</code>. The case
     * <code>0<sup>0</sup></code> yields {@code 1}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code d} should be treated as an unsigned long.
     */
    public MPZ powUiAssign(MPZ base, long exp) {
        GMP.libGMP.__gmpz_pow_ui(mpzPointer, base.mpzPointer, new NativeUnsignedLong(exp));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is <code>(this<sup>exp</sup>)</code>. The
     * case <code>0<sup>0</sup></code> yields {@code 1}.
     *
     * @apiNote {@code exp} should be treated as an unsigned long.
     */
    public MPZ powUi(long exp) {
        return new MPZ().powUiAssign(this, exp);
    }

    /**
     * Sets this {@code MPZ} to <code>(base<sup>exp</sup>)</code>. The case
     * <code>0<sup>0</sup></code> yields {@code 1}.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code exp} should be treated as an unsigned long.
     */
    public MPZ powUiAssign(long base, long exp) {
        GMP.libGMP.__gmpz_ui_pow_ui(mpzPointer, new NativeUnsignedLong(base), new NativeUnsignedLong(exp));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is <code>(base<sup>exp</sup>)</code>. The
     * case <code>0<sup>0</sup></code> yields {@code 1}.
     *
     * @apiNote {@code exp} should be treated as an unsigned long.
     */
    public static MPZ powUi(long base, long exp) {
        return new MPZ().powUiAssign(base, exp);
    }

    // Integer Roots

    /**
     * Sets this {@code MPZ} to the truncated integer part of the {@code n}th root
     * of {@code op}.
     *
     * @return true if the computation is exact.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public boolean rootAssign(MPZ op, long n) {
        return GMP.libGMP.__gmpz_root(mpzPointer, op.mpzPointer, new NativeUnsignedLong(n));
    }

    /**
     * Returns an {@code MPZ} whose value is the truncated integer part of the
     * {@code n}th root of {@code this}, and a boolean flag which is true when the
     * result is exact.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public Pair<Boolean, MPZ> root(long n) {
        var root = new MPZ();
        var exact = root.rootAssign(this, n);
        return new Pair<>(exact, root);
    }

    /**
     * Sets this {@code MPZ} to the truncated integer part of the {@code n}th root
     * of {@code u} and {@code rem} to the remainder, i.e.,
     * <code>(u - root<sup>n</sup>)</code>.
     *
     * @return this {@code MPZ}.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ rootremAssign(MPZ rem, MPZ u, long n) {
        GMP.libGMP.__gmpz_rootrem(mpzPointer, rem.mpzPointer, u.mpzPointer, new NativeUnsignedLong(n));
        return this;
    }

    /**
     * Returns two {@code MPZ}s whose values are the truncated integer part of the
     * {@code n}th root of {@code this} and the remainder, i.e.,
     * <code>(u - root<sup>n</sup>)</code>.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public Pair<MPZ, MPZ> rootrem(long n) {
        MPZ res = new MPZ(), rem = new MPZ();
        res.rootremAssign(rem, this, n);
        return new Pair<>(res, rem);
    }

    /**
     * Sets this {@code MPZ} to the truncated integer part of the square root of
     * {@code op}.
     *
     * @return this {@code MPZ}.
     */
    public MPZ sqrtAssign(MPZ op) {
        GMP.libGMP.__gmpz_sqrt(mpzPointer, op.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the truncated inegeter part of the
     * square root of {@code this}.
     */
    public MPZ sqrt() {
        return new MPZ().sqrtAssign(this);
    }

    /**
     * Sets this {@code MPZ} to the truncated integer part of the square root of
     * {@code op} and {@code rem} to the remainder, i.e.,
     * <code>(op - root<sup>2</sup>)</code>.
     *
     * @return this {@code MPZ}.
     */
    public MPZ sqrtremAssign(MPZ rem, MPZ op) {
        GMP.libGMP.__gmpz_sqrtrem(mpzPointer, rem.mpzPointer, op.mpzPointer);
        return this;
    }

    /**
     * Returns two {@code MPZ}s whose values are the truncated integer part of the
     * square root of {@code this} and the remainder, i.e.,
     * <code>(op - root<sup>2</sup>)</code>.
     */
    public Pair<MPZ, MPZ> sqrtrem() {
        MPZ res = new MPZ(), rem = new MPZ();
        res.sqrtremAssign(rem, this);
        return new Pair<>(res, rem);
    }

    /**
     * Returns {@code true} if and only if this number is a perfect power, i.e., if
     * there exist integers {@code a} and {@code b}, with {@code (b > 1)}, such that
     * {@code this} equals <code>(a<sup>b</sup>)</code>. Under this definition both
     * {@code 0} and {@code 1} are considered to be perfect powers. Negative values
     * are accepted, but of course can only be odd perfect powers.
     */
    public boolean isPerfectPower() {
        return GMP.libGMP.__gmpz_perfect_power_p(mpzPointer);
    }

    /**
     * Returns {@code true} if and only if this number is a perfect square. Under
     * this definition both {@code 0} and {@code 1} are considered to be perfect
     * squares.
     */
    public boolean isPerfectSquare() {
        return GMP.libGMP.__gmpz_perfect_square_p(mpzPointer);
    }

    // Number Theoretic Functions

    /**
     * Returns {@code true} if and only if {@code this} is prime. See the GMP
     * function
     * <a href="https://gmplib.org/manual/Number-Theoretic-Functions" target=
     * "_blank">{@code mpz_probab_prime_p}</a>.
     *
     * @param reps can be used to tune the probability of a non-prime being
     *             identified as “probably prime”. Reasonable values of reps are
     *             between 15 and 50.
     * @return an instance of the {@link PrimalityStatus} enum, telling whether
     *         {@code this} is definitely prime, probably prime or definitely
     *         non-prime.
     */
    public PrimalityStatus isProbabPrime(int reps) {
        var res = GMP.libGMP.__gmpz_probab_prime_p(mpzPointer, reps);
        return PrimalityStatus.values()[res];
    }

    /**
     * Sets this {@code MPZ} to the next prime greater then {@code op}. This
     * function uses a probabilistic algorithm to identify primes. For practical
     * purposes it’s adequate, the chance of a composite passing will be extremely
     * small.
     */
    public MPZ nextprimeAssign(MPZ op) {
        GMP.libGMP.__gmpz_nextprime(mpzPointer, op.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the next prime greater then
     * {@code this}.
     *
     * @see nextprimeAssign(MPZ)
     */
    public MPZ nextprime() {
        return new MPZ().nextprimeAssign(this);
    }

    /**
     * Sets this {@code MPZ} to the greatest commond divisor of {@code op1} and
     * {@code op2}. The result is always positive even if one or both input operands
     * are negative. Except if both inputs are zero; then this function defines
     * {@code gcd(0,0) = 0}.
     */
    public MPZ gcdAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_gcd(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the greatest commond divisor of
     * {@code this} and {@code op}.
     *
     * @see gcdAssign(MPZ, MPZ)
     */
    public MPZ gcd(MPZ op) {
        return new MPZ().gcdAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to the greatest commond divisor of {@code op1} and
     * {@code op2}, and returns it. If the result does not fit into an unsigned
     * long, then 0 si returned.
     *
     * @see gcdAssign(MPZ, MPZ)
     *
     * @apiNote both {@code op2} and the returned value should be treated as
     *          unsigned longs.
     */
    public long gcdUiAssign(MPZ op1, long op2) {
        return GMP.libGMP.__gmpz_gcd_ui(mpzPointer, op1.mpzPointer, new NativeUnsignedLong(op2)).longValue();
    }

    /**
     * Returns the greatest commond divisor of {@code this} and {@code op}. If the
     * result does not fit into an unsigned long, 0 is returned.
     *
     * @apiNote both {@code op2} and the returned value should be treated as
     *          unsigned longs.
     */
    public long gcdUi(long op) {
        return GMP.libGMP.__gmpz_gcd_ui(null, mpzPointer, new NativeUnsignedLong(op)).longValue();
    }

    /**
     * Sets this {@code MPZ} to the greatest common divisor of {@code a} and
     * {@code b}, and in addition sets {@code s} and {@code t} to coefficients
     * satisfying {@code (a*s + b*t = g)}. If {@code t} or {@code g} is null, that
     * value is not computed. See the GMP function
     * <a href="https://gmplib.org/manual/Number-Theoretic-Functions" target=
     * "_blank">{@code mpz_gcdext}</a>.
     */
    public MPZ gcdextAssign(MPZ s, MPZ t, MPZ a, MPZ b) {
        GMP.libGMP.__gmpz_gcdext(mpzPointer, s == null ? null : s.mpzPointer, t == null ? null : t.mpzPointer, a.mpzPointer,
                b.mpzPointer);
        return this;
    }

    /**
     * Returns the greatest common divisor of {@code this} and {@code op}, together
     * with numbers {@code s} and {@code t} satisfying {@code (a*this + b*op = g)}
     * See the GMP function
     * <a href="https://gmplib.org/manual/Number-Theoretic-Functions" target=
     * "_blank">{@code mpz_gcdext}</a>.
     */
    public Triplet<MPZ, MPZ, MPZ> gcdext(MPZ op) {
        MPZ r = new MPZ(), s = new MPZ(), t = new MPZ();
        r.gcdextAssign(s, t, this, op);
        return new Triplet<>(r, s, t);
    }

    /**
     * Sets this {@code MPZ} to the least common multiple of {@code op1} and
     * {@code op2}. The result is always non-negative even if one or both input
     * operands are negative. The result will be zero if either {@code op1} or
     * {@code op2} is zero.
     */
    public MPZ lcmAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_lcm(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the least common multiple of
     * {@code this} and {@code op}.
     *
     * @see lcmAssign(MPZ, MPZ)
     */
    public MPZ lcm(MPZ op) {
        return new MPZ().lcmAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to the least common multiple of {@code op1} and
     * {@code op2}.
     *
     * @see lcmAssign(MPZ, MPZ)
     *
     * @apiNote {@code op2} should be treated as an unsigned long.
     */
    public MPZ lcmUiAssign(MPZ op1, long op2) {
        GMP.libGMP.__gmpz_lcm_ui(mpzPointer, op1.mpzPointer, new NativeUnsignedLong(op2));
        return this;
    }

    /**
     * Returns the least common multiple of {@code this} and {@code op}.
     *
     * @see lcmAssign(MPZ, MPZ)
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public MPZ lcmUi(long op) {
        return new MPZ().lcmUiAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to the inverse of {@code op1} modulo {@code op2}. If
     * the inverse does not exist, the new value of this {@code MPZ} is undefined.
     * The behaviour of this function is undefined when {@code op2} is zero.
     *
     * @return true if the inverse exists, false otherwise.
     */
    public boolean invertAssign(MPZ op1, MPZ op2) {
        return GMP.libGMP.__gmpz_invert(mpzPointer, op1.mpzPointer, op2.mpzPointer);
    }

    /**
     * Optionally returns, when it exists, an {@code MPZ} whose value is the inverse
     * of {@code this} modulo {@code op}. The behaviour of this function is
     * undefined when {@code op} is zero.
     */
    public Optional<MPZ> invert(MPZ modulus) {
        var res = new MPZ();
        var exists = res.invertAssign(this, modulus);
        return exists ? Optional.of(res) : Optional.empty();
    }

    /**
     * Returns the Jacobi symbol {@code (this / b)}. This is defined only for
     * {@code b} odd.
     */
    public int jacobi(MPZ b) {
        return GMP.libGMP.__gmpz_jacobi(mpzPointer, b.mpzPointer);
    }

    /**
     * Returns the Legendre symbol {@code (this / p)}. This is defined only for
     * {@code p} an odd positive prime, and for such {@code p} it’s identical to the
     * Jacobi symbol.
     */
    public int legendre(MPZ p) {
        return GMP.libGMP.__gmpz_legendre(mpzPointer, p.mpzPointer);
    }

    /**
     * Returns the Jacobi symbol {@code (this / n)} with the Kronecker extension
     * {@code (this/2)=(2/this)} when {@code this} is odd, or {@code (this/2)=0 }
     * when {@code this} is even. When {@code b} is odd the Jacobi symbol and
     * Kronecker symbol are identical. See the GMP function {@code mpz_kronecker}.
     */
    public int kronecker(MPZ b) {
        // the jacobi GMP function already implements the Kronecker extension
        return GMP.libGMP.__gmpz_jacobi(mpzPointer, b.mpzPointer);
    }

    /**
     * Returns the Jacobi symbol {@code (this / b)} with the Kronecker extension.
     *
     * @see kronecker(MPZ)
     */
    public int kronecker(long b) {
        return GMP.libGMP.__gmpz_kronecker_si(mpzPointer, new NativeLong(b));
    }

    /**
     * Returns the Jacobi symbol {@code (this / b)} with the Kronecker extension.
     *
     * @see kronecker(MPZ)
     *
     * @apiNote {@code b} should be treated as an unsigned long.
     */
    public int kroneckerUi(long b) {
        return GMP.libGMP.__gmpz_kronecker_ui(mpzPointer, new NativeUnsignedLong(b));
    }

    /**
     * Returns the Jacobi symbol {@code (a / this)} with the Kronecker extension.
     *
     * @see kronecker(MPZ)
     */
    public int kroneckerReverse(long a) {
        return GMP.libGMP.__gmpz_si_kronecker(new NativeLong(a), mpzPointer);
    }

    /**
     * Returns the Jacobi symbol {@code (a / this)} with the Kronecker extension.
     *
     * @see kronecker(MPZ)
     *
     * @apiNote {@code a} should be treated as an unsigned long.
     */
    public int uiKronecker(long a) {
        return GMP.libGMP.__gmpz_ui_kronecker(new NativeUnsignedLong(a), mpzPointer);
    }

    /**
     * Removes all occurrences of the factor {@code f} from {@code op} and stores
     * the result in this {@code MPZ}. The return value is the number of occurrences
     * of {@code f} which were removed.
     */
    public long removeAssign(MPZ op, MPZ f) {
        return GMP.libGMP.__gmpz_remove(mpzPointer, op.mpzPointer, f.mpzPointer).longValue();
    }

    /**
     * Returns the result of removing the factor{@code f} from {@code this},
     * together with the number of occurrences which were removed.
     *
     * @apiNote the first element of the returned value should be treated as an
     *          unsigned long.
     */
    public Pair<Long, MPZ> remove(MPZ f) {
        var res = new MPZ();
        var count = GMP.libGMP.__gmpz_remove(res.mpzPointer, mpzPointer, f.mpzPointer);
        return new Pair<>(count.longValue(), res);
    }

    /**
     * Sets this {@code MPZ} to the factorial of {@code n}.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ facUiAssign(long n) {
        GMP.libGMP.__gmpz_fac_ui(mpzPointer, new NativeUnsignedLong(n));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the factorial of {@code n}.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public static MPZ facUi(long n) {
        return new MPZ().facUiAssign(n);
    }

    /**
     * Sets this {@code MPZ} to the double factorial of {@code n}.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ dfacUiAssign(long n) {
        GMP.libGMP.__gmpz_2fac_ui(mpzPointer, new NativeUnsignedLong(n));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value the double factorial of {@code n}.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public static MPZ dfacUi(long n) {
        return new MPZ().dfacUiAssign(n);
    }

    /**
     * Sets this {@code MPZ} to the {@code m}-multi factorial of {@code n}.
     *
     * @apiNote both {@code n} and {@code m} should be treated as unsigned longs.
     */
    public MPZ mfacUiUiAssign(long n, long m) {
        GMP.libGMP.__gmpz_mfac_uiui(mpzPointer, new NativeUnsignedLong(n), new NativeUnsignedLong(m));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the {@code m}-multi factorial of
     * {@code n}.
     *
     * @apiNote both {@code n} and {@code m} should be treated as unsigned longs.
     */
    public static MPZ mfacUiUi(long n, long m) {
        return new MPZ().mfacUiUiAssign(n, m);
    }

    /**
     * Sets this {@code MPZ} to the primorial of {@code n}, i.e., the product of all
     * positive prime numbers {@code <= n}.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ primorialUiAssign(long n) {
        GMP.libGMP.__gmpz_primorial_ui(mpzPointer, new NativeUnsignedLong(n));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the primorial of {@code n}, i.e., the
     * product of all positive prime numbers {@code <= n}.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public static MPZ primorialUi(long n) {
        return new MPZ().primorialUiAssign(n);
    }

    /**
     * Sets this {@code MPZ} to the binomial coefficient {@code n} over {@code k}.
     * Negative values of {@code n} are supported using the identity
     * {@code (bin(-n,k) = (-1)^k * bin(n+k-1,k))}, see Knuth volume 1 section 1.2.6
     * part G.
     *
     * @apiNote {@code k} should be treated as an unsigned long.
     */
    public MPZ binUiAssign(MPZ n, long k) {
        GMP.libGMP.__gmpz_bin_ui(mpzPointer, n.mpzPointer, new NativeUnsignedLong(k));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the binomial coefficient {@code this}
     * over {@code k}. Negative values of {@code this} are supported as in
     * {@link binUiAssign(MPZ, long)}.
     *
     * @apiNote {@code k} should be treated as an unsigned long.
     */
    public MPZ binUi(long k) {
        return new MPZ().binUiAssign(this, k);
    }

    /**
     * Sets this {@code MPZ} to the binomial coefficient {@code n} over {@code k}.
     *
     * @apiNote both {@code n} and {@code k} should be treated as unsigned longs.
     */
    public MPZ binUiUiAssign(long n, long k) {
        GMP.libGMP.__gmpz_bin_uiui(mpzPointer, new NativeUnsignedLong(n), new NativeUnsignedLong(k));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the binomial coefficient {@code n} over
     * {@code k}.
     *
     * @apiNote both {@code n} and {@code k} should be treated as unsigned longs.
     */
    public static MPZ binUiUi(long n, long k) {
        return new MPZ().binUiUiAssign(n, k);
    }

    /**
     * Sets this {@code MPZ} to the {@code n}-th Fibonacci number.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ fibUiAssign(long n) {
        GMP.libGMP.__gmpz_fib_ui(mpzPointer, new NativeUnsignedLong(n));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the {@code n}-th Fibonacci number.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public static MPZ fibUi(long n) {
        return new MPZ().fibUiAssign(n);
    }

    /**
     * Sets the value of {@code this} and {@code fnsub1} to the {@code n}-th and
     * {@code (n-1)}-th Fibonacci numbers respecively.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ fib2UiAssign(MPZ fnsub1, long n) {
        GMP.libGMP.__gmpz_fib2_ui(mpzPointer, fnsub1.mpzPointer, new NativeUnsignedLong(n));
        return this;
    }

    /**
     * Returns two {@code MPZ} whose values are the {@code n}-th and
     * {@code (n-1)}-th Fibonacci numbers.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public static Pair<MPZ, MPZ> fib2Ui(long n) {
        MPZ fnsub1 = new MPZ(), fn = new MPZ();
        fn.fib2UiAssign(fnsub1, n);
        return new Pair<>(fn, fnsub1);
    }

    /**
     * Sets this {@code MPZ} to the {@code n}-th Lucas number.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ lucnumUiAssign(long n) {
        GMP.libGMP.__gmpz_lucnum_ui(mpzPointer, new NativeUnsignedLong(n));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is the {@code n}-th Lucas number.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public static MPZ lucnumUi(long n) {
        return new MPZ().lucnumUiAssign(n);
    }

    /**
     * Sets the value of {@code this} and {@code fnsub1} to the {@code n}-th and
     * {@code (n-1)}-th Lucas numbers respecively.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ lucnum2UiAssign(MPZ fnsub1, long n) {
        GMP.libGMP.__gmpz_lucnum2_ui(mpzPointer, fnsub1.mpzPointer, new NativeUnsignedLong(n));
        return this;
    }

    /**
     * Returns two {@code MPZ} whose values are the {@code n}-th and
     * {@code (n-1)}-th Lucas numbers.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public static Pair<MPZ, MPZ> lucnum2Ui(long n) {
        MPZ lnsub1 = new MPZ(), ln = new MPZ();
        ln.lucnum2UiAssign(lnsub1, n);
        return new Pair<>(ln, lnsub1);
    }

    // Integer Comparisons

    /**
     * Compares {@code this} with {@code op}. Returns a positive value if
     * {@code (this > op)}, zero if {@code this = op}, or a negative value if
     * {@code this < op}.
     */
    public int cmp(MPZ op) {
        return GMP.libGMP.__gmpz_cmp(mpzPointer, op.mpzPointer);
    }

    /**
     * Compares {@code this} with {@code op}. Returns a positive value if
     * {@code (this > op)}, zero if {@code this = op}, or a negative value if
     * {@code this < op}. The value of {@code op} may be infinite, but the result is
     * undefined on NaNs.
     */
    public int cmp(double op) {
        return GMP.libGMP.__gmpz_cmp_d(mpzPointer, op);
    }

    /**
     * Compares {@code this} with {@code op}. Returns a positive value if
     * {@code (this > op)}, zero if {@code this = op}, or a negative value if
     * {@code this < op}.
     */
    public int cmp(long op) {
        return GMP.libGMP.__gmpz_cmp_si(mpzPointer, new NativeLong(op));
    }

    /**
     * Compares {@code this} with {@code op}. Returns a positive value if
     * {@code (this > op)}, zero if {@code this = op}, or a negative value if
     * {@code this < op}.
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public int cmpUi(long op) {
        return GMP.libGMP.__gmpz_cmp_ui(mpzPointer, new NativeUnsignedLong(op));
    }

    /**
     * Compares the absolute values of {@code this} and {@code op}. Returns a
     * positive value if {@code (abs(this) > abs(op))}, zero if
     * {@code abs(this) = abs(op)}, or a negative value if
     * {@code abs(this) < abs(op)}.
     */
    public int cmpabs(MPZ op) {
        return GMP.libGMP.__gmpz_cmpabs(mpzPointer, op.mpzPointer);
    }

    /**
     * Compares the absolute values of {@code this} and {@code op}. Returns a
     * positive value if {@code (abs(this) > abs(op))}, zero if
     * {@code abs(this) = abs(op)}, or a negative value if
     * {@code abs(this) < abs(op)}. The value of {@code op} may be infinite, but the
     * result is undefined on NaNs.
     */
    public int cmpabs(double op) {
        return GMP.libGMP.__gmpz_cmpabs_d(mpzPointer, op);
    }

    /**
     * Compares the absolute values of {@code this} and {@code op}. Returns a
     * positive value if {@code (abs(this) > abs(op))}, zero if
     * {@code abs(this) = abs(op)}, or a negative value if
     * {@code abs(this) < abs(op)}.
     *
     * @apiNote {@code op} should be treated as an unsigned long.
     */
    public int cmpabsUi(long op) {
        return GMP.libGMP.__gmpz_cmpabs_ui(mpzPointer, new NativeUnsignedLong(op));
    }

    /**
     * Returns {@code +1} if {@code (op > 0)}, {@code 0} if {@code (op = 0)} and
     * {@code -1} if {@code op < 0}.
     */
    public int sgn() {
        return GMP.libGMP.__gmpz_sgn(mpzPointer);
    }

    // Integer Logic and Bit Fiddling

    /**
     * Sets this {@code MPZ} to {@code (op1 & op2)}.
     */
    public MPZ andAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_and(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this & op)}.
     */
    public MPZ and(MPZ op) {
        return new MPZ().andAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to {@code (op1 | op2)}.
     */
    public MPZ iorAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_ior(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this | op)}.
     */
    public MPZ ior(MPZ op) {
        return new MPZ().iorAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to {@code (op1 ^ op2)}.
     */
    public MPZ xorAssign(MPZ op1, MPZ op2) {
        GMP.libGMP.__gmpz_xor(mpzPointer, op1.mpzPointer, op2.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (this ^ op)}.
     */
    public MPZ xor(MPZ op) {
        return new MPZ().xorAssign(this, op);
    }

    /**
     * Sets this {@code MPZ} to {@code (~ op)}.
     */
    public MPZ comAssign(MPZ op) {
        GMP.libGMP.__gmpz_com(mpzPointer, op.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is {@code (~ op)}.
     */
    public MPZ com() {
        return new MPZ().comAssign(this);
    }

    /**
     * If this {@code MPZ} is non-negative, returns its population count, which is
     * the number of {@code 1} bits in its binary representation. If this
     * {@code MPZ} is negative, the number of {@code 1}s is infinite, and the return
     * value is the largest possible value for the native type {@code mp_bitcnt_t}.
     *
     * @apiNote the returned value should be treated as an unigned long.
     */
    public long popcount() {
        return GMP.libGMP.__gmpz_popcount(mpzPointer).longValue();
    }

    /**
     * If {@code this} and {@code op} are both {@code >= 0} or both {@code < 0},
     * returns the Hamming distance between them, which is the number of bit
     * positions where {@code this} and {@code op} have different bit values. If one
     * operand is {@code >= 0} and the other {@code < 0} then the number of bits
     * different is infinite, and the return value is the largest possible value for
     * the native type {@code mp_bitcnt_t}.
     *
     * @apiNote the returned value should be treated as an unigned long.
     */
    public long hamdist(MPZ op) {
        return GMP.libGMP.__gmpz_hamdist(mpzPointer, op.mpzPointer).longValue();
    }

    /**
     * Scans this {@code MPZ}, starting from bit {@code starting_bit}, towards more
     * significant bits, until the first {@code 0} bit is found. Returns the index
     * of the found bit.
     *
     * @apiNote both {@code starting_bit} and the returned value should be treated
     *          as unsigned longs.
     */
    public long scan0(long starting_bit) {
        return GMP.libGMP.__gmpz_scan0(mpzPointer, new MPBitCntT(starting_bit)).longValue();
    }

    /**
     * Scans {@code this}, starting from bit {@code starting_bit}, towards more
     * significant bits, until the first {@code 1} bit is found. Returns the index
     * of the found bit.
     *
     * @apiNote both {@code starting_bit} and the returned value should be treated
     *          as unsigned longs.
     */
    public long scan1(long starting_bit) {
        return GMP.libGMP.__gmpz_scan1(mpzPointer, new MPBitCntT(starting_bit)).longValue();
    }

    /**
     * Sets the bit {@code index} of this {@code MPZ}.
     *
     * @apiNote both {@code starting_bit} and the returned value should be treated
     *          as unsigned longs.
     */
    public MPZ setbitAssign(long index) {
        GMP.libGMP.__gmpz_setbit(mpzPointer, new MPBitCntT(index));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is
     * <code>(this | 2<sup>index</sup>)</code>.
     *
     * @apiNote both {@code starting_bit} and the returned value should be treated
     *          as unsigned longs.
     */
    public MPZ setbit(long index) {
        return new MPZ(this).setbitAssign(index);
    }

    /**
     * Clears the bit {@code index} of this {@code MPZ}.
     *
     * @apiNote both {@code starting_bit} and the returned value should be treated
     *          as unsigned longs.
     */
    public MPZ clrbitAssign(long index) {
        GMP.libGMP.__gmpz_clrbit(mpzPointer, new MPBitCntT(index));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is
     * <code>(this &amp; ~ 2<sup>index</sup>)</code>.
     *
     * @apiNote both {@code starting_bit} and the returned value should be treated
     *          as unsigned longs.
     */
    public MPZ clrbit(long index) {
        return new MPZ(this).clrbitAssign(index);
    }

    /**
     * Complements the bit {@code index} of this {@code MPZ}.
     *
     * @apiNote both {@code starting_bit} and the returned value should be treated
     *          as unsigned longs.
     */
    public MPZ combitAssign(long index) {
        GMP.libGMP.__gmpz_combit(mpzPointer, new MPBitCntT(index));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is
     * <code>(this ^ 2<sup>index</sup>)</code>.
     *
     * @apiNote both {@code starting_bit} and the returned value should be treated
     *          as unsigned longs.
     */
    public MPZ combit(long index) {
        return new MPZ(this).combitAssign(index);
    }

    /**
     * Returns the bit {@code index} of this {@code MPZ}.
     *
     * @apiNote both {@code starting_bit} and the returned value should be treated
     *          as unsigned longs.
     */
    public int tstbit(long index) {
        return GMP.libGMP.__gmpz_tstbit(mpzPointer, new MPBitCntT(index));
    }

    // Random Number Functions

    /**
     * Sets this {@code MPZ} to a uniformly distributed random integer in the range
     * {@code 0} to <code>(2<sup>n</sup> - 1)</code>, inclusive.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ urandombAssign(RandState s, long n) {
        GMP.libGMP.__gmpz_urandomb(mpzPointer, s.getPointer(), new MPBitCntT(n));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is an uniformly distributed random integer
     * in the range {@code 0}} to <code>(2<sup>n</sup> - 1)</code>, inclusive.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public static MPZ urandomb(RandState s, long n) {
        var z = new MPZ();
        z.urandombAssign(s, n);
        return z;
    }

    /**
     * Sets this {@code MPZ} to a uniformly distributed random integer in the range
     * {@code 0} to {@code (n - 1)}, inclusive.
     */
    public MPZ urandommAssign(RandState s, MPZ n) {
        GMP.libGMP.__gmpz_urandomm(mpzPointer, s.getPointer(), n.mpzPointer);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is an uniformly distributed random integer
     * in the range {@code 0} to {@code (n - 1)}, inclusive.
     */
    public static MPZ urandomm(RandState s, MPZ n) {
        var z = new MPZ();
        z.urandommAssign(s, n);
        return z;
    }

    /**
     * Sets this {@code MPZ} to a random integer with long strings of zeros and ones
     * in the binary representation. Useful for testing functions and algorithms,
     * since this kind of random numbers have proven to be more likely to trigger
     * corner-case bugs. The random number will be in the range
     * <code>(2<sup>n - 1</sup>)</code> to <code>(2<sup>n</sup> - 1)</code>,
     * inclusive.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public MPZ rrandombAssign(RandState s, long n) {
        GMP.libGMP.__gmpz_rrandomb(mpzPointer, s.getPointer(), new MPBitCntT(n));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is a random integer with long strings of
     * zeros and ones in the binary representation. Useful for testing functions and
     * algorithms, since this kind of random numbers have proven to be more likely
     * to trigger corner-case bugs. The random number will be in the range
     * <code>(2<sup>n - 1</sup>)</code> to <code>(2<sup>n</sup> - 1)</code>,
     * inclusive.
     *
     * @apiNote {@code n} should be treated as an unsigned long.
     */
    public static MPZ rrandomb(RandState s, long n) {
        var z = new MPZ();
        z.urandombAssign(s, n);
        return z;
    }

    /**
     * Sets this {@code MPZ} to a random integer of at most {@code max_size} limbs.
     * The generated random number doesn’t satisfy any particular requirements of
     * randomness. Negative random numbers are generated when {@code max_size} is
     * negative.
     *
     * @deprecated use {@link urandombAssign} or {@link urandommAssign} instead.
     * @return this {@code MPZ}.
     */
    @Deprecated
    public MPZ randomAssign(long max_size) {
        GMP.libGMP.__gmpz_random(mpzPointer, new MPSizeT(max_size));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is a random integer of at most
     * {@code max_size} limbs. The generated random number doesn’t satisfy any
     * particular requirements of randomness. Negative random numbers are generated
     * when {@code max_size} is negative.
     *
     * @deprecated use {@link urandomb} or {@link urandomm} instead.
     */
    @Deprecated
    public static MPZ random(long max_size) {
        return (new MPZ()).randomAssign(max_size);
    }

    /**
     * Sets this {@code MPZ} to a random integer of at most {@code max_size} limbs,
     * with long strings of zeros and ones in the binary representation. Useful for
     * testing functions and algorithms, since this kind of random numbers have
     * proven to be more likely to trigger corner-case bugs. Negative random numbers
     * are generated when {@code max_size} is negative.
     *
     * @deprecated use {@link rrandombAssign} instead.
     * @return this {@code MPZ}.
     */
    @Deprecated
    public MPZ random2Assign(long max_size) {
        GMP.libGMP.__gmpz_random2(mpzPointer, new MPSizeT(max_size));
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is a random integer of at most
     * {@code max_size} limbs, with long strings of zeros and ones in the binary
     * representation. Useful for testing functions and algorithms, since this kind
     * of random numbers have proven to be more likely to trigger corner-case bugs.
     * Negative random numbers are generated when {@code max_size} is negative.
     *
     * @deprecated use {@link rrandomb} instead.
     */
    @Deprecated
    public static MPZ random2(long max_size) {
        return (new MPZ()).random2Assign(max_size);
    }

    // Integer Import and Export

    /**
     * Sets this {@code MPZ} from the buffer of word data at {@code op}. See the
     * detailed description in the documentation of the GMP function
     * <a href="https://gmplib.org/manual/Integer-Import-and-Export" target=
     * "_blank">{@code mpz_import}</a>. The parameter {@code count} in the prototype
     * of {@code mpz_import} is automatically computed by the capacity of the buffer
     * {@code op}.
     *
     * @apiNote {@code nails} should be treated as an unsigned long.
     */
    public MPZ bufferImportAssign(int order, int size, int endian, long nails, ByteBuffer op) {
        var count = op.capacity() / size + (op.capacity() % size == 0 ? 0 : 1);
        GMP.libGMP.__gmpz_import(mpzPointer, new SizeT(count), order, new SizeT(size), endian, new SizeT(nails), op);
        return this;
    }

    /**
     * Returns an {@code MPZ} whose value is determined from the buffer of word data
     * at {@code op}.
     *
     * @see bufferImportAssign
     *
     * @apiNote {@code nails} should be treated as an unsigned long.
     */
    public static MPZ bufferImport(int order, int size, int endian, long nails, ByteBuffer op) {
        return new MPZ().bufferImportAssign(order, size, endian, nails, op);
    }

    /**
     * Returns a {@link ByteBuffer} filled with word data from this {@code MPZ}. See
     * the detailed description in the documentation of the GMP function
     * <a href="https://gmplib.org/manual/Integer-Import-and-Export" target=
     * "_blank">{@code mpz_export}</a>. We let the function allocate the buffer,
     * since it the easier and safer. The output {@code count} of the original GMP
     * function is not needed, since it corresponds to the capacity of the resulting
     * {@link ByteBuffer}.
     *
     * @apiNote {@code nails} should be treated as an unsigned long.
     */
    public ByteBuffer bufferExport(int order, int size, int endian, long nails) {
        var count = new SizeTByReference();
        var p = GMP.libGMP.__gmpz_export(null, count, order, new SizeT(size), endian, new SizeT(nails), mpzPointer);
        return p.getPointer().getByteBuffer(0, count.getValue().longValue());
    }

    // Miscellaneous Integer Functions

    /**
     * Returns {@code true} if and only if this {@code MPZ} fits into a native
     * unsigned long.
     */
    public boolean fitsUlong() {
        return GMP.libGMP.__gmpz_fits_ulong_p(mpzPointer);
    }

    /**
     * Returns {@code true} if and only if this {@code MPZ} fits into a native
     * signed long.
     */
    public boolean fitsSlong() {
        return GMP.libGMP.__gmpz_fits_slong_p(mpzPointer);
    }

    /**
     * Returns {@code true} if and only if this {@code MPZ} fits into a native
     * unsigned int.
     */
    public boolean fitsUint() {
        return GMP.libGMP.__gmpz_fits_uint_p(mpzPointer);
    }

    /**
     * Returns {@code true} if and only if this {@code MPZ} fits into a native
     * signed int.
     */
    public boolean fitsSint() {
        return GMP.libGMP.__gmpz_fits_sint_p(mpzPointer);
    }

    /**
     * Returns {@code true} if and only if this {@code MPZ} fits into a native
     * unsigned short.
     */
    public boolean fitsUshort() {
        return GMP.libGMP.__gmpz_fits_ushort_p(mpzPointer);
    }

    /**
     * Returns {@code true} if and only if this {@code MPZ} fits into a native
     * signed short.
     */
    public boolean fitsSshort() {
        return GMP.libGMP.__gmpz_fits_sshort_p(mpzPointer);
    }

    /**
     * Returns {@code true} if and only if this {@code MPZ} is odd.
     */
    public boolean isOdd() {
        return fdivUi(2) != 0;
    }

    /**
     * Returns {@code true} if and only if this {@code MPZ} is even
     */
    public boolean isEven() {
        return fdivUi(2) == 0;
    }

    /**
     * Returns the size of this {@code MPZ} measured in number of digits in the
     * specified {@code base}. See the the GMP function
     * <a href="https://gmplib.org/manual/Miscellaneous-Integer-Functions" target=
     * "_blank">{@code mpz_sizeinbase}</a>.
     *
     * @apiNote the return value should be treated as an unsigned long.
     */
    public long sizeinbase(int base) {
        if (base < 2 || base > 62)
            throw new IllegalArgumentException("The value of base can vary from 2 to 62");
        return GMP.libGMP.__gmpz_sizeinbase(mpzPointer, base).longValue();
    }

    // Java name aliases

    /**
     * Builds an {@code MPZ} whose value is zero.
     */
    public MPZ() {
        mpzPointer = new Pointer();
        GMP.libGMP.__gmpz_init(mpzPointer);
        GMP.cleaner.register(this, new MPZCleaner(mpzPointer));
    }

    /**
     * Builds an {@code MPZ} whose value is {@code op}.
     */
    public MPZ(MPZ op) {
        mpzPointer = new Pointer();
        GMP.libGMP.__gmpz_init_set(mpzPointer, op.mpzPointer);
        GMP.cleaner.register(this, new MPZCleaner(mpzPointer));
    }

    /**
     * Builds an {@code MPZ} whose value is {@code op}.
     */
    public MPZ(long op) {
        mpzPointer = new Pointer();
        GMP.libGMP.__gmpz_init_set_si(mpzPointer, new NativeLong(op));
        GMP.cleaner.register(this, new MPZCleaner(mpzPointer));
    }

    /**
     * Builds an {@code MPZ} whose value is the truncation of {@code op}.
     */
    public MPZ(double op) {
        mpzPointer = new Pointer();
        GMP.libGMP.__gmpz_init_set_d(mpzPointer, op);
        GMP.cleaner.register(this, new MPZCleaner(mpzPointer));
    }

    /**
     * Builds an {@code MPZ} whose value is the number represented by the string
     * {@code str} in the specified {@code base}. See the GMP function
     * <a href="https://gmplib.org/manual/Simultaneous-Integer-Init-_0026-Assign"
     * target="_blank">{@code mpz_init_set_str}</a>.
     *
     * @throws IllegalArgumentException if either {@code base} is not valid or
     *                                  {@code str} is not a valid string in the
     *                                  specified {@code base}.
     *
     */
    public MPZ(String str, int base) {
        mpzPointer = new Pointer();
        int result = GMP.libGMP.__gmpz_init_set_str(mpzPointer, str, base);
        if (result == -1) {
            GMP.libGMP.__gmpz_clear(mpzPointer);
            throw new IllegalArgumentException(
                    "either base is not valid or str is not a valid number in the specified base");
        }
        GMP.cleaner.register(this, new MPZCleaner(mpzPointer));
    }

    /**
     * Builds an {@code MPZ} whose value is the number represented by the string
     * {@code str} in decimal base. See the GMP function
     * <a href="https://gmplib.org/manual/Simultaneous-Integer-Init-_0026-Assign"
     * target="_blank">{@code mpz_init_set_str}</a>.
     *
     * @throws IllegalArgumentException if {@code str} is not a valid number
     *                                  representation in decimal base.
     */
    public MPZ(String str) {
        this(str, 10);
    }

    /**
     * Sets this {@code MPZ} to {@code op}.
     */
    public MPZ setValue(MPZ op) {
        return set(op);
    }

    /**
     * Sets this {@code MPZ} to signed long {@code op}.
     */
    public MPZ setValue(long op) {
        return set(op);
    }

    /**
     * Sets this {@code MPZ} to the truncation op {@code op}.
     *
     * @throws IllegalArgumentException if {@code op} is not a finite number. In
     *                                  this case, {@code this} is not altered.
     */
    public MPZ setValue(double op) {
        return set(op);
    }

    /**
     * Set this {@code MPZ} to the number represented by the string {@code str} in
     * the specified {@code base}. See the GMP function
     * <a href="https://gmplib.org/manual/Assigning-Integers" target="
     * _blank">{@code mpz_set_str}</a>.
     *
     * @throws IllegalArgumentException if either {@code base} is not valid or
     *                                  {@code str} is not a valid number
     *                                  representation in the specified base. In
     *                                  this case, {@code this} is not altered.
     */
    public MPZ setValue(String str, int base) {
        var result = set(str, base);
        if (result == -1)
            throw new IllegalArgumentException(
                    "either base is not valid or str is not a valid number in the specified base");
        return this;
    }

    /**
     * Set this {@code MPZ} to the value represented by the string {@code str} in
     * decimal base.
     *
     * @throws IllegalArgumentException if {@code str} is not a valid number
     *                                  representation in decimal base.
     * @see setValue(String, int)
     */
    public MPZ setValue(String str) {
        var result = set(str, 10);
        if (result == -1)
            throw new IllegalArgumentException("str is not a valid number in decimal base");
        return this;
    }

    /**
     * Compares this {@code MPZ} with {@code op}. Returns a positive value if
     * {@code (this > op)}, zero if {@code this = op}, or a negative value if
     * {@code this < op}.This order is compatible with equality.
     */
    @Override
    public int compareTo(MPZ op) {
        return GMP.libGMP.__gmpz_cmp(mpzPointer, op.mpzPointer);
    }

    /**
     * Compates this {@code MPZ} with the object {@code op} for equality. It returns
     * {@code true} if and only if {@code op} is an {@code MPZ} with the same value
     * of {@code this}.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof MPZ) {
            var z = (MPZ) obj;
            return GMP.libGMP.__gmpz_cmp(mpzPointer, z.mpzPointer) == 0;
        }
        return false;
    }

    /***
     * Returns a hash code value for this {@code MPZ}.
     */
    @Override
    public int hashCode() {
        return intValue();
    }

    /**
     * Converts this {@code MPZ} to a signed long.
     *
     * If this number is too big to fit a signed long, return the least significant
     * part, preserving the sign.
     */
    @Override
    public long longValue() {
        return getSi();
    }

    /**
     * Converts this {@code MPZ} to a signed int.
     *
     * If this number is too big to fit a signed long, return the least significant
     * part, preserving the sign.
     */
    @Override
    public int intValue() {
        return (int) getSi();
    }

    /**
     * Converts this {@code MPZ} to a double, truncating if necessary.
     *
     * @see getD
     */
    @Override
    public double doubleValue() {
        return getD();
    }

    /**
     * Converts this {@code MPZ} to a float, truncating if necessary.
     *
     * @see getD
     */
    @Override
    public float floatValue() {
        return (float) getD();
    }

    /**
     * Converts this {@code MPZ} to its string representation in the specified
     * {@code base}, or {@code null} if the base is not valid. See the GMP function
     * <a href="https://gmplib.org/manual/Converting-Integers" target=
     * "_blank">{@code mpz_get_str}</a>.
     */
    public String toString(int base) {
        return getStr(base);
    }

    /**
     * Converts this {@code MPZ} to its decimal string representation.
     */
    @Override
    public String toString() {
        return getStr(10);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        // writeUTF seems more efficient, but has a limit of 64Kb
        // use base 62 to have a more compact representation
        out.writeObject(toString(62));
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        mpzPointer = new Pointer();
        GMP.libGMP.__gmpz_init_set_str(mpzPointer, (String) in.readObject(), 62);
        GMP.cleaner.register(this, new MPZCleaner(mpzPointer));
    }

    @SuppressWarnings("unused")
    private void readObjectNoData() throws ObjectStreamException {
        mpzPointer = new Pointer();
        GMP.libGMP.__gmpz_init(mpzPointer);
        GMP.cleaner.register(this, new MPZCleaner(mpzPointer));
    }

}
