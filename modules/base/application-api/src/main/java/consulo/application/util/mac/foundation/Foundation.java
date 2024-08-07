/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.application.util.mac.foundation;

import com.sun.jna.*;
import consulo.util.jna.JnaLoader;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author spleaner
 * @see http://developer.apple.com/documentation/Cocoa/Reference/ObjCRuntimeRef/Reference/reference.html
 */
public class Foundation {
  private static final FoundationLibrary myFoundationLibrary;
  private static final Function myObjcMsgSend;

  static {
    assert JnaLoader.isLoaded() : "JNA library is not available";
    myFoundationLibrary = Native.load("Foundation", FoundationLibrary.class, Map.of("jna.encoding", "UTF8"));
    NativeLibrary nativeLibrary = ((Library.Handler)Proxy.getInvocationHandler(myFoundationLibrary)).getNativeLibrary();
    myObjcMsgSend = nativeLibrary.getFunction("objc_msgSend");
  }

  static Callback ourRunnableCallback;


  public static void init() { /* fake method to init foundation */ }

  private Foundation() {
  }

  /**
   * Get the ID of the NSClass with className
   */
  public static ID getObjcClass(String className) {
    return myFoundationLibrary.objc_getClass(className);
  }

  public static ID getProtocol(String name) {
    return myFoundationLibrary.objc_getProtocol(name);
  }

  public static Pointer createSelector(String s) {
    return myFoundationLibrary.sel_registerName(s);
  }

  @Nonnull
  private static Object[] prepInvoke(ID id, Pointer selector, Object[] args) {
    Object[] invokArgs = new Object[args.length + 2];
    invokArgs[0] = id;
    invokArgs[1] = selector;
    System.arraycopy(args, 0, invokArgs, 2, args.length);
    return invokArgs;
  }

  public static ID invoke(final ID id, final Pointer selector, Object... args) {
    // objc_msgSend is called with the calling convention of the target method
    // on x86_64 this does not make a difference, but arm64 uses a different calling convention for varargs
    // it is therefore important to not call objc_msgSend as a vararg function
    return new ID(myObjcMsgSend.invokeLong(prepInvoke(id, selector, args)));
  }

  public static ID invoke(final String cls, final String selector, Object... args) {
    return invoke(getObjcClass(cls), createSelector(selector), args);
  }

  public static ID invoke(final ID id, final String selector, Object... args) {
    return invoke(id, createSelector(selector), args);
  }

  public static ID allocateObjcClassPair(ID superCls, String name) {
    return myFoundationLibrary.objc_allocateClassPair(superCls, name, 0);
  }

  public static void registerObjcClassPair(ID cls) {
    myFoundationLibrary.objc_registerClassPair(cls);
  }

  public static boolean isClassRespondsToSelector(ID cls, Pointer selectorName) {
    return myFoundationLibrary.class_respondsToSelector(cls, selectorName);
  }

  /**
   * @param cls          The class to which to add a method.
   * @param selectorName A selector that specifies the name of the method being added.
   * @param impl         A function which is the implementation of the new method. The function must take at least two arguments—self and _cmd.
   * @param types        An array of characters that describe the types of the arguments to the method.
   *                     See <a href="https://developer.apple.com/library/IOs/documentation/Cocoa/Conceptual/ObjCRuntimeGuide/Articles/ocrtTypeEncodings.html#//apple_ref/doc/uid/TP40008048-CH100"></a>
   * @return true if the method was added successfully, otherwise false (for example, the class already contains a method implementation with that name).
   */
  public static boolean addMethod(ID cls, Pointer selectorName, Callback impl, String types) {
    return myFoundationLibrary.class_addMethod(cls, selectorName, impl, types);
  }

  public static boolean addProtocol(ID aClass, ID protocol) {
    return myFoundationLibrary.class_addProtocol(aClass, protocol);
  }

  public static boolean addMethodByID(ID cls, Pointer selectorName, ID impl, String types) {
    return myFoundationLibrary.class_addMethod(cls, selectorName, impl, types);
  }

  public static boolean isMetaClass(ID cls) {
    return myFoundationLibrary.class_isMetaClass(cls);
  }

  @Nullable
  public static String stringFromSelector(Pointer selector) {
    ID id = myFoundationLibrary.NSStringFromSelector(selector);
    if (id.intValue() > 0) {
      return toStringViaUTF8(id);
    }

    return null;
  }

  public static Pointer getClass(Pointer clazz) {
    return myFoundationLibrary.objc_getClass(clazz);
  }

  public static String fullUserName() {
    return toStringViaUTF8(myFoundationLibrary.NSFullUserName());
  }

  public static ID class_replaceMethod(ID cls, Pointer selector, Callback impl, String types) {
    return myFoundationLibrary.class_replaceMethod(cls, selector, impl, types);
  }

  public static ID getMetaClass(String className) {
    return myFoundationLibrary.objc_getMetaClass(className);
  }

  public static boolean isPackageAtPath(@Nonnull final String path) {
    final ID workspace = invoke("NSWorkspace", "sharedWorkspace");
    final ID result = invoke(workspace, createSelector("isFilePackageAtPath:"), nsString(path));

    return result.intValue() == 1;
  }

