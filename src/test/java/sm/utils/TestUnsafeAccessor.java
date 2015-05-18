/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package sm.utils;

/**
 *
 * @author smdeveloper
 */
import org.testng.Assert;
import org.testng.annotations.Test;


import sun.misc.Unsafe;

public class TestUnsafeAccessor {

    /**
     * allocates 1MB, write a bunch of bytes, reads them back, frees the memory
     *
     * @throws Exception
     */
    @Test(groups = "unit")
    public void testSanity() throws Exception {
        Unsafe unsafe = UnsafeAccessor.get();
        int size = 1024 * 1024;
        long ptr = unsafe.allocateMemory(size);
        long writePtr = ptr;
        for (int i = 0; i < size; i++) {
            byte b = (byte) (i % 127);
            unsafe.putByte(writePtr, b);
            writePtr++;
        }
        long readPtr = ptr;
        for (int i = 0; i < size; i++) {
            byte b = (byte) (i % 127);
            byte readByte = unsafe.getByte(readPtr);
            readPtr++;
            Assert.assertEquals(b, readByte);
        }
        unsafe.freeMemory(ptr);
    }
}
