package it.unich.jgmptest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Optional;

import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.junit.jupiter.api.Test;

import it.unich.jgmp.MPZ;
import it.unich.jgmp.MPZ.PrimalityStatus;
import it.unich.jgmp.RandState;

public class MPZTest {

    public static final String MAX_ULONG = "18446744073709551615";

    public static final MPZ zMaxUlong = new MPZ(MAX_ULONG);

    @Test
    void test_init() {
        assertEquals(new MPZ(0), MPZ.init());
        assertEquals(new MPZ(0), MPZ.init2(1000));
        assertEquals(new MPZ(15), new MPZ(15).realloc2(10000));
    }

    @Test
    void test_assignment() {
        var z = new MPZ();
        assertEquals(new MPZ(15), z.set(new MPZ(15)));
        assertEquals(zMaxUlong, z.set(-1));
        assertEquals(new MPZ(-3), z.setSi(-3));
        assertEquals(new MPZ(5), z.set(5.2));
        assertThrows(IllegalArgumentException.class, () -> z.set(Double.POSITIVE_INFINITY));
        assertEquals(0, z.set("-1A", 16));
        assertEquals(new MPZ(-26), z);
        assertEquals(-1, z.set("2", 63));
        assertEquals(new MPZ(-26), z);
        var z2 = new MPZ(-26);
        var z3 = new MPZ(2).swap(z2);
        assertEquals(new MPZ(-26), z3);
        assertEquals(new MPZ(2), z2);
    }

    @Test
    void test_initandassignment() {
        assertEquals(new MPZ(15), MPZ.initSet(new MPZ(15)));
        assertEquals(new MPZ(15), MPZ.initSet(15));
        assertEquals(zMaxUlong, MPZ.initSet(-1));
        assertEquals(new MPZ(-1), MPZ.initSetSi(-1));
        assertEquals(new MPZ(15), MPZ.initSet(15.2));
        assertEquals(new Pair<>(0, new MPZ(15)), MPZ.initSet("15", 10));
        assertEquals(new Pair<>(-1, new MPZ(0)), MPZ.initSet("15", 63));
        assertEquals(new Pair<>(-1, new MPZ(0)), MPZ.initSet("99", 7));
    }

    @Test
    void test_constructors() {
        assertEquals(new MPZ(0), new MPZ());
        assertEquals(new MPZ(15), new MPZ("15"));
        assertEquals(new MPZ(15), new MPZ(15.4));
    }

    @Test
    void test_conversion() {
        assertEquals(4l, new MPZ(-4).getUi());
        assertEquals(-4l, new MPZ(-4).getSi());
        assertEquals(-4.0, new MPZ(-4).getD());
        assertEquals(new Pair<>(-0.5, 3l), new MPZ(-4).getD2Exp());
        assertEquals("125", new MPZ(125).getStr(10));
        assertEquals(null, new MPZ(125).getStr(63));
    }

    @Test
    void test_arithmetic() {
        assertEquals(new MPZ(15), new MPZ(8).add(new MPZ(7)));
        assertEquals(new MPZ(15), new MPZ(8).add(7));
        assertEquals(new MPZ(1), new MPZ(8).sub(new MPZ(7)));
        assertEquals(new MPZ(1), new MPZ(8).sub(7));
        assertEquals(new MPZ(-1), new MPZ(8).subReverse(7));
        assertEquals(new MPZ(56), new MPZ(8).mul(new MPZ(7)));
        assertEquals(new MPZ(56), new MPZ(8).mul(7));
        assertEquals(new MPZ(-56), new MPZ(8).mulSi(-7));
        assertEquals(new MPZ(14), new MPZ(2).addmul(new MPZ(4), new MPZ(3)));
        assertEquals(new MPZ(14), new MPZ(2).addmul(new MPZ(4), 3));
        assertEquals(new MPZ(-10), new MPZ(2).submul(new MPZ(4), new MPZ(3)));
        assertEquals(new MPZ(-10), new MPZ(2).submul(new MPZ(4), 3));
        assertEquals(new MPZ(48), new MPZ(3).mul2Exp(4));
        assertEquals(new MPZ(-5), new MPZ(5).neg());
        assertEquals(new MPZ(5), new MPZ(-5).abs());
    }

