/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server;

import android.os.StrictMode;
import android.util.Log;

import dalvik.system.SocketTagger;

import java.io.FileDescriptor;
import java.net.SocketException;

/**
 * Assigns tags to sockets for traffic stats.
 * @hide
 */
public final class NetworkManagementSocketTagger extends SocketTagger {
    private static final String TAG = "NetworkManagementSocketTagger";
    private static final boolean LOGD = false;

    private static ThreadLocal<SocketTags> threadSocketTags = new ThreadLocal<SocketTags>() {
        @Override
        protected SocketTags initialValue() {
            return new SocketTags();
        }
    };

    public static void install() {
        SocketTagger.set(new NetworkManagementSocketTagger());
    }

    public static int setThreadSocketStatsTag(int tag) {
        final int old = threadSocketTags.get().statsTag;
        threadSocketTags.get().statsTag = tag;
        return old;
    }

    public static int getThreadSocketStatsTag() {
        return threadSocketTags.get().statsTag;
    }

    public static int setThreadSocketStatsUid(int uid) {
        final int old = threadSocketTags.get().statsUid;
        threadSocketTags.get().statsUid = uid;
        return old;
    }

    public static int getThreadSocketStatsUid() {
        return threadSocketTags.get().statsUid;
    }

    @Override
    public void tag(FileDescriptor fd) throws SocketException {
        final SocketTags options = threadSocketTags.get();
        if (LOGD) {
            Log.d(TAG, "tagSocket(" + fd.getInt$() + ") with statsTag=0x"
                    + Integer.toHexString(options.statsTag) + ", statsUid=" + options.statsUid);
        }
        if (options.statsTag == -1) {
            StrictMode.noteUntaggedSocket();
        }
        // TODO: skip tagging when options would be no-op
        tagSocketFd(fd, options.statsTag, options.statsUid);
    }

    private void tagSocketFd(FileDescriptor fd, int tag, int uid) {
        if (tag == -1 && uid == -1) return;

        final int errno = native_tagSocketFd(fd, tag, uid);
        if (errno < 0) {
            Log.i(TAG, "tagSocketFd(" + fd.getInt$() + ", "
                    + tag + ", "
                    + uid + ") failed with errno" + errno);
        }
    }

    @Override
    public void untag(FileDescriptor fd) throws SocketException {
        if (LOGD) {
            Log.i(TAG, "untagSocket(" + fd.getInt$() + ")");
        }
        unTagSocketFd(fd);
    }

    private void unTagSocketFd(FileDescriptor fd) {
        final SocketTags options = threadSocketTags.get();
        if (options.statsTag == -1 && options.statsUid == -1) return;

        final int errno = native_untagSocketFd(fd);
        if (errno < 0) {
            Log.w(TAG, "untagSocket(" + fd.getInt$() + ") failed with errno " + errno);
        }
    }

    public static class SocketTags {
        public int statsTag = -1;
        public int statsUid = -1;
    }

    public static void setKernelCounterSet(int uid, int counterSet) {
        final int errno = native_setCounterSet(counterSet, uid);
        if (errno < 0) {
            Log.w(TAG, "setKernelCountSet(" + uid + ", " + counterSet + ") failed with errno "
                    + errno);
        }
    }

    public static void resetKernelUidStats(int uid) {
        int errno = native_deleteTagData(0, uid);
        if (errno < 0) {
            Log.w(TAG, "problem clearing counters for uid " + uid + " : errno " + errno);
        }
    }

    /**
     * Convert {@code /proc/} tag format to {@link Integer}. Assumes incoming
     * format like {@code 0x7fffffff00000000}.
     */
    public static int kernelToTag(String string) {
        int length = string.length();
        if (length > 10) {
            return Long.decode(string.substring(0, length - 8)).intValue();
        } else {
            return 0;
        }
    }

    private static native int native_tagSocketFd(FileDescriptor fd, int tag, int uid);
    private static native int native_untagSocketFd(FileDescriptor fd);
    private static native int native_setCounterSet(int uid, int counterSetNum);
    private static native int native_deleteTagData(int tag, int uid);
}