  public static boolean isPackageAtPath(@Nonnull final File file) {
    if (!file.isDirectory()) return false;
    return isPackageAtPath(file.getPath());
  }

  public static ID nsString(@Nonnull String s) {
    // Use a byte[] rather than letting jna do the String -> char* marshalling itself.
    // Turns out about 10% quicker for long strings.
    try {
      if (s.isEmpty()) {
        return invoke("NSString", "string");
      }

      byte[] utf16Bytes = s.getBytes("UTF-16LE");
      return invoke(invoke(invoke("NSString", "alloc"), "initWithBytes:length:encoding:", utf16Bytes, utf16Bytes.length, convertCFEncodingToNS(FoundationLibrary.kCFStringEncodingUTF16LE)),
                    "autorelease");
    }
    catch (UnsupportedEncodingException x) {
      throw new RuntimeException(x);
    }
  }

  @Nullable
  public static String toStringViaUTF8(ID cfString) {
    if (cfString.intValue() == 0) return null;

    int lengthInChars = myFoundationLibrary.CFStringGetLength(cfString);
    int potentialLengthInBytes = 3 * lengthInChars + 1; // UTF8 fully escaped 16 bit chars, plus nul

    byte[] buffer = new byte[potentialLengthInBytes];
    byte ok = myFoundationLibrary.CFStringGetCString(cfString, buffer, buffer.length, FoundationLibrary.kCFStringEncodingUTF8);
    if (ok == 0) throw new RuntimeException("Could not convert string");
    return Native.toString(buffer);
  }

  @Nullable
  public static String getEncodingName(long nsStringEncoding) {
    long cfEncoding = myFoundationLibrary.CFStringConvertNSStringEncodingToEncoding(nsStringEncoding);
    ID pointer = myFoundationLibrary.CFStringConvertEncodingToIANACharSetName(cfEncoding);
    return toStringViaUTF8(pointer);
  }

  public static long getEncodingCode(@Nullable String encodingName) {
    if (StringUtil.isEmptyOrSpaces(encodingName)) return -1;

    ID converted = nsString(encodingName);
    long cfEncoding = myFoundationLibrary.CFStringConvertIANACharSetNameToEncoding(converted);

    ID restored = myFoundationLibrary.CFStringConvertEncodingToIANACharSetName(cfEncoding);
    if (ID.NIL.equals(restored)) return -1;

    return convertCFEncodingToNS(cfEncoding);
  }

  private static long convertCFEncodingToNS(long cfEncoding) {
    return myFoundationLibrary.CFStringConvertEncodingToNSStringEncoding(cfEncoding) & 0xffffffffffl;  // trim to C-type limits
  }

  public static void cfRetain(ID id) {
    myFoundationLibrary.CFRetain(id);
  }

  public static void cfRelease(ID... ids) {
    for (ID id : ids) {
      if (id != null) {
        myFoundationLibrary.CFRelease(id);
      }
    }
  }

  public static boolean isMainThread() {
    return invoke("NSThread", "isMainThread").intValue() > 0;
  }

  private static final Map<String, RunnableInfo> ourMainThreadRunnables = new HashMap<String, RunnableInfo>();
  private static long ourCurrentRunnableCount = 0;
  private static final Object RUNNABLE_LOCK = new Object();

  static class RunnableInfo {
    RunnableInfo(Runnable runnable, boolean useAutoreleasePool) {
      myRunnable = runnable;
      myUseAutoreleasePool = useAutoreleasePool;
    }

    Runnable myRunnable;
    boolean myUseAutoreleasePool;
  }

  public static void executeOnMainThread(final boolean withAutoreleasePool, final boolean waitUntilDone, final Runnable runnable) {
    initRunnableSupport();

    synchronized (RUNNABLE_LOCK) {
      ourCurrentRunnableCount++;
      ourMainThreadRunnables.put(String.valueOf(ourCurrentRunnableCount), new RunnableInfo(runnable, withAutoreleasePool));
    }

    final ID ideaRunnable = getObjcClass("IdeaRunnable");
    final ID runnableObject = invoke(invoke(ideaRunnable, "alloc"), "init");
    invoke(runnableObject, "performSelectorOnMainThread:withObject:waitUntilDone:", createSelector("run:"), nsString(String.valueOf(ourCurrentRunnableCount)), Boolean.valueOf(waitUntilDone));
    invoke(runnableObject, "release");
  }

  private static void initRunnableSupport() {
    if (ourRunnableCallback == null) {
      final ID runnableClass = allocateObjcClassPair(getObjcClass("NSObject"), "IdeaRunnable");
      registerObjcClassPair(runnableClass);

      final Callback callback = new Callback() {
        @SuppressWarnings("UnusedDeclaration")
        public void callback(ID self, String selector, ID keyObject) {
          final String key = toStringViaUTF8(keyObject);

          RunnableInfo info;
          synchronized (RUNNABLE_LOCK) {
            info = ourMainThreadRunnables.remove(key);
          }

          if (info == null) {
            return;
          }

          ID pool = null;
          try {
            if (info.myUseAutoreleasePool) {
              pool = invoke("NSAutoreleasePool", "new");
            }

            info.myRunnable.run();
          }
          finally {
            if (pool != null) {
              invoke(pool, "release");
            }
          }
        }
      };
      if (!addMethod(runnableClass, createSelector("run:"), callback, "v@:*")) {
        throw new RuntimeException("Unable to add method to objective-c runnableClass class!");
      }
      ourRunnableCallback = callback;
    }
  }

