// Initial template generated from Rdynload.h from R 3.2.2
package org.renjin.gnur.api;

import org.renjin.gcc.runtime.BytePtr;
import org.renjin.gcc.runtime.ObjectPtr;
import org.renjin.primitives.packaging.DllInfo;
import org.renjin.primitives.packaging.DllSymbol;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public final class Rdynload {

  private static final Map<String,MethodHandle> CALL_MAP = new HashMap<>();

  private Rdynload() { }


  public static int R_registerRoutines (DllInfo info,
                                        ObjectPtr<MethodDef> croutines,
                                        ObjectPtr<MethodDef> callRoutines,
                                        ObjectPtr<MethodDef> fortranRoutines,
                                        ObjectPtr<MethodDef> externalRoutines) {

    addTo(info, DllSymbol.Convention.C, croutines);
    addTo(info, DllSymbol.Convention.CALL, callRoutines);
    addTo(info, DllSymbol.Convention.FORTRAN, fortranRoutines);
    addTo(info, DllSymbol.Convention.EXTERNAL, externalRoutines);

    return 0;
  }

  private static void addTo(DllInfo library, DllSymbol.Convention convention, ObjectPtr<MethodDef> methods) {

    if(methods != null && methods.array != null) {
      for(int i=0; ; i++) {
        MethodDef def = methods.get(i);
        if (def.fun == null) {
          break;
        }
        DllSymbol symbol = new DllSymbol(library);
        symbol.setMethodHandle(def.fun);
        symbol.setConvention(convention);
        symbol.setName(def.getName());
        library.addSymbol(symbol);
      }
    }
  }

  public static boolean R_useDynamicSymbols(DllInfo info, boolean value) {
    // unclear what this function does
    return true;
  }

  public static boolean R_forceSymbols(DllInfo info, boolean value) {
    return true;
  }

//
//   DllInfo* R_getDllInfo (const char *name)
//
//   DllInfo* R_getEmbeddingDllInfo (void)
//
//   DL_FUNC R_FindSymbol (char const *, char const *, R_RegisteredNativeSymbol *symbol)
//

  @Deprecated
  public static void R_RegisterCCallable (BytePtr packageName, BytePtr name, Object method) {
    R_RegisterCCallable(packageName, name, (MethodHandle)method);
  }

  public static void R_RegisterCCallable (BytePtr packageName, BytePtr name, MethodHandle method) {
    // We assume this is thread save given if multiple sessions are Registering or Getting
    // a method the packages/functions stay the same and the order of processing is irrelevant
    String key = packageName.nullTerminatedString() + "." + name.nullTerminatedString();
    CALL_MAP.put(key, method);
  }

  public static MethodHandle R_GetCCallable (BytePtr packageName, BytePtr name) {
    String key = packageName.nullTerminatedString() + "." + name.nullTerminatedString();
    return CALL_MAP.get(key);
  }
}
