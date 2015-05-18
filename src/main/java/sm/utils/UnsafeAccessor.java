/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sm.utils;

import sun.misc.Unsafe;
import java.lang.reflect.Field;

/**
 *
 * @author smdeveloper
 */
public class UnsafeAccessor {

    private static Unsafe UNSAFE = null;

    static {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UnsafeAccessor.UNSAFE = (sun.misc.Unsafe) field.get(null);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static Unsafe get() {
        return UnsafeAccessor.UNSAFE;
    }

}

