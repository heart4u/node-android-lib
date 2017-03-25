/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

#include "uv.h"
#include "jni.h"
///#include "com_iwebpp_libuvpp_LibUV.h"

/*
 * Class:     com_iwebpp_libuvpp_LibUV
 * Method:    _version
 * Signature: ()Ljava/lang/String;
 */
extern "C" JNIEXPORT jstring JNICALL Java_com_iwebpp_libuvpp_LibUV__1version
  (JNIEnv *env, jclass cls) {

  const char* version = "libuvpp v0.8.x";///uv_version_string();
  if (version) {
    return env->NewStringUTF(version);
  }
  return NULL;
}

/*
 * Class:     com_iwebpp_libuvpp_LibUV
 * Method:    _disable_stdio_inheritance
 * Signature: ()V
 */
extern "C" JNIEXPORT void JNICALL Java_com_iwebpp_libuvpp_LibUV__1disable_1stdio_1inheritance
  (JNIEnv *env, jclass cls) {

  // Make inherited handles noninheritable.
  uv_disable_stdio_inheritance();
}
