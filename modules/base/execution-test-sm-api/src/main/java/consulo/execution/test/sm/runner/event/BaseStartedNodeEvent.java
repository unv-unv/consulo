/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package consulo.execution.test.sm.runner.event;

import consulo.util.lang.StringUtil;
import jetbrains.buildServer.messages.serviceMessages.MessageWithAttributes;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class BaseStartedNodeEvent extends TreeNodeEvent {
    private final String myParentId;
    private final String myLocationUrl;
    private final String myMetainfo;
    private final String myNodeType;
    private final String myNodeArgs;
    private final boolean myRunning;

    protected BaseStartedNodeEvent(
        @Nullable String name,
        @Nullable String id,
        @Nullable String parentId,
        @Nullable String locationUrl,
        @Nullable String metainfo,
        @Nullable String nodeType,
        @Nullable String nodeArgs,
        boolean running
    ) {
        super(name, id);
        myParentId = parentId;
        myLocationUrl = locationUrl;
        myMetainfo = metainfo;
        myNodeType = nodeType;
        myNodeArgs = nodeArgs;
        myRunning = running;
    }

    /**
     * @return parent node id, or null if undefined
     */
    @Nullable
    public String getParentId() {
        return myParentId;
    }

    @Nullable
    public String getLocationUrl() {
        return myLocationUrl;
    }

    @Nullable
    public String getMetainfo() {
        return myMetainfo;
    }

    @Nullable
    public String getNodeType() {
        return myNodeType;
    }

    @Nullable
    public String getNodeArgs() {
        return myNodeArgs;
    }

    public boolean isRunning() {
        return myRunning;
    }

    @Override
    protected void appendToStringInfo(@Nonnull StringBuilder buf) {
        append(buf, "parentId", myParentId);
        append(buf, "locationUrl", myLocationUrl);
        append(buf, "metainfo", myMetainfo);
        append(buf, "running", myRunning);
    }

    @Nullable
    public static String getParentNodeId(@Nonnull MessageWithAttributes message) {
        return TreeNodeEvent.getNodeId(message, "parentNodeId");
    }

    @Nullable
    public static String getNodeType(@Nonnull MessageWithAttributes message) {
        return message.getAttributes().get("nodeType");
    }

    @Nullable
    public static String getMetainfo(@Nonnull MessageWithAttributes message) {
        return message.getAttributes().get("metainfo");
    }

    @Nullable
    public static String getNodeArgs(@Nonnull MessageWithAttributes message) {
        return message.getAttributes().get("nodeArgs");
    }

    public static boolean isRunning(@Nonnull MessageWithAttributes message) {
        String runningStr = message.getAttributes().get("running");
        if (StringUtil.isEmpty(runningStr)) {
            // old behavior preserved
            return true;
        }
        return Boolean.parseBoolean(runningStr);
    }
}