  public static class NSDictionary {
    private final ID myDelegate;

    public NSDictionary(ID delegate) {
      myDelegate = delegate;
    }

    public ID get(ID key) {
      return invoke(myDelegate, "objectForKey:", key);
    }

    public ID get(String key) {
      return get(nsString(key));
    }

    public int count() {
      return invoke(myDelegate, "count").intValue();
    }

    public NSArray keys() {
      return new NSArray(invoke(myDelegate, "allKeys"));
    }
  }

  public static class NSArray {
    private final ID myDelegate;

    public NSArray(ID delegate) {
      myDelegate = delegate;
    }

    public int count() {
      return invoke(myDelegate, "count").intValue();
    }

    public ID at(int index) {
      return invoke(myDelegate, "objectAtIndex:", index);
    }
  }

  public static class NSAutoreleasePool {
    private final ID myDelegate;

    public NSAutoreleasePool() {
      myDelegate = invoke(invoke("NSAutoreleasePool", "alloc"), "init");
    }

    public void drain() {
      invoke(myDelegate, "drain");
    }
  }

  public static class NSRect extends Structure implements Structure.ByValue {
    private static final List __FIELDS = Arrays.asList("origin", "size");

    public NSPoint origin;
    public NSSize size;

    public NSRect(double x, double y, double w, double h) {
      origin = new NSPoint(x, y);
      size = new NSSize(w, h);
    }

    @Override
    protected List getFieldOrder() {
      return __FIELDS;
    }
  }

  public static class NSPoint extends Structure implements Structure.ByValue {
    private static final List __FIELDS = Arrays.asList("x", "y");

    public CGFloat x;
    public CGFloat y;

    @SuppressWarnings("UnusedDeclaration")
    public NSPoint() {
      this(0, 0);
    }

    public NSPoint(double x, double y) {
      this.x = new CGFloat(x);
      this.y = new CGFloat(y);
    }

    @Override
    protected List getFieldOrder() {
      return __FIELDS;
    }
  }

  public static class NSSize extends Structure implements Structure.ByValue {
    private static final List __FIELDS = Arrays.asList("width", "height");

    public CGFloat width;
    public CGFloat height;

    @SuppressWarnings("UnusedDeclaration")
    public NSSize() {
      this(0, 0);
    }

    public NSSize(double width, double height) {
      this.width = new CGFloat(width);
      this.height = new CGFloat(height);
    }

    @Override
    protected List getFieldOrder() {
      return __FIELDS;
    }
  }

  public static class CGFloat implements NativeMapped {
    private final double value;

    @SuppressWarnings("UnusedDeclaration")
    public CGFloat() {
      this(0);
    }

    public CGFloat(double d) {
      value = d;
    }

    @Override
    public Object fromNative(Object o, FromNativeContext fromNativeContext) {
      switch (Native.LONG_SIZE) {
        case 4:
          return new CGFloat((Float)o);
        case 8:
          return new CGFloat((Double)o);
      }
      throw new IllegalStateException();
    }

    @Override
    public Object toNative() {
      switch (Native.LONG_SIZE) {
        case 4:
          return (float)value;
        case 8:
          return value;
      }
      throw new IllegalStateException();
    }

    @Override
    public Class<?> nativeType() {
      switch (Native.LONG_SIZE) {
        case 4:
          return Float.class;
        case 8:
          return Double.class;
      }
      throw new IllegalStateException();
    }
  }

  public static ID fillArray(final Object[] a) {
    final ID result = invoke("NSMutableArray", "array");
    for (Object s : a) {
      invoke(result, "addObject:", convertType(s));
    }

    return result;
  }

  public static ID createDict(@Nonnull final String[] keys, @Nonnull final Object[] values) {
    final ID nsKeys = invoke("NSArray", "arrayWithObjects:", convertTypes(keys));
    final ID nsData = invoke("NSArray", "arrayWithObjects:", convertTypes(values));
    return invoke("NSDictionary", "dictionaryWithObjects:forKeys:", nsData, nsKeys);
  }

  private static Object[] convertTypes(@Nonnull Object[] v) {
    final Object[] result = new Object[v.length];
    for (int i = 0; i < v.length; i++) {
      result[i] = convertType(v[i]);
    }
    return result;
  }

  private static Object convertType(@Nonnull Object o) {
    if (o instanceof Pointer || o instanceof ID) {
      return o;
    }
    else if (o instanceof String) {
      return nsString((String)o);
    }
    else {
      throw new IllegalArgumentException("Unsupported type! " + o.getClass());
    }
  }
}