    @Test
    void test_division() {
        assertEquals(new MPZ(4), new MPZ(15).cdivq(new MPZ(4)));
        assertEquals(new MPZ(-1), new MPZ(15).cdivr(new MPZ(4)));
        assertEquals(new Pair<>(new MPZ(4), new MPZ(-1)), new MPZ(15).cdivqr(new MPZ(4)));
        assertEquals(1, new MPZ(15).cdiv(4));
        assertEquals(new MPZ(4), new MPZ(15).cdivq2Exp(2));
        assertEquals(new MPZ(-1), new MPZ(15).cdivr2Exp(2));

        assertEquals(new MPZ(3), new MPZ(15).fdivq(new MPZ(4)));
        assertEquals(new MPZ(3), new MPZ(15).fdivr(new MPZ(4)));
        assertEquals(new Pair<>(new MPZ(3), new MPZ(3)), new MPZ(15).fdivqr(new MPZ(4)));
        assertEquals(3, new MPZ(15).fdiv(4));
        assertEquals(new MPZ(3), new MPZ(15).fdivq2Exp(2));
        assertEquals(new MPZ(3), new MPZ(15).fdivr2Exp(2));

        assertEquals(new MPZ(3), new MPZ(15).tdivq(new MPZ(4)));
        assertEquals(new MPZ(3), new MPZ(15).tdivr(new MPZ(4)));
        assertEquals(new Pair<>(new MPZ(3), new MPZ(3)), new MPZ(15).tdivqr(new MPZ(4)));
        assertEquals(new Pair<>(new MPZ(-3), new MPZ(-3)), new MPZ(-15).tdivqr(new MPZ(4)));
        assertEquals(3, new MPZ(15).tdiv(4));
        assertEquals(new MPZ(3), new MPZ(15).tdivq2Exp(2));
        assertEquals(new MPZ(3), new MPZ(15).tdivr2Exp(2));

        assertEquals(new MPZ(3), new MPZ(15).mod(new MPZ(6)));
        assertEquals(new MPZ(3), new MPZ(-15).mod(new MPZ(-6)));
        assertEquals(new MPZ(4), new MPZ(12).divexact(new MPZ(3)));
        assertEquals(new MPZ(4), new MPZ(12).divexact(3));
        assertTrue(new MPZ(15).isDivisible(new MPZ(3)));
        assertTrue(new MPZ(15).isDivisible(3));
        assertFalse(new MPZ(15).isDivisible2Exp(3));

        assertTrue(new MPZ(15).isCongruent(new MPZ(3), new MPZ(4)));
        assertTrue(new MPZ(15).isCongruent(3, 4));
        assertFalse(new MPZ(15).isCongruent2Exp(new MPZ(1), 3));
    }

    @Test
    void test_exponentiation() {
        assertEquals(new MPZ(1), new MPZ(2).powm(new MPZ(4), new MPZ(3)));
        assertEquals(new MPZ(1), new MPZ(2).powm(4, new MPZ(3)));
        assertEquals(new MPZ(1), new MPZ(2).powmSec(new MPZ(4), new MPZ(3)));
        assertEquals(new MPZ(16), new MPZ(2).pow(4));
        assertEquals(new MPZ(16), MPZ.pow(2, 4));
    }

    @Test
    void test_roots() {
        assertEquals(new Pair<>(false, new MPZ(2)), new MPZ(17).root(4));
        assertEquals(new Pair<>(new MPZ(2), new MPZ(1)), new MPZ(17).rootrem(4));
        assertEquals(new MPZ(8), new MPZ(65).sqrt());
        assertEquals(new Pair<>(new MPZ(8), new MPZ(1)), new MPZ(65).sqrtrem());
        assertTrue(new MPZ(8).isPerfectPower());
        assertFalse(new MPZ(8).isPerfectSquare());
        assertTrue(new MPZ(16).isPerfectSquare());
    }

    @Test
    void test_numbertheory() {
        assertEquals(PrimalityStatus.PRIME, new MPZ(17).isProbabPrime(15));
        assertEquals(new MPZ(19), new MPZ(17).nextprime());
        assertEquals(new MPZ(6), new MPZ(30).gcd(new MPZ(24)));
        assertEquals(6, new MPZ(30).gcd(24));
        assertEquals(30, new MPZ(30).gcd(0));
        assertEquals(0, new MPZ(0).gcd(0));
        assertEquals(0, new MPZ(0).gcd(0));
        assertEquals(0, zMaxUlong.add(1).gcd(0));
        assertEquals(new Triplet<>(new MPZ(6), new MPZ(1), new MPZ(-1)), new MPZ(30).gcdext(new MPZ(24)));
        assertEquals(new MPZ(120), new MPZ(30).lcm(new MPZ(24)));
        assertEquals(new MPZ(120), new MPZ(30).lcm(24));
        assertEquals(Optional.of(new MPZ(3)), new MPZ(5).invert(new MPZ(7)));
        assertEquals(-1, new MPZ(5).jacobi(new MPZ(3)));
        assertEquals(0, new MPZ(9).legendre(new MPZ(3)));
        assertEquals(1, new MPZ(5).kronecker(new MPZ(4)));
        assertEquals(-1, new MPZ(27).kroneckerSi(28));
        assertEquals(-1, new MPZ(27).kronecker(28));
        assertEquals(1, new MPZ(27).siKronecker(28));
        assertEquals(1, new MPZ(27).kroneckerReverse(28));
        assertEquals(new Pair<>(2l, new MPZ(3)), new MPZ(12).remove(new MPZ(2)));
        assertEquals(new MPZ(40320), MPZ.fac(8));
        assertEquals(new MPZ(945), MPZ.dfac(9));
        assertEquals(new MPZ(28), MPZ.mfac(7, 3));
        assertEquals(new MPZ(210), MPZ.primorial(8));
        assertEquals(new MPZ(21), new MPZ(7).bin(2));
        assertEquals(new MPZ(21), MPZ.bin(7, 2));
        assertEquals(new MPZ(34), MPZ.fib(9));
        assertEquals(new Pair<>(new MPZ(34), new MPZ(21)), MPZ.fib2(9));
        assertEquals(new MPZ(18), MPZ.lucnum(6));
        assertEquals(new Pair<>(new MPZ(18), new MPZ(11)), MPZ.lucnum2(6));
    }

