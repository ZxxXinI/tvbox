package com.tvbox.acceptance;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.InputEvent;
import java.lang.reflect.Method;
public class KeyProbe {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("android.hardware.input.InputManager");
        Object manager = cls.getMethod("getInstance").invoke(null);
        Method inject = cls.getMethod("injectInputEvent", InputEvent.class, int.class);
        int code = Integer.parseInt(args[0]);
        int count = Integer.parseInt(args[1]);
        long start = SystemClock.uptimeMillis();
        for (int i = 0; i < count; i++) {
            inject.invoke(manager, new KeyEvent(start, SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN,
                code, i, 0, -1, 0, i == 0 ? 0 : KeyEvent.FLAG_LONG_PRESS, 0x101), 2);
            Thread.sleep(200);
        }
        inject.invoke(manager, new KeyEvent(start, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
            code, 0, 0, -1, 0, 0, 0x101), 2);
        System.out.println("Injected " + count + " repeats at 200ms");
    }
}