    @Test
    void test_randomstate() {
        var a = new RandState();
        assertDoesNotThrow(() -> new RandState());
        assertDoesNotThrow(() -> RandState.create());
        assertDoesNotThrow(() -> RandState.mt());
        assertDoesNotThrow(() -> RandState.lc(10));
        assertDoesNotThrow(() -> new RandState(a));
        assertDoesNotThrow(() -> RandState.valueOf(a));
        assertThrows(IllegalArgumentException.class, () -> RandState.lc(200));
    }

    @Test
    void test_comparison() {
        var a = new MPZ(10);
        var b = new MPZ(2);
        assertTrue(a.compareTo(b) > 0);
        assertEquals(0, a.cmp(10.0));
        assertTrue(a.cmpSi(-1) > 0);
        assertTrue(a.cmp(-1) < 0);
        assertTrue(a.cmpabs(b) > 0);
        assertEquals(0, a.cmpabs(-10.0));
        assertTrue(a.cmpabsSi(-1) > 0);
        assertTrue(a.cmpabs(-1) < 0);
        assertTrue(a.sgn() > 0);
    }

    @Test
    void test_bitmanipulation() {
        var a = new MPZ(65535);
        assertEquals(1, a.tstbit(15));
        assertEquals(0, a.tstbit(16));
        assertEquals(new MPZ(32767), a.combit(15));
        assertEquals(new MPZ(32767), a.clrbit(15));
        assertEquals(a, a.setbit(15));
        assertEquals(0, a.scan1(0));
        assertEquals(16, a.scan0(0));
        assertEquals(0, a.hamdist(a));
        assertEquals(16, a.popcount());

        var b = new MPZ(15);
        var c = new MPZ(17);
        assertEquals(new MPZ(1), b.and(c));
        assertEquals(new MPZ(31), b.ior(c));
        assertEquals(new MPZ(30), b.xor(c));
        assertEquals(new MPZ(-16), b.com());
    }

    @Test
    @SuppressWarnings( "deprecation" )
    void test_random() {
        var s = new RandState();
        var a = MPZ.urandomb(s, 2);
        assertTrue(a.cmp(0) >= 0);
        assertTrue(a.cmp(3) <= 0);
        var b = MPZ.urandomm(s, new MPZ(10));
        assertTrue(b.cmp(0) >= 0);
        assertTrue(b.cmp(10) <= 0);
        var c = MPZ.rrandomb(s, 2);
        assertTrue(c.cmp(0) >= 0);
        assertTrue(c.cmp(3) <= 0);
        MPZ.random(10);
        MPZ.random2(10);
    }

    @Test
    void test_importexport() {
        var a = new MPZ("124485");
        var buffer = a.bufferExport(1, 1, 0, 0);
        var b = MPZ.bufferImport(1, 1, 0, 0, buffer);
        assertEquals(a, b);
    }

    @Test
    void test_miscellaneous() {
        var a = new MPZ("-213945");
        assertTrue(a.fitsSlong());
        assertFalse(a.fitsUlong());
        assertTrue(a.fitsSint());
        assertFalse(a.fitsUint());
        assertFalse(a.fitsSshort());
        assertFalse(a.fitsUshort());
        assertTrue(a.isOdd());
        assertFalse(a.isEven());
        assertTrue(a.sizeinbase(10) >= 6);
        assertTrue(a.sizeinbase(10) <= 7);
        assertThrows(IllegalArgumentException.class, () -> a.sizeinbase(-20));
    }

    @Test
    void test_serialize() throws IOException, ClassNotFoundException {
        var n = new MPZ(1524132);
        var baos = new ByteArrayOutputStream();
        var oos = new ObjectOutputStream(baos);
        oos.writeObject(n);
        var arr = baos.toByteArray();
        oos.close();

        var ois = new ObjectInputStream(new ByteArrayInputStream(arr));
        var n2 = ois.readObject();
        assertEquals(n, n2);

    }

}